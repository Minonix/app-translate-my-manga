package com.tmm.mobile.ui.screens.transfers

import androidx.lifecycle.ViewModel
import com.tmm.mobile.ui.mock.MockupData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel synchrone / testable de l'écran Transferts (E4.4, Sync.dc.html).
 */
class SyncViewModel : ViewModel() {
    private val _state = MutableStateFlow(MockupData.sync)
    val state: StateFlow<SyncState> = _state

    fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.ToggleAutoTranslate -> _state.update {
                it.copy(autoTranslate = !it.autoTranslate)
            }
            SyncEvent.SendUp -> _state.update {
                it.copy(
                    sendState = SendState.Done(it.upCount),
                    upCount = 0,
                    liveTransfers = it.liveTransfers.filterNot { item -> item.isUpload },
                )
            }
            SyncEvent.GetDown -> _state.update { current ->
                current.copy(
                    fetchState = FetchState.Done,
                    downCount = 0,
                    downSeries = current.downSeries.map { item -> item.copy(isDone = true) },
                    liveTransfers = current.liveTransfers.filter { item -> item.isUpload },
                )
            }
            SyncEvent.Rescan -> _state.update {
                it.copy(scanning = false, scannedAgo = "à l’instant")
            }
        }
    }
}
