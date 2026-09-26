package com.tmm.mobile.ui.screens.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PermissionViewModel(initial: PermissionState = PermissionState()) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    private var verifyJob: Job? = null

    fun onEvent(event: PermissionEvent) {
        when (event) {
            PermissionEvent.PickFolder -> pickFolder()
        }
    }

    private fun pickFolder() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            delay(1200)
            _state.update { it.copy(isBusy = false, isFixed = true) }
        }
    }
}
