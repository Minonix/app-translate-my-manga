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

    /**
     * Feuille "Supprimer" de la selection : chapitres traites, chapitres ignores (job actif) et les
     * trois cases. Le telechargement Mihon (irreversible) et le PC sont decoches par defaut.
     */
    data class Delete(
        val chapterCount: Int,
        val skippedCount: Int,
        val localFr: Boolean = true,
        val mihon: Boolean = false,
        val pc: Boolean = false,
    ) : SeriesSheet {
        val anyChecked: Boolean get() = localFr || mihon || pc
    }
}

/** Case de la feuille "Supprimer". */
enum class DeleteTarget { LocalFr, Mihon, Pc }

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu (regle 4.10). */
sealed interface SeriesToast {
    data class Sent(val chapterCount: Int) : SeriesToast
    data class Received(val chapterCount: Int) : SeriesToast
    data class Verified(val chapterCount: Int) : SeriesToast
    data class Translated(val chapterCount: Int) : SeriesToast
    data class Retranslated(val chapterCount: Int) : SeriesToast
    // failed : fichiers SAF non supprimes ; pcUnavailable : commande PC non mise en file.
    data class Deleted(val chapterCount: Int, val failed: Int = 0, val pcUnavailable: Boolean = false) : SeriesToast
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
    // Bouton "Traduire"/"Retraduire" de la selection : calcule par le ViewModel depuis les vraies
    // lignes (planTranslation), les tons de SeriesChapter ne suffisant pas (original sur le PC, job actif).
    val selectionToTranslate: Int = 0,
    val selectionRetranslates: Boolean = false,
    // Bouton "Supprimer" : chapitres selectionnes sans job actif, et ceux ignores (ChapterDeleter).
    val selectionToDelete: Int = 0,
    val selectionDeleteSkipped: Int = 0,
) {
    val gapChapter: SeriesChapter? get() = chapters.firstOrNull { it.kind == StatusTone.Gap }

    private val pickedChapters: List<SeriesChapter>
        get() = chapters.filter { it.number in selected && it.kind != StatusTone.Busy && it.kind != StatusTone.Gap }

    val selectionCount: Int get() = pickedChapters.size
    val hasSelection: Boolean get() = pickedChapters.isNotEmpty()
    val selectionToSend: Int get() = pickedChapters.count { it.kind == StatusTone.Send }
    val selectionToGet: Int get() = pickedChapters.count { it.kind == StatusTone.Get }
    // Meme predicat que VerifyPending (UP_TO_DATE, seul statut au ton Ok).
    val selectionToVerify: Int get() = pickedChapters.count { it.kind == StatusTone.Ok }
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
    data object RequestDelete : SeriesEvent
    data class ToggleDeleteTarget(val target: DeleteTarget) : SeriesEvent
    data object ConfirmDelete : SeriesEvent
    data object DismissToast : SeriesEvent
}
