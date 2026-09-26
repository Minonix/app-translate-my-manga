package com.tmm.mobile.ui.screens.series

import com.tmm.mobile.ui.components.StatusTone
import androidx.compose.ui.graphics.Color

/** Statistiques de traduction d'un chapitre (Series.dc.html, `st` : pages, bulles, SFX, verification). */
data class SeriesChapterStats(
    val pagesTotal: Int,
    val pagesTranslated: Int,
    val bubblesTranslated: Int,
    val bubblesUntranslated: Int,
    val sfxKept: Int,
    val verifiedDate: String?,
)

/**
 * Ligne de la liste "Chapitres" (Series.dc.html) : `kind` pilote le verrouillage
 * (busy/gap non selectionnables) et le comptage des actions (send/get/ok).
 */
data class SeriesChapter(
    val number: Int,
    val kind: StatusTone,
    val statusLabel: String,
    val statusColor: Color,
    val onPhone: Boolean,
    val onPc: Boolean,
    val onFr: Boolean,
    val translationProgress: Float? = null,
    val stats: SeriesChapterStats? = null,
) {
    val locked: Boolean get() = kind == StatusTone.Busy || kind == StatusTone.Gap
}

/** Feuille du bas ouverte (Series.dc.html `sheet`) : `Confirm` porte le nombre de chapitres capture a l'ouverture. */
sealed interface SeriesSheet {
    data object None : SeriesSheet
    data object SeriesActions : SeriesSheet
    data class Confirm(val chapterCount: Int) : SeriesSheet
}

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu (regle 4.10). */
sealed interface SeriesToast {
    data class Sent(val chapterCount: Int) : SeriesToast
    data class Received(val chapterCount: Int) : SeriesToast
    data class Verified(val chapterCount: Int) : SeriesToast
    data class Retranslated(val chapterCount: Int) : SeriesToast
}

data class SeriesState(
    val title: String,
    val categoryLabel: String,
    val formatLabel: String,
    val coverColor: Color,
    val onPhoneCount: Int,
    val onPcCount: Int,
    val frCount: Int,
    val totalChapters: Int,
    val chapters: List<SeriesChapter>,
    val selected: Set<Int> = emptySet(),
    val expandedStats: Set<Int> = emptySet(),
    val showGap: Boolean = true,
    val sheet: SeriesSheet = SeriesSheet.None,
    val toast: SeriesToast? = null,
) {
    val gapChapter: SeriesChapter? get() = chapters.firstOrNull { it.kind == StatusTone.Gap }

    private val pickedChapters: List<SeriesChapter>
        get() = chapters.filter { it.number in selected && it.kind != StatusTone.Busy && it.kind != StatusTone.Gap }

    val selectionCount: Int get() = pickedChapters.size
    val hasSelection: Boolean get() = pickedChapters.isNotEmpty()
    val selectionToSend: Int get() = pickedChapters.count { it.kind == StatusTone.Send }
    val selectionToGet: Int get() = pickedChapters.count { it.kind == StatusTone.Get }
    val selectionToVerify: Int get() = pickedChapters.count { it.kind == StatusTone.Get || it.kind == StatusTone.Ok }
}

sealed interface SeriesEvent {
    data class ToggleChapter(val number: Int) : SeriesEvent
    data object SelectTodo : SeriesEvent
    data object ClearSelection : SeriesEvent
    data object DismissGap : SeriesEvent
    data class ToggleStats(val number: Int) : SeriesEvent
    data object SendToPc : SeriesEvent
    data object FetchToMihon : SeriesEvent
    data object VerifyPending : SeriesEvent
    data object RequestRetranslateSelection : SeriesEvent
    data object OpenSeriesSheet : SeriesEvent
    data object CloseSheet : SeriesEvent
    data object SeriesSendMissing : SeriesEvent
    data object SeriesFetchMissing : SeriesEvent
    data object SeriesVerifyAll : SeriesEvent
    data object SeriesRequestRetranslateAll : SeriesEvent
    data object ConfirmRetranslate : SeriesEvent
    data object DismissToast : SeriesEvent
}
