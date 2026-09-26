package com.tmm.mobile.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var toastJob: Job? = null

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.ChangeGoogleAccount -> showToast(SettingsToast.ChooseAccount)
            SettingsEvent.CycleDriveRetention -> _state.update { it.copy(driveRetention = it.driveRetention.next()) }
            SettingsEvent.ChangeMihonFolder -> showToast(SettingsToast.FolderPicker)
            is SettingsEvent.SetWifiOnly -> _state.update { it.copy(wifiOnly = event.enabled) }
            SettingsEvent.ToggleWifiOnly -> _state.update { it.copy(wifiOnly = !it.wifiOnly) }
            SettingsEvent.ExportLogs -> showToast(SettingsToast.LogsReady)
            SettingsEvent.DismissToast -> _state.update { it.copy(toast = null) }
        }
    }

    private fun showToast(toast: SettingsToast) {
        toastJob?.cancel()
        _state.update { it.copy(toast = toast) }
        toastJob = viewModelScope.launch {
            delay(2600)
            _state.update { if (it.toast == toast) it.copy(toast = null) else it }
        }
    }
}
