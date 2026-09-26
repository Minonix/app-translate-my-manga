package com.tmm.mobile.ui.screens.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OfflineViewModel(initial: OfflineState = OfflineState()) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<OfflineState> = _state.asStateFlow()

    private var reconnectJob: Job? = null

    fun onEvent(event: OfflineEvent) {
        when (event) {
            OfflineEvent.Reconnect -> reconnect()
        }
    }

    private fun reconnect() {
        if (_state.value.isReconnecting) return
        _state.update { it.copy(isReconnecting = true) }
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(1500)
            _state.update { it.copy(isReconnecting = false) }
        }
    }
}
