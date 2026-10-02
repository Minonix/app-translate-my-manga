package com.tmm.mobile.ui.screens.library

import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockSeries

/** Filtre "Afficher" (Library.dc.html, radiogroup) ; ignore quand `picking` est actif. */
enum class LibraryViewFilter { All, Todo, Downloaded, OnPc, Absent }

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu (regle 4.10). */
sealed interface LibraryToast {
    data class Sent(val chapterCount: Int) : LibraryToast
    data class Received(val chapterCount: Int) : LibraryToast
    data class Verified(val chapterCount: Int) : LibraryToast
    data class Retranslated(val chapterCount: Int) : LibraryToast
}

data class LibraryState(
    val query: String = "",
    val picking: Boolean = false,
    val selected: Set<String> = emptySet(),
    val category: String = "Tous",
    val categories: List<Pair<String, Int>>,
    val view: LibraryViewFilter = LibraryViewFilter.All,
    val allSeries: List<MockSeries>,
    val confirmRetranslate: Boolean = false,
    val toast: LibraryToast? = null,
) {
    /** Filtre categorie + recherche toujours, filtre "Afficher" seulement hors mode selection. */
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
        }

    private val selectedSeries: List<MockSeries> get() = allSeries.filter { it.title in selected }

    val selectionCount: Int get() = selectedSeries.size
    val selectionLabel: String get() = selectedSeries.joinToString(", ") { it.title }
    val hasSelection: Boolean get() = picking && selectedSeries.isNotEmpty()

    /** send = chapitres a envoyer, get = a recuperer, fr = deja traduits (verifiables/retraduisibles). */
    val selectionToSend: Int get() = selectedSeries.sumOf { it.toSend }
    val selectionToGet: Int get() = selectedSeries.sumOf { it.toGet }
    val selectionToVerify: Int get() = selectedSeries.sumOf { it.frChapters }

    val allVisibleSelected: Boolean get() = visibleSeries.isNotEmpty() && visibleSeries.all { it.title in selected }
}

sealed interface LibraryEvent {
    data class QueryChanged(val value: String) : LibraryEvent
    data object TogglePicking : LibraryEvent
    data class SelectCategory(val name: String) : LibraryEvent
    data class SelectView(val view: LibraryViewFilter) : LibraryEvent
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
