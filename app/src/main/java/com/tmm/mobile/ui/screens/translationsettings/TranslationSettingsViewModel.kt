package com.tmm.mobile.ui.screens.translationsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranslationSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(TranslationSettingsState())
    val state: StateFlow<TranslationSettingsState> = _state.asStateFlow()

    private var syncJob: Job? = null

    fun onEvent(event: TranslationSettingsEvent) {
        when (event) {
            is TranslationSettingsEvent.SelectSourceLanguage -> triggerSync { it.copy(sourceLanguage = event.value) }
            is TranslationSettingsEvent.SelectTargetLanguage -> triggerSync { it.copy(targetLanguage = event.value) }
            is TranslationSettingsEvent.SelectTranslationProvider -> triggerSync { it.copy(translationProvider = event.value) }
            is TranslationSettingsEvent.SetAutoFailover -> triggerSync { it.copy(autoFailoverToLocalLlm = event.value) }
            is TranslationSettingsEvent.SetAutoVerify -> triggerSync { it.copy(autoVerifyAfterTranslate = event.value) }
            is TranslationSettingsEvent.SetChapterConcurrency -> triggerSync { it.copy(chapterConcurrency = event.value) }
            is TranslationSettingsEvent.SetConcurrency -> triggerSync { it.copy(concurrency = event.value) }
            is TranslationSettingsEvent.SetPileSize -> triggerSync { it.copy(pileSize = event.value) }
            is TranslationSettingsEvent.SetUseGpu -> triggerSync { it.copy(useGpu = event.value) }
            is TranslationSettingsEvent.SelectDetector -> triggerSync { it.copy(detector = event.value) }
            is TranslationSettingsEvent.SelectPreferredOcr -> triggerSync { it.copy(preferredOcr = event.value) }
            is TranslationSettingsEvent.SelectInpainter -> triggerSync { it.copy(inpainter = event.value) }
            is TranslationSettingsEvent.SelectInpaintingSize -> triggerSync { it.copy(inpaintingSize = event.value) }
            is TranslationSettingsEvent.SelectFont -> triggerSync { it.copy(font = event.value) }
            TranslationSettingsEvent.ToggleAdvanced -> _state.update { it.copy(isAdvancedExpanded = !it.isAdvancedExpanded) }
            is TranslationSettingsEvent.OpenSheet -> _state.update { it.copy(activeSheet = event.sheet) }
            TranslationSettingsEvent.CloseSheet -> _state.update { it.copy(activeSheet = null) }
        }
    }

    private fun triggerSync(updater: (TranslationSettingsState) -> TranslationSettingsState) {
        syncJob?.cancel()
        _state.update { updater(it).copy(activeSheet = null, syncStatus = TranslationSyncStatus.Sending) }
        syncJob = viewModelScope.launch {
            delay(1100)
            _state.update { it.copy(syncStatus = TranslationSyncStatus.Applied) }
            delay(700)
            _state.update { it.copy(syncStatus = TranslationSyncStatus.Synced) }
        }
    }
}
