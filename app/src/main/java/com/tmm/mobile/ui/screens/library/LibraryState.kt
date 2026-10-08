package com.tmm.mobile.ui.screens.library

import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockSeries

/** Filtre "Afficher" (Library.dc.html, radiogroup) ; ignore quand `picking` est actif. */
enum class LibraryViewFilter { All, Todo, Downloaded, OnPc, Absent }

/**
 * Tri de la feuille "Trier et filtrer" (hors maquette), en memoire seulement. Par defaut "Ajoutees
 * recemment" (dateAdded Mihon) : tri stable, donc les donnees de demo (dateAdded = 0) gardent l'ordre
 * de la maquette.
 */
enum class LibrarySort { RecentlyAdded, Title, ToTranslateFirst }

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu (regle 4.10). */
sealed interface LibraryToast {
    data class Sent(val chapterCount: Int) : LibraryToast
    data class Received(val chapterCount: Int) : LibraryToast
    data class Verified(val chapterCount: Int) : LibraryToast
    data class Translated(val chapterCount: Int) : LibraryToast
    data class Retranslated(val chapterCount: Int) : LibraryToast
}

data class LibraryState(
    val query: String = "",
    val picking: Boolean = false,
    val selected: Set<String> = emptySet(),
    val category: String = "Tous",
    val categories: List<Pair<String, Int>>,
    val view: LibraryViewFilter = LibraryViewFilter.All,
    val sort: LibrarySort = LibrarySort.RecentlyAdded,
    val filtersOpen: Boolean = false,
    val allSeries: List<MockSeries>,
    val confirmRetranslate: Boolean = false,
    val toast: LibraryToast? = null,
) {
    /** Filtre categorie + recherche toujours, filtre "Afficher" seulement hors mode selection, puis tri. */
    val visibleSeries: List<MockSeries>
        get() = allSeries.filter { s ->
            (category == "Tous" || s.category == category) &&
                (
                    picking || view == LibraryViewFilter.All ||
                        (view == LibraryViewFilter.Todo && (s.tone == StatusTone.Send || s.tone == StatusTone.Busy)) ||
                        (view == LibraryViewFilter.Downloaded && s.onPhone > 0) ||
                        (view == LibraryViewFilter.OnPc && s.onPc > 0) ||
                        (view == LibraryViewFilter.Absent && s.tone == StatusTone.Absent)
                    ) &&
                (query.isBlank() || s.title.contains(query, ignoreCase = true))
        }.sortedWith(sort.comparator)

    private val selectedSeries: List<MockSeries> get() = allSeries.filter { it.title in selected }

    val selectionCount: Int get() = selectedSeries.size
    val selectionLabel: String get() = selectedSeries.joinToString(", ") { it.title }
    val hasSelection: Boolean get() = picking && selectedSeries.isNotEmpty()

    /** send = chapitres a envoyer, get = a recuperer, verify = a jour (frChapters, meme predicat que VerifyPending). */
    val selectionToSend: Int get() = selectedSeries.sumOf { it.toSend }
    val selectionToGet: Int get() = selectedSeries.sumOf { it.toGet }
    val selectionToVerify: Int get() = selectedSeries.sumOf { it.frChapters }

    /** Bouton "Traduire"/"Retraduire" : "Retraduire" des qu'une cible a deja une traduction. */
    val selectionToTranslate: Int get() = selectedSeries.sumOf { it.toTranslate }
    val selectionRetranslates: Boolean get() = selectedSeries.any { it.toRetranslate > 0 }

    val allVisibleSelected: Boolean get() = visibleSeries.isNotEmpty() && visibleSeries.all { it.title in selected }
}

private val byTitle: Comparator<MockSeries> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }

private val LibrarySort.comparator: Comparator<MockSeries>
    get() = when (this) {
        LibrarySort.RecentlyAdded -> compareByDescending { it.dateAdded }
        LibrarySort.Title -> byTitle
        // "A traduire d'abord" : le plus de chapitres a envoyer en tete, puis par titre.
        LibrarySort.ToTranslateFirst -> compareByDescending<MockSeries> { it.toSend }.then(byTitle)
    }

sealed interface LibraryEvent {
    data class QueryChanged(val value: String) : LibraryEvent
    data object TogglePicking : LibraryEvent
    data class SelectCategory(val name: String) : LibraryEvent
    data class SelectView(val view: LibraryViewFilter) : LibraryEvent
    data class SelectSort(val sort: LibrarySort) : LibraryEvent
    data object OpenFilters : LibraryEvent
    data object CloseFilters : LibraryEvent
    data class ToggleSelected(val title: String) : LibraryEvent
    data object ToggleSelectAll : LibraryEvent
    data object SendToPc : LibraryEvent
    data object FetchToMihon : LibraryEvent
    data object VerifyPending : LibraryEvent
    data object RequestRetranslate : LibraryEvent
    data object ConfirmRetranslate : LibraryEvent
    data object DismissConfirm : LibraryEvent
    data object DismissToast : LibraryEvent
}
