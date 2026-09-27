package com.tmm.mobile.ui.screens.newtranslation

import androidx.compose.ui.graphics.Color
import com.tmm.mobile.ui.theme.TmmColors

/**
 * Un chapitre TO_SEND proposable a l'envoi. `mihonPath` est l'identifiant unique (celui de
 * DownloadedChapterEntity) utilise pour la selection et pour retrouver la cible de job reelle
 * au moment de l'envoi (NewTranslationViewModel, plan 7.7).
 */
data class NewTranslationChapter(
    val mihonPath: String,
    val number: Float,
    val pages: Int,
) {
    /** "52" ou "10.5" (pas de decimale inutile), comme Formatters.chapterRangeFr. */
    val numberLabel: String get() = if (number == number.toInt().toFloat()) number.toInt().toString() else number.toString()
}

data class NewTranslationSeries(
    val seriesKey: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val chapters: List<NewTranslationChapter>,
)

enum class NewTranslationPhase {
    Idle, Busy, Done
}

data class NewTranslationState(
    val searchQuery: String = "",
    val seriesList: List<NewTranslationSeries> = listOf(
        NewTranslationSeries(
            seriesKey = "mock-kagurabachi",
            title = "Kagurabachi",
            subtitle = "En cours · anglais",
            color = TmmColors.CCFD8C6,
            chapters = listOf(
                NewTranslationChapter("mock-kagurabachi|52", 52f, 21),
                NewTranslationChapter("mock-kagurabachi|53", 53f, 22),
                NewTranslationChapter("mock-kagurabachi|54", 54f, 23),
            ),
        ),
        NewTranslationSeries(
            seriesKey = "mock-sakamoto-days",
            title = "Sakamoto Days",
            subtitle = "À lire · anglais",
            color = TmmColors.CC9D3DE,
            chapters = listOf(
                NewTranslationChapter("mock-sakamoto-days|1", 1f, 19),
                NewTranslationChapter("mock-sakamoto-days|2", 2f, 20),
                NewTranslationChapter("mock-sakamoto-days|3", 3f, 21),
                NewTranslationChapter("mock-sakamoto-days|4", 4f, 22),
                NewTranslationChapter("mock-sakamoto-days|5", 5f, 23),
                NewTranslationChapter("mock-sakamoto-days|6", 6f, 24),
                NewTranslationChapter("mock-sakamoto-days|7", 7f, 18),
                NewTranslationChapter("mock-sakamoto-days|8", 8f, 19),
                NewTranslationChapter("mock-sakamoto-days|9", 9f, 20),
                NewTranslationChapter("mock-sakamoto-days|10", 10f, 21),
                NewTranslationChapter("mock-sakamoto-days|11", 11f, 22),
                NewTranslationChapter("mock-sakamoto-days|12", 12f, 23),
            ),
        ),
        NewTranslationSeries(
            seriesKey = "mock-blue-lock",
            title = "Blue Lock",
            subtitle = "En cours · anglais",
            color = TmmColors.CD8D2E4,
            chapters = listOf(NewTranslationChapter("mock-blue-lock|214", 214f, 22)),
        ),
        NewTranslationSeries(
            seriesKey = "mock-kaiju-no-8",
            title = "Kaiju No. 8",
            subtitle = "Webtoons · coréen",
            color = TmmColors.CE6DCC0,
            chapters = listOf(
                NewTranslationChapter("mock-kaiju-no-8|118", 118f, 24),
                NewTranslationChapter("mock-kaiju-no-8|119", 119f, 18),
            ),
        ),
    ),
    val selectedSeriesKey: String? = null,
    val selectedChapters: Set<String> = emptySet(),
    val translateOnArrival: Boolean = true,
    val phase: NewTranslationPhase = NewTranslationPhase.Idle,
) {
    val selectedSeries: NewTranslationSeries? get() = seriesList.firstOrNull { it.seriesKey == selectedSeriesKey }
    val isStep2: Boolean get() = selectedSeries != null
    val selectedCount: Int get() = selectedChapters.size

    val filteredSeries: List<NewTranslationSeries>
        get() {
            val q = searchQuery.trim().lowercase()
            return if (q.isEmpty()) seriesList else seriesList.filter { it.title.lowercase().contains(q) }
        }

    val isAllChaptersSelected: Boolean
        get() {
            val chapters = selectedSeries?.chapters ?: return false
            return chapters.isNotEmpty() && chapters.all { it.mihonPath in selectedChapters }
        }
}

sealed interface NewTranslationEvent {
    data class UpdateQuery(val query: String) : NewTranslationEvent
    data class SelectSeries(val seriesKey: String) : NewTranslationEvent
    data object BackToSeries : NewTranslationEvent
    data class ToggleChapter(val mihonPath: String) : NewTranslationEvent
    data object ToggleAllChapters : NewTranslationEvent
    data object ToggleTranslateOnArrival : NewTranslationEvent
    data object Send : NewTranslationEvent
}
