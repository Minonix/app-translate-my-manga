package com.tmm.mobile.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.status.GlobalCounts
import com.tmm.mobile.mihon.BackupImporter
import com.tmm.mobile.mihon.BackupReader
import com.tmm.mobile.mihon.DownloadScanner
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonTree
import com.tmm.mobile.transport.GoogleAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "OnboardingViewModel"

// PLAN_GEMINI.md 7.10 : etapes reelles (Google, dossier Mihon, import). La plomberie SAF/Activity
// Result (takePersistableUriPermission, StartIntentSenderForResult) reste dans OnboardingRoute ;
// cette classe n'a besoin que d'un Uri/Intent deja obtenu, ce qui rend attachMihonTree()/runImport()
// testables sur FakeMihonFs sans Robolectric+SAF (cf OnboardingViewModelTest).
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    private val googleAuth = GoogleAuth(container.context)

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _pendingAuthIntent = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingAuthIntent: StateFlow<IntentSenderRequest?> = _pendingAuthIntent.asStateFlow()

    private var job: Job? = null
    private var fs: MihonFs? = null
    private var rootDocId: String? = null

    // Le picker SAF (etape 2) et le consentement Google (etape 1) sont des Activity externes ;
    // Android peut tuer le process pendant qu'elles sont au premier plan, ce qui recree ce
    // ViewModel a l'etat par defaut (isGoogleConnected=false) et boucle sur "se reconnecter".
    // On relit les memes signaux persistes que Startup.run() pour ne pas rejouer une etape acquise.
    init {
        viewModelScope.launch {
            val email = container.prefs.googleEmail.first()
            if (email != null) {
                _state.update { it.copy(isGoogleConnected = true, googleAccount = email, step = maxOf(it.step, 2)) }
            }
            val treeUriString = container.prefs.mihonTreeUri.first()
            if (treeUriString != null) {
                val treeUri = Uri.parse(treeUriString)
                val hasPermission = container.context.contentResolver.persistedUriPermissions.any {
                    it.uri == treeUri && it.isReadPermission && it.isWritePermission
                }
                if (hasPermission) {
                    try {
                        val tree = MihonTree(container.context.contentResolver, treeUri)
                        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                        val displayPath = treeUri.lastPathSegment?.substringAfterLast(':')?.let { "/$it" } ?: treeUri.toString()
                        withContext(Dispatchers.IO) { tree.children(rootId) }
                        attachMihonTree(tree, rootId, displayPath)
                    } catch (e: Exception) {
                        AppLog.d(TAG, "restauration du dossier Mihon a echoue : ${e.message}")
                    }
                }
            }
        }
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.PrimaryAction -> handlePrimaryAction()
            OnboardingEvent.PreviousStep -> handlePreviousStep()
        }
    }

    private fun handlePrimaryAction() {
        val current = _state.value
        // Les branches "pas encore connecte"/"pas encore de dossier" sont interceptees par
        // OnboardingRoute (elles ont besoin d'une Activity pour lancer le consentement Google ou
        // OpenDocumentTree) : ce qui reste ici, c'est l'avancee une fois l'etape deja acquise.
        if (current.step == 1 && current.isGoogleConnected) {
            _state.update { it.copy(step = 2) }
            return
        }
        if (current.step == 2 && current.isMihonSelected) {
            _state.update { it.copy(step = 3) }
            runImport()
        }
    }

    private fun handlePreviousStep() {
        if (_state.value.isWorking) return
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }
    }

    // --- Etape 1 : Google (CdC/PLAN 5.11) ---------------------------------------------------

    fun startGoogleAuth() {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true) }
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val result = googleAuth.authorize()
                val pendingIntent = googleAuth.pendingIntent(result)
                if (pendingIntent != null) {
                    _pendingAuthIntent.value = IntentSenderRequest.Builder(pendingIntent).build()
                } else {
                    finishGoogleAuth(result)
                }
            } catch (e: Exception) {
                AppLog.d(TAG, "authorize() a echoue : ${e.message}")
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    fun onGoogleAuthResult(data: Intent?) {
        _pendingAuthIntent.value = null
        if (data == null) {
            _state.update { it.copy(isWorking = false) }
            return
        }
        viewModelScope.launch {
            try {
                finishGoogleAuth(googleAuth.resultFromIntent(data))
            } catch (e: Exception) {
                AppLog.d(TAG, "resultFromIntent() a echoue : ${e.message}")
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private suspend fun finishGoogleAuth(result: AuthorizationResult) {
        // BLOQUÉ (point ouvert, cf rapport) : AuthorizationResult ne porte pas l'e-mail du compte.
        // container.transport.accountEmail() (Drive "about", deja cable pour Reglages/export des
        // journaux) est la seule source d'identite reelle du depot ; best-effort, ne bloque pas
        // l'onboarding si hors-ligne ou si le scope ne l'autorise pas.
        var email: String? = null
        try {
            container.transport.ensureLayout()
            email = container.transport.accountEmail()
            if (email != null) container.prefs.setGoogleEmail(email)
        } catch (e: Exception) {
            AppLog.d(TAG, "ensureLayout/accountEmail a echoue, poursuite sans e-mail : ${e.message}")
        }
        _state.update {
            it.copy(
                isWorking = false,
                isGoogleConnected = true,
                googleAccount = email ?: it.googleAccount,
            )
        }
    }

    // --- Etape 2 : dossier Mihon (SAF) -------------------------------------------------------

    // Appele par OnboardingRoute avec l'Uri renvoye par OpenDocumentTree (null si annule).
    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            container.context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            container.prefs.setMihonTreeUri(uri.toString())
            val tree = MihonTree(container.context.contentResolver, uri)
            val rootId = DocumentsContract.getTreeDocumentId(uri)
            val displayPath = uri.lastPathSegment?.substringAfterLast(':')?.let { "/$it" } ?: uri.toString()
            attachMihonTree(tree, rootId, displayPath)
        }
    }

    // Seam de test (PLAN_GEMINI.md E10.3 : partie non-SAF testable sur FakeMihonFs, sans Robolectric+SAF).
    internal fun attachMihonTree(mihonFs: MihonFs, docId: String, displayPath: String) {
        fs = mihonFs
        rootDocId = docId
        val subs = _state.value.subfolders.map { sub ->
            sub.copy(found = mihonFs.findChild(docId, sub.name.removeSuffix("/")) != null)
        }
        _state.update {
            it.copy(isWorking = false, isMihonSelected = true, mihonFolderPath = displayPath, subfolders = subs)
        }
    }

    // --- Etape 3 : import de la derniere sauvegarde (PLAN_GEMINI.md 5.16/7.10) --------------

    private fun runImport() {
        val mihonFs = fs
        val docId = rootDocId
        if (mihonFs == null || docId == null) return
        job?.cancel()
        job = viewModelScope.launch { runImport(mihonFs, docId) }
    }

    // Seam de test : logique d'import pure (aucune API SAF), testable sur FakeMihonFs + Room en memoire.
    internal suspend fun runImport(mihonFs: MihonFs, docId: String) {
        val autobackupId = mihonFs.findChild(docId, "autobackup")?.docId
        val newest = autobackupId?.let { id ->
            mihonFs.children(id).filter { !it.isDirectory && it.name.endsWith(".tachibk") }.maxByOrNull { it.lastModified }
        }
        // Sauvegarde illisible (format Mihon inattendu, fichier tronque) : on continue comme sans
        // sauvegarde plutot que de laisser l'exception tuer l'app a l'etape 3.
        val backup = newest?.let {
            try {
                withContext(Dispatchers.IO) { mihonFs.openInput(it.docId).use { input -> BackupReader.read(input) } }
            } catch (e: Exception) {
                AppLog.d(TAG, "lecture de ${it.name} a echoue : ${e.message}")
                null
            }
        }
        if (newest == null || backup == null) {
            _state.update {
                it.copy(hasBackup = false, readProgress = 1f, seriesCount = 0, categoriesCount = 0, downloadedCount = 0, isReadDone = true, toSendCount = 0)
            }
            return
        }
        _state.update { it.copy(hasBackup = true, backupFileName = "autobackup/${newest.name}") }

        BackupImporter(container.db).import(backup)
        mihonFs.findChild(docId, "downloads")?.let { DownloadScanner(mihonFs, container.db).scanDownloads(it.docId) }

        val finalSeries = backup.backupManga.size
        val finalCategories = backup.backupCategories.size
        val finalDownloaded = container.db.downloadedChapterDao().observeAll().first()
            .mapNotNull { it.seriesKey }.toSet().size

        delay(350)
        for (i in 1..20) {
            delay(60)
            val fraction = i / 20f
            _state.update {
                it.copy(
                    readProgress = fraction,
                    seriesCount = (finalSeries * fraction).toInt(),
                    categoriesCount = (finalCategories * fraction).toInt(),
                    downloadedCount = (finalDownloaded * fraction).toInt(),
                    isReadDone = i == 20,
                )
            }
        }
        val toSend = GlobalCounts.transfersBadgeCount(container.db).first()
        _state.update { it.copy(toSendCount = toSend) }
    }

    // --- Fin ----------------------------------------------------------------------------------

    fun finishOnboarding() {
        viewModelScope.launch { container.prefs.setOnboardingDone(true) }
    }
}
