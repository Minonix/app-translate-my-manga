package com.tmm.mobile.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ChapterJoinIndex
import com.tmm.mobile.data.status.ChapterJoinInputs
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.buildChapterJoinRows
import com.tmm.mobile.data.status.planTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel reel de l'ecran Bibliotheque (E10, plan 7.2) : la liste agregee (SeriesSummary par
 * serie) vient de LibraryCache, partage entre les ecrans et persiste sur disque. Les actions (envoyer/recuperer/
 * verifier/retraduire) passent par JobRepository, comme les autres ecrans de cette milestone.
 */
class LibraryViewModel(
    private val container: AppContainer,
    initialCategoryOrder: Long? = null,
    initialPicking: Boolean = false,
) : ViewModel() {

    private data class UiOnly(
        val query: String = "",
        val picking: Boolean = false,
        val selected: Set<String> = emptySet(),
        val category: String = "Tous",
        val view: LibraryViewFilter = LibraryViewFilter.All,
        val sort: LibrarySort = LibrarySort.RecentlyAdded,
        val filtersOpen: Boolean = false,
        val confirmRetranslate: Boolean = false,
        val toast: LibraryToast? = null,
    )

    private val ui = MutableStateFlow(UiOnly(picking = initialPicking))

    // Liste partagee et persistee (LibraryCache) : affichee tout de suite, recalculee en fond.
    private val dataState: StateFlow<LibrarySnapshot> = container.libraryCache.data

    val state: StateFlow<LibraryState> = combine(ui, dataState) { u, d ->
        LibraryState(
            query = u.query,
            picking = u.picking,
            selected = u.selected,
            category = u.category,
            categories = d.categories,
            view = u.view,
            sort = u.sort,
            filtersOpen = u.filtersOpen,
            allSeries = d.series,
            confirmRetranslate = u.confirmRetranslate,
            toast = u.toast,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState(categories = LibrarySnapshot().categories, allSeries = emptyList()))

    init {
        if (initialCategoryOrder != null) {
            viewModelScope.launch {
                val name = container.db.categoryDao().observeAll().first().firstOrNull { it.order == initialCategoryOrder }?.name
                if (name != null) ui.update { it.copy(category = name) }
            }
        }
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.QueryChanged -> ui.update { it.copy(query = event.value) }
            LibraryEvent.TogglePicking -> ui.update { it.copy(picking = !it.picking, selected = emptySet()) }
            is LibraryEvent.SelectCategory -> ui.update { it.copy(category = event.name) }
            is LibraryEvent.SelectView -> ui.update { it.copy(view = event.view) }
            is LibraryEvent.SelectSort -> ui.update { it.copy(sort = event.sort) }
            LibraryEvent.OpenFilters -> ui.update { it.copy(filtersOpen = true) }
            LibraryEvent.CloseFilters -> ui.update { it.copy(filtersOpen = false) }
            is LibraryEvent.ToggleSelected -> ui.update {
                it.copy(selected = it.selected.toMutableSet().apply { if (!add(event.title)) remove(event.title) })
            }
            LibraryEvent.ToggleSelectAll -> ui.update {
                val visible = state.value.visibleSeries.map { s -> s.title }
                it.copy(selected = if (state.value.allVisibleSelected) emptySet() else visible.toSet())
            }
            LibraryEvent.SendToPc -> performSend()
            LibraryEvent.FetchToMihon -> performFetch()
            LibraryEvent.VerifyPending -> performVerify()
            // "Retraduire" ecrase une traduction : confirmation d'abord. "Traduire" agit directement.
            LibraryEvent.RequestRetranslate -> {
                val current = state.value
                when {
                    current.selectionToTranslate == 0 -> Unit
                    current.selectionRetranslates -> ui.update { it.copy(confirmRetranslate = true) }
                    else -> performTranslation()
                }
            }
            LibraryEvent.ConfirmRetranslate -> performTranslation()
            LibraryEvent.DismissConfirm -> ui.update { it.copy(confirmRetranslate = false) }
            LibraryEvent.DismissToast -> ui.update { it.copy(toast = null) }
        }
    }

    /**
     * Chapitres des series selectionnees, recalcules a la demande depuis Room : le cache de la
     * Bibliotheque ne garde que le resume par serie, et seules les series choisies sont relues.
     */
    private suspend fun selectedRows(): List<Pair<SeriesEntity, List<ChapterJoinRow>>> = withContext(Dispatchers.Default) {
        val db = container.db
        val keys = state.value.allSeries.filter { it.title in state.value.selected }.map { it.seriesKey }
        val series = keys.mapNotNull { db.seriesDao().get(it) }
        val index = ChapterJoinIndex(
            ChapterJoinInputs(
                chapters = series.flatMap { db.chapterDao().getForSeries(it.seriesKey) },
                downloaded = series.flatMap { db.downloadedChapterDao().observeForSeries(it.seriesKey).first() },
                jobs = db.jobDao().observeAll().first(),
                localFr = db.localFrChapterDao().observeAll().first(),
                pcIndex = db.pcIndexChapterDao().observeAll().first(),
            ),
        )
        series.map { it to buildChapterJoinRows(it.seriesKey, it.sourceName, it.title, index) }
    }

    private suspend fun collectTargets(predicate: (ChapterJoinRow) -> Boolean): List<JobTarget> =
        selectedRows().flatMap { (series, rows) ->
            rows.filter(predicate).map { row ->
                JobTarget(mihonPath = row.mihonPath, downloaded = row.downloaded, series = series, chapter = row.chapter)
            }
        }

    private fun performSend() = viewModelScope.launch {
        val targets = collectTargets { it.status == ChapterStatus.TO_SEND }
        container.jobRepository.createJobs(JobMode.TRANSLATE, targets)
        showToast(LibraryToast.Sent(targets.size))
    }

    private fun performFetch() = viewModelScope.launch {
        val jobIds = selectedRows().flatMap { (_, rows) ->
            rows.mapNotNull { row -> row.facts.latestJob?.takeIf { it.phoneState == "RESULT_READY" }?.jobId }
        }
        container.jobRepository.fetch(jobIds)
        showToast(LibraryToast.Received(jobIds.size))
    }

    private fun performVerify() = viewModelScope.launch {
        val targets = collectTargets { it.status == ChapterStatus.UP_TO_DATE }
        container.jobRepository.createJobs(JobMode.VERIFY, targets)
        showToast(LibraryToast.Verified(targets.size))
    }

    /** Bouton "Traduire"/"Retraduire" : cibles et libelle du toast via planTranslation, serie par serie. */
    private fun performTranslation() = viewModelScope.launch {
        var retranslate = false
        val targets = selectedRows().flatMap { (series, rows) ->
            val plan = planTranslation(rows)
            if (plan.retranslate) retranslate = true
            plan.targets.map { row -> JobTarget(mihonPath = row.mihonPath, downloaded = row.downloaded, series = series, chapter = row.chapter) }
        }
        container.jobRepository.createJobs(JobMode.RETRANSLATE, targets)
        showToast(if (retranslate) LibraryToast.Retranslated(targets.size) else LibraryToast.Translated(targets.size))
    }

    private fun showToast(toast: LibraryToast) {
        ui.update { it.copy(toast = toast, confirmRetranslate = false, picking = false, selected = emptySet()) }
    }
}
