package com.tmm.mobile.ui.screens.series

import androidx.lifecycle.ViewModel
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockupData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel de l'ecran Series : toutes les actions de Series.dc.html sont
 * synchrones (pas de delay/setTimeout cote logique).
 */
class SeriesViewModel : ViewModel() {
    private val _state = MutableStateFlow(MockupData.seriesDetail)
    val state: StateFlow<SeriesState> = _state

    fun onEvent(event: SeriesEvent) {
        when (event) {
            is SeriesEvent.ToggleChapter -> _state.update {
                val chapter = it.chapters.firstOrNull { c -> c.number == event.number }
                if (chapter == null || chapter.locked) {
                    it
                } else {
                    it.copy(selected = it.selected.toMutableSet().apply {
                        if (!add(event.number)) remove(event.number)
                    })
                }
            }
            SeriesEvent.SelectTodo -> _state.update {
                val todo = it.chapters.filter { c -> c.kind == StatusTone.Send || c.kind == StatusTone.Get }.map { c -> c.number }
                it.copy(selected = todo.toSet())
            }
            SeriesEvent.ClearSelection -> _state.update { it.copy(selected = emptySet()) }
            SeriesEvent.DismissGap -> _state.update { it.copy(showGap = false) }
            is SeriesEvent.ToggleStats -> _state.update {
                it.copy(expandedStats = it.expandedStats.toMutableSet().apply {
                    if (!add(event.number)) remove(event.number)
                })
            }
            SeriesEvent.SendToPc -> showToast { SeriesToast.Sent(it.selectionToSend) }
            SeriesEvent.FetchToMihon -> showToast { SeriesToast.Received(it.selectionToGet) }
            SeriesEvent.VerifyPending -> showToast { SeriesToast.Verified(it.selectionToVerify) }
            SeriesEvent.RequestRetranslateSelection -> _state.update {
                if (it.selectionToVerify > 0) it.copy(sheet = SeriesSheet.Confirm(it.selectionToVerify)) else it
            }
            SeriesEvent.OpenSeriesSheet -> _state.update { it.copy(sheet = SeriesSheet.SeriesActions) }
            SeriesEvent.CloseSheet -> _state.update { it.copy(sheet = SeriesSheet.None) }
            SeriesEvent.SeriesSendMissing -> showToast { SeriesToast.Sent(it.chapters.count { c -> c.kind == StatusTone.Send }) }
            SeriesEvent.SeriesFetchMissing -> showToast { SeriesToast.Received(it.chapters.count { c -> c.kind == StatusTone.Get }) }
            SeriesEvent.SeriesVerifyAll -> showToast { SeriesToast.Verified(it.frCount) }
            SeriesEvent.SeriesRequestRetranslateAll -> _state.update { it.copy(sheet = SeriesSheet.Confirm(it.frCount)) }
            SeriesEvent.ConfirmRetranslate -> _state.update {
                val count = (it.sheet as? SeriesSheet.Confirm)?.chapterCount ?: it.selectionToVerify
                it.copy(toast = SeriesToast.Retranslated(count), sheet = SeriesSheet.None, selected = emptySet())
            }
            SeriesEvent.DismissToast -> _state.update { it.copy(toast = null) }
        }
    }

    private inline fun showToast(toast: (SeriesState) -> SeriesToast) {
        _state.update { it.copy(toast = toast(it), sheet = SeriesSheet.None, selected = emptySet()) }
    }
}
