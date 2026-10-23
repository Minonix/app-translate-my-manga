package com.tmm.mobile.ui.screens.library

import com.tmm.mobile.core.matchesQuery
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockSeries
import com.tmm.mobile.ui.screens.series.ActionCounts
import com.tmm.mobile.ui.screens.series.DeleteTarget
import com.tmm.mobile.ui.screens.series.SeriesSheet

/** Filtres de la Bibliotheque, conserves pendant la selection. */
enum class LibraryViewFilter { All, Todo, Followed, Downloaded, OnPc, Absent }

/**
 * Tri de la feuille "Trier et filtrer" (hors maquette), en memoire seulement. Par defaut "Ajoutees
 * recemment" (dateAdded Mihon) : tri stable, donc les donnees de demo (dateAdded = 0) gardent l'ordre
 * de la maquette.
 */
enum class LibrarySort { RecentlyAdded, Title, ToTranslateFirst }

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu (regle 4.10). */
sealed interface LibraryToast {
    data class Installing(val chapterCount: Int) : LibraryToast
    data class Verified(val chapterCount: Int) : LibraryToast
    data class Retranslated(val chapterCount: Int) : LibraryToast
    data class Copied(val chapterCount: Int) : LibraryToast
    data class Deleted(val chapterCount: Int, val failed: Int = 0, val pcUnavailable: Boolean = false) : LibraryToast
    data class Followed(val seriesCount: Int, val chapterCount: Int) : LibraryToast
}

/**
 * Comptes de la feuille « Que faire ? », calcules depuis Room a son ouverture avec les predicats de
 * la fiche serie (canTranslate, canReprocess...) : ce qui est affiche est ce qui partira vraiment.
 */
data class LibraryActions(
    val counts: ActionCounts = ActionCounts(),
    val deletable: Int = 0,
    val deleteSkipped: Int = 0,
    /** Series selectionnees pas encore suivies (« Suivre ces series »). */
    val toFollow: Int = 0,
)

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
    /** Feuille « Que faire ? » de la selection multi-series (ecran 4 de la maquette v2). */
    val moreOpen: Boolean = false,
    /** Comptes de cette feuille ; null tant qu'ils se calculent (la feuille attend). */
    val actions: LibraryActions? = null,
    /** Feuille « Supprimer » ouverte depuis « Que faire ? ». */
    val delete: SeriesSheet.Delete? = null,
    /** Feuille Trier et filtrer : categories au-dela des 4 premieres depliees. */
    val categoriesExpanded: Boolean = false,
    /** Date de la sauvegarde Mihon d'ou viennent les categories (note de la feuille), null si aucune. */
    val backupModifiedAt: Long? = null,
    val toast: LibraryToast? = null,
    val details: Map<String, LibrarySeriesDetails> = emptyMap(),
) {
    /** Filtre categorie + recherche toujours, filtre "Afficher", puis tri. */
    val visibleSeries: List<MockSeries>
        get() = allSeries.filter { s ->
            (category == "Tous" || category in (details[s.seriesKey]?.categories ?: setOf(s.category))) &&
                (
                    view == LibraryViewFilter.All ||
                        (view == LibraryViewFilter.Followed && details[s.seriesKey]?.followed == true) ||
                        (view == LibraryViewFilter.Todo && (s.toSend > 0 || s.tone == StatusTone.Busy || (details[s.seriesKey]?.busy ?: 0) > 0)) ||
                        (view == LibraryViewFilter.Downloaded && s.onPhone > 0) ||
                        (view == LibraryViewFilter.OnPc && s.onPc > 0) ||
                        (view == LibraryViewFilter.Absent && s.tone == StatusTone.Absent)
                    ) &&
                (query.isBlank() || matchesQuery(s.title, query))
        }.sortedWith(sort.comparator)

    private val selectedSeries: List<MockSeries> get() = allSeries.filter { it.seriesKey in selected }

    val selectionCount: Int get() = selectedSeries.size
    val selectionLabel: String get() = selectedSeries.joinToString(", ") { it.title }
    val hasSelection: Boolean get() = picking && selectedSeries.isNotEmpty()

    val allVisibleSelected: Boolean get() = visibleSeries.isNotEmpty() && visibleSeries.all { it.seriesKey in selected }
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
    data class ToggleSelected(val seriesKey: String) : LibraryEvent
    data object ToggleSelectAll : LibraryEvent
    data object ExpandCategories : LibraryEvent
    data object OpenMore : LibraryEvent
    data object CloseMore : LibraryEvent
    data object Install : LibraryEvent
    data object Verify : LibraryEvent
    data object Retranslate : LibraryEvent
    data object CopyToPc : LibraryEvent
    data object Follow : LibraryEvent
    data object RequestDelete : LibraryEvent
    data class ToggleDeleteTarget(val target: DeleteTarget) : LibraryEvent
    data object ConfirmDelete : LibraryEvent
    data object CloseDelete : LibraryEvent
    data object DismissToast : LibraryEvent
}

/** Priorite de la maquette : echec, activite, installation, traduction, absence, a jour. */
internal fun libraryPillTone(series: MockSeries, details: LibrarySeriesDetails?): com.tmm.mobile.ui.components.PillTone {
    return when {
        (details?.failures ?: 0) > 0 -> com.tmm.mobile.ui.components.PillTone.Fail
        (details?.busy ?: 0) > 0 || series.tone == StatusTone.Busy -> com.tmm.mobile.ui.components.PillTone.Busy
        (details?.toInstall ?: series.toGet) > 0 -> com.tmm.mobile.ui.components.PillTone.Get
        series.toSend > 0 -> com.tmm.mobile.ui.components.PillTone.Todo
        series.onPhone == 0 && series.tone == StatusTone.Absent -> com.tmm.mobile.ui.components.PillTone.Absent
        else -> com.tmm.mobile.ui.components.PillTone.Ok
    }
}
