package com.tmm.mobile.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.ui.mock.MockupData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SendDurationMs = 1300L
private const val GetFillDelayMs = 30L
private const val GetDurationMs = 1500L

/**
 * ViewModel provisoire de l'ecran Accueil : logique d'interaction seedee sur
 * MockupData.main (Main.dc.html). Remplace par les vraies donnees en E10.
 */
class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(MockupData.main)
    val state: StateFlow<MainState> = _state

    fun onEvent(event: MainEvent) {
        when (event) {
            MainEvent.Refresh -> {}
            MainEvent.RefreshFinished -> _state.update { it.copy(lastCheckLabel = "à l'instant") }
            MainEvent.SendPending -> sendPending()
            MainEvent.FetchTranslated -> fetchTranslated()
            MainEvent.DismissToast -> _state.update { it.copy(toast = null) }
        }
    }

    private fun sendPending() {
        if (_state.value.sendState != BusyState.Idle) return
        _state.update { it.copy(sendState = BusyState.Busy) }
        viewModelScope.launch {
            delay(SendDurationMs)
            val sentCount = _state.value.toSendCount
            _state.update {
                it.copy(sendState = BusyState.Done, toSendCount = 0, toast = MainToast.Sent(sentCount))
            }
        }
    }

    private fun fetchTranslated() {
        if (_state.value.getState != BusyState.Idle) return
        _state.update { it.copy(getState = BusyState.Busy, getFraction = 0f) }
        viewModelScope.launch {
            delay(GetFillDelayMs)
            _state.update { it.copy(getFraction = 1f) }
            delay(GetDurationMs)
            val gotCount = _state.value.toGetCount
            _state.update {
                it.copy(getState = BusyState.Done, toGetCount = 0, toast = MainToast.Received(gotCount))
            }
        }
    }
}
