package com.tmm.mobile.ui.screens.newtranslation

import androidx.compose.ui.graphics.Color
import com.tmm.mobile.ui.theme.TmmColors

data class NewTranslationSeries(
    val title: String,
    val subtitle: String,
    val color: Color,
    val chapters: List<Int>,
)

enum class NewTranslationPhase {
    Idle, Busy, Done
}

data class NewTranslationState(
    val searchQuery: String = "",
    val seriesList: List<NewTranslationSeries> = listOf(
        NewTranslationSeries("Kagurabachi", "En cours · anglais", TmmColors.CCFD8C6, listOf(52, 53, 54)),
        NewTranslationSeries("Sakamoto Days", "À lire · anglais", TmmColors.CC9D3DE, (1..12).toList()),
        NewTranslationSeries("Blue Lock", "En cours · anglais", TmmColors.CD8D2E4, listOf(214)),
        NewTranslationSeries("Kaiju No. 8", "Webtoons · coréen", TmmColors.CE6DCC0, listOf(118, 119)),
    ),
    val selectedSeries: NewTranslationSeries? = null,
    val selectedChapters: Set<Int> = emptySet(),
    val translateOnArrival: Boolean = true,
    val phase: NewTranslationPhase = NewTranslationPhase.Idle,
) {
    val isStep2: Boolean get() = selectedSeries != null
    val selectedCount: Int get() = selectedChapters.size

    val filteredSeries: List<NewTranslationSeries>
        get() {
            val q = searchQuery.trim().lowercase()
            return if (q.isEmpty()) seriesList else seriesList.filter { it.title.lowercase().contains(q) }
        }

    val isAllChaptersSelected: Boolean
        get() = selectedSeries != null && selectedSeries.chapters.isNotEmpty() && selectedChapters.size == selectedSeries.chapters.size
}

sealed interface NewTranslationEvent {
    data class UpdateQuery(val query: String) : NewTranslationEvent
    data class SelectSeries(val series: NewTranslationSeries) : NewTranslationEvent
    data object BackToSeries : NewTranslationEvent
    data class ToggleChapter(val chapter: Int) : NewTranslationEvent
    data object ToggleAllChapters : NewTranslationEvent
    data object ToggleTranslateOnArrival : NewTranslationEvent
    data object Send : NewTranslationEvent
}
