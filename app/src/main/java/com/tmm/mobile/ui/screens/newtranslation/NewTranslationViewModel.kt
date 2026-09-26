package com.tmm.mobile.ui.screens.newtranslation

import androidx.lifecycle.ViewModel
import com.tmm.mobile.ui.mock.MockupData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel synchrone / testable de la feuille Nouvelle Traduction (E4.7, NewTranslation.dc.html).
 */
class NewTranslationViewModel(initialState: NewTranslationState = MockupData.newTranslation) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<NewTranslationState> = _state

    fun onEvent(event: NewTranslationEvent) {
        when (event) {
            is NewTranslationEvent.UpdateQuery -> _state.update {
                it.copy(searchQuery = event.query)
            }
            is NewTranslationEvent.SelectSeries -> _state.update {
                it.copy(
                    selectedSeries = event.series,
                    selectedChapters = event.series.chapters.toSet(),
                    phase = NewTranslationPhase.Idle,
                )
            }
            NewTranslationEvent.BackToSeries -> _state.update {
                it.copy(
                    selectedSeries = null,
                    selectedChapters = emptySet(),
                    phase = NewTranslationPhase.Idle,
                )
            }
            is NewTranslationEvent.ToggleChapter -> _state.update { current ->
                val next = if (current.selectedChapters.contains(event.chapter)) {
                    current.selectedChapters - event.chapter
                } else {
                    current.selectedChapters + event.chapter
                }
                current.copy(selectedChapters = next)
            }
            NewTranslationEvent.ToggleAllChapters -> _state.update { current ->
                val all = current.selectedSeries?.chapters.orEmpty()
                val next = if (current.isAllChaptersSelected) emptySet() else all.toSet()
                current.copy(selectedChapters = next)
            }
            NewTranslationEvent.ToggleTranslateOnArrival -> _state.update {
                it.copy(translateOnArrival = !it.translateOnArrival)
            }
            NewTranslationEvent.Send -> _state.update {
                it.copy(phase = NewTranslationPhase.Done)
            }
        }
    }
}
