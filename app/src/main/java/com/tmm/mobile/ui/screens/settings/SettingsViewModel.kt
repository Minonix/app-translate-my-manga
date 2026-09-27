package com.tmm.mobile.ui.screens.settings

import android.net.Uri
import android.provider.DocumentsContract
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.Retention
import com.tmm.mobile.data.db.BackupImportEntity
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsValues
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _state = MutableStateFlow(SettingsState())
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
    }

    private suspend fun observeHeartbeat() {
        container.prefs.heartbeatJson.collect { json ->
            val heartbeat = json?.let { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }
            _state.update { it.copy(pcName = pcNameOf(heartbeat), pcVersionProtocol = pcVersionOf(heartbeat)) }
            _state.update { it.copy(isPcOnline = isOnline(heartbeat), pcLastSignalText = lastSignalTextOf(heartbeat)) }
        }
    }

    private fun pcNameOf(heartbeat: Heartbeat?): String = heartbeat?.pcName ?: "PC"

    private fun pcVersionOf(heartbeat: Heartbeat?): String =
        heartbeat?.let { "TMM-Win ${it.appVersion} · protocole v1" } ?: "PC"

    // PLAN_GEMINI.md 7.1 : en ligne (<5 min), vu (5 min-24h), hors ligne (>24h ou absent). L'ecran
    // Reglages n'a qu'une pastille binaire : le cas "vu" compte comme hors ligne pour cette pastille.
    private fun isOnline(heartbeat: Heartbeat?): Boolean {
        if (heartbeat == null) return false
        val updatedAt = runCatching { Instant.parse(heartbeat.updatedAt).toEpochMilli() }.getOrNull() ?: return false
        return container.clock.nowMillis() - updatedAt < 5 * 60_000L
    }

    private fun lastSignalTextOf(heartbeat: Heartbeat?): String {
        if (heartbeat == null) return "Aucun signal reçu" // ajouté, PLAN_GEMINI.md 7.8
        val updatedAt = runCatching { Instant.parse(heartbeat.updatedAt).toEpochMilli() }.getOrNull()
            ?: return "Aucun signal reçu"
        val relative = Formatters.relativeFr(updatedAt, container.clock.nowMillis())
        return "Dernier signal $relative · le PC écrit sur Drive toutes les minutes"
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
            _state.update { it.copy(lastBackupInfo = backupInfoText(backup)) }
        }
    }

    private suspend fun backupInfoText(backup: BackupImportEntity?): String {
        if (backup == null) return "Aucune sauvegarde lue" // ajouté, aucune sauvegarde encore importee
        val seriesCount = container.db.seriesDao().observeAll().first().size
        return "${importedAtFr(backup.importedAt)} · $seriesCount séries"
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
        _state.update { it.copy(pcTranslationSummary = summary) }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
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
