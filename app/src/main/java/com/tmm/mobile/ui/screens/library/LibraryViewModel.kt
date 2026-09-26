package com.tmm.mobile.ui.screens.library

import androidx.lifecycle.ViewModel
import com.tmm.mobile.ui.mock.MockupData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel de l'ecran Bibliotheque : toutes les actions de Library.dc.html sont
 * synchrones (pas de delay/setTimeout cote logique, seul le toast s'auto-efface via TmmToast).
 */
class LibraryViewModel : ViewModel() {
    private val _state = MutableStateFlow(MockupData.library)
    val state: StateFlow<LibraryState> = _state

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.QueryChanged -> _state.update { it.copy(query = event.value) }
            LibraryEvent.TogglePicking -> _state.update { it.copy(picking = !it.picking, selected = emptySet()) }
            is LibraryEvent.SelectCategory -> _state.update { it.copy(category = event.name) }
            is LibraryEvent.SelectView -> _state.update { it.copy(view = event.view) }
            is LibraryEvent.ToggleSelected -> _state.update {
                it.copy(selected = it.selected.toMutableSet().apply {
                    if (!add(event.title)) remove(event.title)
                })
            }
            LibraryEvent.ToggleSelectAll -> _state.update {
                val visibleTitles = it.visibleSeries.map { s -> s.title }
                it.copy(selected = if (it.allVisibleSelected) emptySet() else visibleTitles.toSet())
            }
            LibraryEvent.SendToPc -> showToast { LibraryToast.Sent(it.selectionToSend) }
            LibraryEvent.FetchToMihon -> showToast { LibraryToast.Received(it.selectionToGet) }
            LibraryEvent.VerifyPending -> showToast { LibraryToast.Verified(it.selectionToVerify) }
            LibraryEvent.RequestRetranslate -> _state.update {
                if (it.selectionToVerify > 0) it.copy(confirmRetranslate = true) else it
            }
            LibraryEvent.ConfirmRetranslate -> showToast { LibraryToast.Retranslated(it.selectionToVerify) }
            LibraryEvent.DismissConfirm -> _state.update { it.copy(confirmRetranslate = false) }
            LibraryEvent.DismissToast -> _state.update { it.copy(toast = null) }
        }
    }

    private inline fun showToast(toast: (LibraryState) -> LibraryToast) {
        _state.update {
            it.copy(toast = toast(it), confirmRetranslate = false, picking = false, selected = emptySet())
        }
    }
}
