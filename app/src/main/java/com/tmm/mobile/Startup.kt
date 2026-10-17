package com.tmm.mobile

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.JobRepository
import com.tmm.mobile.data.RefreshCoordinator
import com.tmm.mobile.data.db.BackupImportEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.runAutoTranslate
import com.tmm.mobile.mihon.BackupImporter
import com.tmm.mobile.mihon.BackupReader
import com.tmm.mobile.mihon.DownloadScanner
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonNaming
import com.tmm.mobile.mihon.MihonTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class StartupResult { ONBOARDING, PERMISSION_NEEDED, DONE }

// PLAN_GEMINI.md 5.16. TmmRoot (E10) appelle run() a chaque ON_START ; le Mutex garantit une seule
// execution a la fois si l'activite repasse ON_START pendant qu'une sequence tourne deja.
class Startup(
    private val context: Context,
    private val db: TmmDatabase,
    private val prefs: AppPrefs,
    private val jobRepository: JobRepository,
    private val refreshCoordinator: RefreshCoordinator,
) {
    private val mutex = Mutex()
    // Synchro PC lancee sans l'attendre (rescanSeries) : survit a la sortie de la Fiche serie.
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _results = MutableSharedFlow<StartupResult>(extraBufferCapacity = 1)

    // Chaque resultat, quel que soit l'appelant : Actualiser (Accueil) relance run() sans lire le retour,
    // et TmmRoot doit quand meme renvoyer vers l'ecran Permission si l'acces Mihon a ete retire.
    val results: SharedFlow<StartupResult> = _results.asSharedFlow()

    // Tout sur IO : les appels SAF (children, openInput) et la lecture du .tachibk sont bloquants, et
    // TmmRoot/Reglages/Accueil lancaient run() depuis le thread principal (UI figee au lancement).
    suspend fun run(forceRescan: Boolean = false): StartupResult = withContext(Dispatchers.IO) { runLocked(forceRescan) }.also { _results.tryEmit(it) }

    /**
     * Tirer pour recharger sur la Fiche serie : relit seulement les dossiers de cette serie (le rescan
     * complet relisait toute la bibliotheque Mihon) et rend la main sans attendre la synchro avec le PC,
     * qui continue en arriere-plan (plus d'une minute sur Drive apres une nuit de traduction).
     */
    suspend fun rescanSeries(seriesKey: String): StartupResult = withContext(Dispatchers.IO) {
        val series = db.seriesDao().get(seriesKey)
        val dirs = db.downloadedChapterDao().observeForSeries(seriesKey).first().mapTo(HashSet()) { it.seriesDir }
        series?.let { dirs += MihonNaming.getMangaDirName(it.title) }
        runLocked(forceRescan = false, forceSeriesDirs = dirs, awaitSync = false)
    }.also { _results.tryEmit(it) }

    /**
     * Porte rapide du premier affichage : onboarding, permission SAF et lecture de la racine Mihon,
     * sans scan, import de sauvegarde ni poll. TmmRoot affiche l'ecran tout de suite (cache Room) puis
     * lance run() en arriere-plan.
     */
    suspend fun gate(): StartupResult = withContext(Dispatchers.IO) { gateOrRoot().first }

    private suspend fun gateOrRoot(): Pair<StartupResult, Pair<MihonFs, String>?> {
        if (!prefs.onboardingDone.first()) return StartupResult.ONBOARDING to null

        val treeUriString = prefs.mihonTreeUri.first() ?: return StartupResult.PERMISSION_NEEDED to null
        val treeUri = Uri.parse(treeUriString)
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        if (!hasPermission) return StartupResult.PERMISSION_NEEDED to null

        val fs: MihonFs = MihonTree(context.contentResolver, treeUri)
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            fs.children(docId)
            StartupResult.DONE to (fs to docId)
        } catch (e: Exception) {
            StartupResult.PERMISSION_NEEDED to null
        }
    }

    private suspend fun runLocked(
        forceRescan: Boolean,
        forceSeriesDirs: Set<String> = emptySet(),
        awaitSync: Boolean = true,
    ): StartupResult = mutex.withLock {
        val (gate, root) = gateOrRoot()
        if (root == null) return@withLock gate
        val (fs, rootDocId) = root

        // Sauvegarde ou dossier Mihon devenus illisibles en cours de route (SAF) : on garde le cache
        // Room et on poursuit, plutot que de faire planter l'app a chaque retour au premier plan.
        try {
            val backupImported = importBackupIfNeeded(fs, rootDocId)
            scanDownloads(fs, rootDocId, rematch = backupImported, force = forceRescan, forceSeriesDirs = forceSeriesDirs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("Startup", "scan Mihon interrompu : ${e.javaClass.simpleName} ${e.message}")
        }
        // Avant toute traduction automatique ou poll : sinon le premier refresh relancerait la file.
        try {
            jobRepository.resetPendingJobsOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Jamais de crash au demarrage : la remise a plat sera retentee au prochain lancement.
            AppLog.d("Startup", "remise a plat interrompue : ${e.javaClass.simpleName} ${e.message}")
        }
        if (awaitSync) {
            runAutoTranslate(db, jobRepository)
            refreshCoordinator.refresh()
        } else {
            syncScope.launch {
                // Hors de tout appelant : une exception ici ferait planter l'app.
                try {
                    runAutoTranslate(db, jobRepository)
                    refreshCoordinator.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.d("Startup", "synchro en arriere-plan interrompue : ${e.javaClass.simpleName}")
                }
            }
        }
        StartupResult.DONE
    }

    /** true si une nouvelle sauvegarde a ete importee (le catalogue Room a change). */
    private suspend fun importBackupIfNeeded(fs: MihonFs, rootDocId: String): Boolean {
        val autobackupId = fs.findChild(rootDocId, "autobackup")?.docId ?: return false
        val newest = fs.children(autobackupId)
            .filter { !it.isDirectory && it.name.endsWith(".tachibk") }
            .maxByOrNull { it.lastModified }
            ?: return false
        val known = db.backupImportDao().observe().first()
        val changed = known == null || known.name != newest.name || known.size != newest.size ||
            known.lastModified != newest.lastModified
        if (!changed) return false

        // Sauvegarde illisible : on garde le cache Room existant plutot que de planter au demarrage.
        val backup = try {
            fs.openInput(newest.docId).use { BackupReader.read(it) }
        } catch (e: Exception) {
            return false
        }
        BackupImporter(db).import(backup)
        db.backupImportDao().upsert(
            BackupImportEntity(
                name = newest.name,
                size = newest.size,
                lastModified = newest.lastModified,
                importedAt = System.currentTimeMillis(),
                source = "autobackup",
            ),
        )
        return true
    }

    private suspend fun scanDownloads(fs: MihonFs, rootDocId: String, rematch: Boolean, force: Boolean, forceSeriesDirs: Set<String>) {
        val scanner = DownloadScanner(fs, db)
        val matcherOutdated = prefs.matcherVersion.first() < MATCHER_VERSION
        // Mise a jour du scan : le cache par date de dossier garde les trous laisses par l'ancienne
        // version, on relit donc tout une fois.
        fs.findChild(rootDocId, "downloads")?.let { scanner.scanDownloads(it.docId, force || matcherOutdated, forceSeriesDirs) }
        if (rematch || matcherOutdated) scanner.rematch()
        if (matcherOutdated) prefs.setMatcherVersion(MATCHER_VERSION)
        fs.findChild(rootDocId, "local")?.let { scanner.scanLocal(it.docId) }
        prefs.setLastScanAt(System.currentTimeMillis())
    }
}

// 1 : Matcher tolere le suffixe de langue des dossiers source Mihon ("MangaDex (EN)").
// 2 : une serie en double (ajoutee deux fois, ou FR + EN sur une meme source) recoit ses propres chapitres.
// 3 : un dossier sans serie dans la sauvegarde devient sa propre serie (numeros lus dans le nom).
// 5 : relecture complete une fois (CBZ ".cbz.tmp" en cours, dossier illisible qui stoppait le scan).
private const val MATCHER_VERSION = 5
