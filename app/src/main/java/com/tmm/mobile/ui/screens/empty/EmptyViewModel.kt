package com.tmm.mobile.ui.screens.empty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EmptyViewModel(initial: EmptyState = EmptyState()) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<EmptyState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun onEvent(event: EmptyEvent) {
        when (event) {
            EmptyEvent.ScanMihon -> scan()
        }
    }

    private fun scan() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(1300)
            _state.update { it.copy(isBusy = false, isDone = true) }
        }
    }
}
