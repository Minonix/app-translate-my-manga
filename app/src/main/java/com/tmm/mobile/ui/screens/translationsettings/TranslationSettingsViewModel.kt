package com.tmm.mobile.ui.screens.translationsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.data.SyncBanner
import com.tmm.mobile.data.status.ErrorCatalog
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsValues
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// PLAN_GEMINI.md 7.9/5.18 : reglages de traduction du PC. Chaque changement passe par
// SettingsSync.change(key, value) - jamais d'ecriture directe de settings_json - puis suit le
// SyncBanner (SENDING pendant l'envoi, APPLIED/WAITING_PC selon awaitConfirmation()). Sans
// settings_json (readOnly), l'ecran reste en lecture seule (7.9).
class TranslationSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TranslationSettingsState(pcName = "PC", isReadOnly = true))
    val state: StateFlow<TranslationSettingsState> = _state.asStateFlow()

    private var confirmJob: Job? = null

    init {
        viewModelScope.launch { observeHeartbeat() }
        viewModelScope.launch { load() }
    }

    private suspend fun observeHeartbeat() {
        container.prefs.heartbeatJson.collect { json ->
            val heartbeat = json?.let { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }
            heartbeat?.let { hb -> _state.update { it.copy(pcName = hb.pcName) } }
        }
    }

    private suspend fun load() {
        val readOnly = container.settingsSync.readOnly()
        val values = container.settingsSync.values()
        _state.update { s ->
            (if (values != null) fromValues(s, values) else s).copy(isReadOnly = readOnly)
        }
    }

    // R3/R15 : une valeur inconnue du PC (nouvelle option, code a 2 lettres "FR"...) garde la valeur
    // courante au lieu de lever NoSuchElementException et de fermer l'ecran.
    private fun fromValues(state: TranslationSettingsState, v: SettingsValues) = state.copy(
        sourceLanguage = SourceLanguage.entries.firstOrNull { it.code == normalizeLang(v.sourceLanguage) } ?: state.sourceLanguage,
        targetLanguage = TargetLanguage.entries.firstOrNull { it.code == normalizeLang(v.targetLanguage) } ?: state.targetLanguage,
        translationProvider = TranslationProvider.entries.firstOrNull { it.id == v.translationProvider } ?: state.translationProvider,
        autoFailoverToLocalLlm = v.autoFailoverToLocalLlm,
        autoVerifyAfterTranslate = v.autoVerifyAfterTranslate,
        sendPageImages = v.sendPageImages,
        chapterConcurrency = v.chapterConcurrency,
        concurrency = v.concurrency,
        pileSize = v.pileSize,
        useGpu = v.useGpu,
        detector = BubbleDetector.entries.firstOrNull { it.id == v.detector } ?: state.detector,
        preferredOcr = PreferredOcr.entries.firstOrNull { it.id == v.preferredOcr } ?: state.preferredOcr,
        inpainter = Inpainter.entries.firstOrNull { it.id == v.inpainter } ?: state.inpainter,
        inpaintingSize = InpaintingSize.entries.firstOrNull { it.sizePx == v.inpaintingSize } ?: state.inpaintingSize,
    )

    fun onEvent(event: TranslationSettingsEvent) {
        when (event) {
            is TranslationSettingsEvent.SelectSourceLanguage ->
                change("sourceLanguage", event.value.code) { it.copy(sourceLanguage = event.value) }
            is TranslationSettingsEvent.SelectTargetLanguage ->
                change("targetLanguage", event.value.code) { it.copy(targetLanguage = event.value) }
            is TranslationSettingsEvent.SelectTranslationProvider ->
                change("translationProvider", event.value.id) { it.copy(translationProvider = event.value) }
            is TranslationSettingsEvent.SetAutoFailover ->
                change("autoFailoverToLocalLlm", event.value.toString()) { it.copy(autoFailoverToLocalLlm = event.value) }
            is TranslationSettingsEvent.SetSendPageImages ->
                change("sendPageImages", event.value.toString()) { it.copy(sendPageImages = event.value) }
            is TranslationSettingsEvent.SetAutoVerify ->
                change("autoVerifyAfterTranslate", event.value.toString()) { it.copy(autoVerifyAfterTranslate = event.value) }
            is TranslationSettingsEvent.SetChapterConcurrency ->
                change("chapterConcurrency", event.value.toString()) { it.copy(chapterConcurrency = event.value) }
            is TranslationSettingsEvent.SetConcurrency ->
                change("concurrency", event.value.toString()) { it.copy(concurrency = event.value) }
            is TranslationSettingsEvent.SetPileSize ->
                change("pileSize", event.value.toString()) { it.copy(pileSize = event.value) }
            is TranslationSettingsEvent.SetUseGpu ->
                change("useGpu", event.value.toString()) { it.copy(useGpu = event.value) }
            is TranslationSettingsEvent.SelectDetector ->
                change("detector", event.value.id) { it.copy(detector = event.value) }
            is TranslationSettingsEvent.SelectPreferredOcr ->
                change("preferredOcr", event.value.id) { it.copy(preferredOcr = event.value) }
            is TranslationSettingsEvent.SelectInpainter ->
                change("inpainter", event.value.id) { it.copy(inpainter = event.value) }
            is TranslationSettingsEvent.SelectInpaintingSize ->
                change("inpaintingSize", event.value.sizePx.toString()) { it.copy(inpaintingSize = event.value) }
            TranslationSettingsEvent.ToggleSpeed -> _state.update { it.copy(speedExpanded = !it.speedExpanded) }
            TranslationSettingsEvent.ToggleRendering -> _state.update { it.copy(renderingExpanded = !it.renderingExpanded) }
            TranslationSettingsEvent.ToggleReading -> _state.update { it.copy(readingExpanded = !it.readingExpanded) }
            TranslationSettingsEvent.ToggleAdvanced -> _state.update { it.copy(isAdvancedExpanded = !it.isAdvancedExpanded) }
            is TranslationSettingsEvent.OpenSheet -> _state.update { it.copy(activeSheet = event.sheet) }
            TranslationSettingsEvent.CloseSheet -> _state.update { it.copy(activeSheet = null) }
        }
    }

    // PLAN_GEMINI.md 5.18 : SENDING optimiste tout de suite, puis APPLIED/WAITING_PC selon
    // awaitConfirmation() (relit pc/settings.json toutes les 5s, 60s au plus).
    private fun change(key: String, value: String, apply: (TranslationSettingsState) -> TranslationSettingsState) {
        if (_state.value.isReadOnly) return
        confirmJob?.cancel()
        _state.update { apply(it).copy(activeSheet = null, syncStatus = TranslationSyncStatus.Sending) }
        confirmJob = viewModelScope.launch {
            val cmdId = container.settingsSync.change(key, value)
            when (container.settingsSync.awaitConfirmation(cmdId)) {
                SyncBanner.APPLIED -> {
                    val overwrote = container.settingsSync.overwritten(cmdId)
                    if (overwrote.isNotEmpty()) {
                        // R8 : message qui reste affiche jusqu'au prochain changement.
                        _state.update { it.copy(syncStatus = TranslationSyncStatus.Overwrote, overwroteKeys = overwrote.joinToString(", ")) }
                    } else {
                        _state.update { it.copy(syncStatus = TranslationSyncStatus.Applied) }
                        delay(700)
                        _state.update { it.copy(syncStatus = TranslationSyncStatus.Synced) }
                    }
                }
                SyncBanner.WAITING_PC -> _state.update { it.copy(syncStatus = TranslationSyncStatus.WaitingPc) }
                // R12 : refusee par le PC -> on reaffiche ses valeurs (la commande n'est plus en attente)
                // et le motif du refus, jusqu'au prochain changement.
                SyncBanner.REJECTED -> {
                    load()
                    val reason = ErrorCatalog.settingsRejectLabel(container.settingsSync.rejectCode(cmdId))
                    _state.update { it.copy(syncStatus = TranslationSyncStatus.Rejected, rejectReason = reason) }
                }
                else -> Unit
            }
        }
    }
}

// Codes ISO a 2 lettres (requete telephone) -> codes TMM-Win a 3 lettres, comme map_language du PC.
private val ISO_TO_TMM_LANG = mapOf("FR" to "FRA", "EN" to "ENG", "ES" to "SPA", "DE" to "DEU", "JA" to "JPN", "JP" to "JPN", "KO" to "KOR", "ZH" to "CHS")

internal fun normalizeLang(code: String): String {
    if (code.equals("auto", ignoreCase = true)) return "auto"
    val upper = code.trim().uppercase()
    return ISO_TO_TMM_LANG[upper] ?: upper
}
