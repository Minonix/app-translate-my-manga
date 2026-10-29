package com.tmm.mobile.ui.screens.settings

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.tmm.mobile.BuildConfig
import com.tmm.mobile.core.AppContainer
import java.io.File
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.DEFAULT_DIRECT_URL
import com.tmm.mobile.data.PC_ONLINE_MILLIS
import com.tmm.mobile.data.Retention
import com.tmm.mobile.data.db.BackupImportEntity
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsValues
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.tmm.mobile.mihon.MihonTree
import com.tmm.mobile.transport.DirectTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.journalExportText

private val BACKUP_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val BACKUP_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM")

// PLAN_GEMINI.md 7.8 : Reglages. Toute ecriture d'un reglage cote PC passe par settingsSync.change()
// (5.18) - jamais d'ecriture directe de settings_json. Compte Google/dossier Mihon/retention/wifi_only
// sont des reglages locaux (AppPrefs), pas des SettingsCommand.
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        // Etat neutre avant la lecture des prefs : les defauts de SettingsState sont ceux de la maquette.
        SettingsState(
            pcName = "PC", pcVersionProtocol = "PC", isPcOnline = false, pcLastSignalText = "Aucun signal reçu",
            googleAccount = "", mihonFolder = "", lastBackupInfo = "", pcTranslationSummary = "", logsInfo = "",
            languageSummary = "", engineSummary = "",
        ),
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var toastJob: Job? = null

    init {
        viewModelScope.launch { observeHeartbeat() }
        viewModelScope.launch { observeAccount() }
        viewModelScope.launch { observeMihonFolder() }
        viewModelScope.launch { observeRetention() }
        viewModelScope.launch { observeWifiOnly() }
        viewModelScope.launch { observeBackupInfo() }
        viewModelScope.launch { observeLogsInfo() }
        viewModelScope.launch { loadTranslationSummary() }
        viewModelScope.launch { observeDirect() }
        viewModelScope.launch { observePcDirect() }
        checkUpdate()
        viewModelScope.launch {
            combine(container.db.seriesDao().observeAll(), container.db.seriesSettingsDao().observeAutoTranslateSeriesKeys()) { series, keys ->
                series.filter { it.seriesKey in keys }.map { FollowedSeries(it.seriesKey, it.title) }.sortedBy { it.title }
            }.collect { followed -> _state.update { it.copy(followedSeries = followed) } }
        }
    }

    // URL connue (publiee par le PC, deja associee, ou Hub VPS Tailscale par defaut) pre-remplie ;
    // l'etat suit l'appairage et un health-check rapide a chaque changement.
    private suspend fun observeDirect() {
        combine(container.prefs.directUrl, container.prefs.directPairingExpired) { url, expired -> url to expired }
            .collect { (url, expired) ->
                val status = directStatusOf(container.router.hub, expired)
                _state.update {
                    it.copy(directStatus = status, directUrl = if (it.directUrl.isEmpty()) url ?: DEFAULT_DIRECT_URL else it.directUrl)
                }
            }
    }

    // PC en secours : adresse publiee par le PC (pc/remote.json) ou deja associee, jamais celle du Hub par defaut.
    private suspend fun observePcDirect() {
        val slot = container.prefs.pcSlot
        combine(slot.url, slot.pairingExpired) { url, expired -> url to expired }
            .collect { (url, expired) ->
                val status = directStatusOf(container.router.pc, expired)
                _state.update {
                    it.copy(pcDirectStatus = status, pcDirectUrl = if (it.pcDirectUrl.isEmpty()) url.orEmpty() else it.pcDirectUrl)
                }
            }
    }

    private suspend fun directStatusOf(direct: DirectTransport, expired: Boolean): DirectStatus = when {
        expired -> DirectStatus.Expired
        !direct.isPaired() -> DirectStatus.NotPaired
        direct.health() != null -> DirectStatus.Direct
        else -> DirectStatus.Unreachable
    }

    private fun pair() {
        val s = _state.value
        val targetUrl = s.directUrl.ifBlank { DEFAULT_DIRECT_URL }
        if (s.pairing || s.pairingCode.length != 6 || targetUrl.isBlank()) return
        _state.update { it.copy(pairing = true, directUrl = targetUrl) }
        viewModelScope.launch {
            val status = pairSlot(container.router.hub, targetUrl, s.pairingCode)
            _state.update {
                it.copy(
                    pairing = false,
                    pairingCode = if (status != null) "" else it.pairingCode,
                    directStatus = status ?: it.directStatus,
                )
            }
        }
    }

    private fun pairPc() {
        val s = _state.value
        val targetUrl = s.pcDirectUrl
        if (s.pcPairing || s.pcPairingCode.length != 6 || targetUrl.isBlank()) return
        _state.update { it.copy(pcPairing = true) }
        viewModelScope.launch {
            val status = pairSlot(container.router.pc, targetUrl, s.pcPairingCode)
            _state.update {
                it.copy(
                    pcPairing = false,
                    pcPairingCode = if (status != null) "" else it.pcPairingCode,
                    pcDirectStatus = status ?: it.pcDirectStatus,
                )
            }
        }
    }

    // Echange le code contre un jeton pour cet emplacement ; null si l'appairage a echoue.
    private suspend fun pairSlot(direct: DirectTransport, url: String, code: String): DirectStatus? {
        val ok = runCatching { direct.pair(url, code, Build.MODEL ?: "android") }
            .onFailure { AppLog.d("SettingsViewModel", "appairage ${direct.slot.id} impossible : ${it.javaClass.simpleName} ${it.message}") }
            .isSuccess
        if (!ok) return null.also { showToast(SettingsToast.PairFailed) }
        direct.slot.setPairingExpired(false)
        val status = if (direct.health() != null) DirectStatus.Direct else DirectStatus.Unreachable
        showToast(SettingsToast.Paired)
        return status
    }

    private suspend fun observeHeartbeat() {
        combine(container.prefs.heartbeatJson, container.prefs.heartbeatSeenAt) { json, seenAt -> json to seenAt }
            .collect { (json, seenAt) ->
                // R15 : un heartbeat illisible en cache ne doit pas tuer la collecte (ni l'ecran).
                val heartbeat = json?.let { runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }.getOrNull() }
                val signalAt = signalAtOf(heartbeat, seenAt)
                _state.update {
                    it.copy(
                        pcName = pcNameOf(heartbeat), pcVersionProtocol = pcVersionOf(heartbeat),
                        isPcOnline = isOnline(signalAt), pcLastSignalText = lastSignalTextOf(signalAt),
                    )
                }
            }
    }

    private fun pcNameOf(heartbeat: Heartbeat?): String = heartbeat?.pcName ?: "PC"

    private fun pcVersionOf(heartbeat: Heartbeat?): String =
        heartbeat?.let { "TMM-Win ${it.appVersion} · protocole v1" } ?: "PC"

    // PLAN_GEMINI.md 7.1 : en ligne (<5 min), vu (5 min-24h), hors ligne (>24h ou absent). L'ecran
    // Reglages n'a qu'une pastille binaire : le cas "vu" compte comme hors ligne pour cette pastille.
    // R9 : heure du dernier signal sur l'horloge du telephone, deduite de l'heure serveur (RefreshCoordinator) ;
    // updatedAt du PC seulement a defaut (cache d'avant la mise a jour).
    private fun signalAtOf(heartbeat: Heartbeat?, seenAt: Long?): Long? {
        if (heartbeat == null) return null
        return seenAt ?: runCatching { Instant.parse(heartbeat.updatedAt).toEpochMilli() }.getOrNull()
    }

    private fun isOnline(signalAt: Long?): Boolean =
        signalAt != null && container.clock.nowMillis() - signalAt < PC_ONLINE_MILLIS

    private fun lastSignalTextOf(signalAt: Long?): String {
        if (signalAt == null) return "Aucun signal reçu" // ajouté, PLAN_GEMINI.md 7.8
        val relative = Formatters.relativeFr(signalAt, container.clock.nowMillis())
        // Maquette v2 : "Allumé · vu il y a 17 s" ; au-dela de 5 min, meme vocabulaire que l'Accueil ("PC éteint").
        val power = if (isOnline(signalAt)) "Allumé" else "Éteint"
        return "$power · vu $relative"
    }

    private suspend fun observeAccount() {
        container.prefs.googleEmail.collect { email ->
            if (email != null) _state.update { it.copy(googleAccount = email) }
        }
    }

    private suspend fun observeMihonFolder() {
        container.prefs.mihonTreeUri.collect { uriString ->
            val readable = uriString?.let(::readableMihonPath)
            if (readable != null) _state.update { it.copy(mihonFolder = readable) }
        }
    }

    // PLAN_GEMINI.md 7.8 : DocumentsContract.getTreeDocumentId, partie apres ':', prefixee de '/'.
    private fun readableMihonPath(uriString: String): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        "/" + docId.substringAfter(':')
    }.getOrNull()

    private suspend fun observeRetention() {
        container.prefs.retention.collect { retention ->
            _state.update { it.copy(driveRetention = retention.toUi()) }
        }
    }

    private fun Retention.toUi(): DriveRetention = when (this) {
        Retention.NEVER -> DriveRetention.Never
        Retention.DAYS30 -> DriveRetention.Days30
        Retention.AFTER_RECEIVE -> DriveRetention.AfterDownload
    }

    private fun DriveRetention.toPrefs(): Retention = when (this) {
        DriveRetention.Never -> Retention.NEVER
        DriveRetention.Days30 -> Retention.DAYS30
        DriveRetention.AfterDownload -> Retention.AFTER_RECEIVE
    }

    private suspend fun observeWifiOnly() {
        container.prefs.wifiOnly.collect { wifi -> _state.update { it.copy(wifiOnly = wifi) } }
    }

    private suspend fun observeBackupInfo() {
        container.db.backupImportDao().observe().collect { backup ->
            _state.update { it.copy(lastBackupInfo = backupInfoText(backup), backupFileName = backup?.name.orEmpty()) }
        }
    }

    private suspend fun backupInfoText(backup: BackupImportEntity?): String {
        if (backup == null) return "Aucune sauvegarde lue" // ajouté, aucune sauvegarde encore importee
        val seriesCount = container.db.seriesDao().observeAll().first().size
        // Maquette v2 : « Lue aujourd'hui 03:00 · 142 séries, 7 catégories ».
        val categoryCount = container.db.categoryDao().observeAll().first().size
        val categories = when (categoryCount) {
            0 -> ""
            1 -> ", 1 catégorie"
            else -> ", $categoryCount catégories"
        }
        return "${importedAtFr(backup.importedAt)} · $seriesCount séries$categories"
    }

    // PLAN_GEMINI.md 7.6 : aujourd'hui HH:mm, hier HH:mm, sinon le jj/mm.
    private fun importedAtFr(importedAtMillis: Long): String {
        val zone = ZoneId.systemDefault()
        val importedDate = Instant.ofEpochMilli(importedAtMillis).atZone(zone)
        val today = Instant.ofEpochMilli(container.clock.nowMillis()).atZone(zone)
        val time = importedDate.format(BACKUP_TIME_FMT)
        return when (importedDate.toLocalDate()) {
            today.toLocalDate() -> "Aujourd'hui $time"
            today.toLocalDate().minusDays(1) -> "Hier $time"
            else -> "le ${importedDate.format(BACKUP_DATE_FMT)}"
        }
    }

    private fun observeLogsInfo() {
        val text = journalExportText(AppLog.entries(), container.clock.nowMillis())
        val ko = text.toByteArray().size / 1024
        _state.update { it.copy(logsInfo = "7 derniers jours · $ko Ko") }
    }

    // PLAN_GEMINI.md 7.8 : cle courte du moteur, verifiee contre SettingsSchema.allowedValues (7.5).
    private fun engineShort(id: String): String = when (id) {
        "gemini" -> "Gemini"
        "openai" -> "OpenAI"
        "claude" -> "Claude"
        "deepl" -> "DeepL"
        "groq" -> "Groq"
        "openrouter" -> "OpenRouter"
        "tokenhub" -> "TokenHub"
        "offline" -> "Hors ligne"
        "local_llm" -> "LLM local"
        else -> id // cle absente de la liste fermee du CdC 7.5 - a signaler
    }

    private fun translationSummary(values: SettingsValues): String {
        val engine = engineShort(values.translationProvider)
        val langs = "${Formatters.langLabelFr(values.sourceLanguage)} vers ${Formatters.langLabelFr(values.targetLanguage)}"
        return "$engine · $langs · ${values.chapterConcurrency} en parallèle"
    }

    private suspend fun loadTranslationSummary() {
        val values = container.settingsSync.values()
        val summary = if (values != null) translationSummary(values) else "Réglages non reçus du PC" // ajouté
        _state.update { it.copy(pcTranslationSummary = summary,
            languageSummary = values?.let { "${Formatters.langLabelFr(it.sourceLanguage)} vers ${Formatters.langLabelFr(it.targetLanguage)}" } ?: summary,
            engineSummary = values?.let { com.tmm.mobile.ui.screens.translationsettings.TranslationProvider.entries.find { p -> p.id == it.translationProvider }?.label ?: engineShort(it.translationProvider) } ?: summary) }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.ToggleAdvanced -> _state.update { it.copy(advancedExpanded = !it.advancedExpanded) }
            SettingsEvent.OpenDirect -> _state.update { it.copy(directOpen = true) }
            SettingsEvent.CloseDirect -> _state.update { it.copy(directOpen = false) }
            SettingsEvent.ToggleFollowed -> _state.update { it.copy(followedExpanded = !it.followedExpanded) }
            is SettingsEvent.Unfollow -> viewModelScope.launch {
                val dao = container.db.seriesSettingsDao()
                dao.observe(event.seriesKey).first()?.let { dao.upsert(it.copy(autoTranslate = false)) }
            }
            SettingsEvent.Reimport -> reimport()
            // BLOQUÉ (E10) : re-authentifier un autre compte Google necessite GoogleAuth, non expose
            // par AppContainer (interface figee) - reste un stub visuel jusqu'a exposition explicite.
            SettingsEvent.ChangeGoogleAccount -> showToast(SettingsToast.ChooseAccount)
            SettingsEvent.CycleDriveRetention -> {
                val next = _state.value.driveRetention.next()
                _state.update { it.copy(driveRetention = next) }
                viewModelScope.launch { container.prefs.setRetention(next.toPrefs()) }
            }
            // Le vrai selecteur de dossier (OpenDocumentTree) est lance par SettingsRoute (I/O Activity).
            SettingsEvent.ChangeMihonFolder -> Unit
            is SettingsEvent.SetWifiOnly -> {
                _state.update { it.copy(wifiOnly = event.enabled) }
                viewModelScope.launch { container.prefs.setWifiOnly(event.enabled) }
            }
            SettingsEvent.ToggleWifiOnly -> onEvent(SettingsEvent.SetWifiOnly(!_state.value.wifiOnly))
            // Le vrai export (CreateDocument) est lance par SettingsRoute ; onLogsExported() affiche le toast.
            SettingsEvent.ExportLogs -> Unit
            SettingsEvent.DismissToast -> _state.update { it.copy(toast = null) }
            is SettingsEvent.DirectUrlChanged -> _state.update { it.copy(directUrl = event.url.trim()) }
            is SettingsEvent.PairingCodeChanged ->
                _state.update { it.copy(pairingCode = event.code.filter(Char::isDigit).take(6)) }
            SettingsEvent.Pair -> pair()
            SettingsEvent.CheckUpdate -> checkUpdate()
            SettingsEvent.InstallUpdate -> Unit
            SettingsEvent.ForgetPc -> viewModelScope.launch {
                container.router.hub.forget()
                _state.update { it.copy(directStatus = DirectStatus.NotPaired, directUrl = DEFAULT_DIRECT_URL) }
            }
            is SettingsEvent.PcDirectUrlChanged -> _state.update { it.copy(pcDirectUrl = event.url.trim()) }
            is SettingsEvent.PcPairingCodeChanged ->
                _state.update { it.copy(pcPairingCode = event.code.filter(Char::isDigit).take(6)) }
            SettingsEvent.PairPc -> pairPc()
            SettingsEvent.ForgetBackupPc -> viewModelScope.launch {
                container.router.pc.forget()
                _state.update { it.copy(pcDirectStatus = DirectStatus.NotPaired, pcDirectUrl = "") }
            }
        }
    }

    private var updateJob: Job? = null

    private fun checkUpdate() {
        if (updateJob?.isActive == true) return
        _state.update { it.copy(update = AppUpdateState.Checking) }
        updateJob = viewModelScope.launch {
            val next = try {
                container.appUpdater.newerThan(BuildConfig.VERSION_NAME)
                    ?.let { AppUpdateState.Available(it) } ?: AppUpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("SettingsViewModel", "verification de mise a jour impossible : ${e.message}")
                AppUpdateState.Failed
            }
            _state.update { it.copy(update = next) }
        }
    }

    /** Telecharge l'APK propose (une seule fois) puis le remet a [install]. */
    fun downloadUpdate(install: (File) -> Unit) {
        when (val current = _state.value.update) {
            is AppUpdateState.Ready -> install(current.apk)
            is AppUpdateState.Available -> {
                if (updateJob?.isActive == true) return
                val release = current.release
                _state.update { it.copy(update = AppUpdateState.Downloading(release, 0)) }
                updateJob = viewModelScope.launch {
                    try {
                        val apk = container.appUpdater.download(release) { done, total ->
                            val percent = if (total > 0) (done * 100 / total).toInt() else 0
                            _state.update { s ->
                                if ((s.update as? AppUpdateState.Downloading)?.percent == percent) s
                                else s.copy(update = AppUpdateState.Downloading(release, percent))
                            }
                        }
                        _state.update { it.copy(update = AppUpdateState.Ready(release, apk)) }
                        install(apk)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.d("SettingsViewModel", "telechargement de la mise a jour impossible : ${e.message}")
                        _state.update { it.copy(update = AppUpdateState.Available(release)) }
                        showToast(SettingsToast.UpdateFailed)
                    }
                }
            }
            else -> Unit
        }
    }

    private var reimportJob: Job? = null

    private fun reimport() {
        if (reimportJob?.isActive == true) return
        _state.update { it.copy(importError = false, importStep = BackupImportStep.ReadingFile) }
        reimportJob = viewModelScope.launch {
            try {
                val uri = container.prefs.mihonTreeUri.first() ?: error("Dossier Mihon absent")
                var imported = false
                withContext(Dispatchers.IO) {
                    val tree = uri.toUri()
                    importReimportSequence(MihonTree(container.context.contentResolver, tree), DocumentsContract.getTreeDocumentId(tree), container.db, container.clock) { step ->
                        _state.update { it.copy(importStep = step) }
                        if (step == BackupImportStep.Comparing) imported = true
                    }
                }
                if (!imported) error("Sauvegarde absente")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(importError = true) }
            } finally {
                _state.update { it.copy(importStep = BackupImportStep.Idle) }
            }
        }
    }

    fun onLogsExported() = showToast(SettingsToast.LogsReady)

    private fun showToast(toast: SettingsToast) {
        toastJob?.cancel()
        _state.update { it.copy(toast = toast) }
        toastJob = viewModelScope.launch {
            delay(2600)
            _state.update { if (it.toast == toast) it.copy(toast = null) else it }
        }
    }
}
