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
import com.tmm.mobile.mihon.MihonTree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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
    private val _results = MutableSharedFlow<StartupResult>(extraBufferCapacity = 1)

    // Chaque resultat, quel que soit l'appelant : Actualiser (Accueil) relance run() sans lire le retour,
    // et TmmRoot doit quand meme renvoyer vers l'ecran Permission si l'acces Mihon a ete retire.
    val results: SharedFlow<StartupResult> = _results.asSharedFlow()

    // Tout sur IO : les appels SAF (children, openInput) et la lecture du .tachibk sont bloquants, et
    // TmmRoot/Reglages/Accueil lancaient run() depuis le thread principal (UI figee au lancement).
    suspend fun run(): StartupResult = withContext(Dispatchers.IO) { runLocked() }.also { _results.tryEmit(it) }

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

    private suspend fun runLocked(): StartupResult = mutex.withLock {
        val (gate, root) = gateOrRoot()
        if (root == null) return@withLock gate
        val (fs, rootDocId) = root

        // Sauvegarde ou dossier Mihon devenus illisibles en cours de route (SAF) : on garde le cache
        // Room et on poursuit, plutot que de faire planter l'app a chaque retour au premier plan.
        try {
            val backupImported = importBackupIfNeeded(fs, rootDocId)
            scanDownloads(fs, rootDocId, rematch = backupImported)
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
        runAutoTranslate(db, jobRepository)
        refreshCoordinator.refresh()
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

    private suspend fun scanDownloads(fs: MihonFs, rootDocId: String, rematch: Boolean) {
        val scanner = DownloadScanner(fs, db)
        fs.findChild(rootDocId, "downloads")?.let { scanner.scanDownloads(it.docId) }
        val matcherOutdated = prefs.matcherVersion.first() < MATCHER_VERSION
        if (rematch || matcherOutdated) scanner.rematch()
        if (matcherOutdated) prefs.setMatcherVersion(MATCHER_VERSION)
        fs.findChild(rootDocId, "local")?.let { scanner.scanLocal(it.docId) }
        prefs.setLastScanAt(System.currentTimeMillis())
    }
}

// 1 : Matcher tolere le suffixe de langue des dossiers source Mihon ("MangaDex (EN)").
// 2 : une serie en double (ajoutee deux fois, ou FR + EN sur une meme source) recoit ses propres chapitres.
private const val MATCHER_VERSION = 2
