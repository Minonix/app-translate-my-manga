package com.tmm.mobile.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.data.ChapterDeleter
import com.tmm.mobile.data.DeleteOptions
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.autoTranslateTargets
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.SeriesSettingsEntity
import com.tmm.mobile.data.status.ChapterJoinIndex
import com.tmm.mobile.data.status.ChapterJoinInputs
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.buildChapterJoinRows
import com.tmm.mobile.ui.screens.series.DeleteTarget
import com.tmm.mobile.ui.screens.series.SeriesSheet
import com.tmm.mobile.ui.screens.series.actionCounts
import com.tmm.mobile.ui.screens.series.canCopy
import com.tmm.mobile.ui.screens.series.canInstall
import com.tmm.mobile.ui.screens.series.canRedeliver
import com.tmm.mobile.ui.screens.series.canReprocess
import com.tmm.mobile.ui.screens.series.canRetranslate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel reel de l'ecran Bibliotheque (E10, plan 7.2) : la liste agregee (SeriesSummary par
 * serie) vient de LibraryCache, partage entre les ecrans et persiste sur disque. Les actions de la
 * selection (installer, corriger, retraduire, copier, supprimer, suivre) passent par JobRepository,
 * ChapterDeleter et autoTranslateTargets, avec les memes predicats que la fiche serie.
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
        val categoriesExpanded: Boolean = false,
        val moreOpen: Boolean = false,
        val actions: LibraryActions? = null,
        val delete: SeriesSheet.Delete? = null,
        val toast: LibraryToast? = null,
    )

    private val ui = MutableStateFlow(UiOnly(picking = initialPicking))

    // Liste partagee et persistee (LibraryCache) : affichee tout de suite, recalculee en fond.
    private val dataState: StateFlow<LibrarySnapshot> = container.libraryCache.data

    private val backupModifiedAt = container.db.backupImportDao().observe().map { it?.lastModified }

    val state: StateFlow<LibraryState> = combine(ui, dataState, backupModifiedAt) { u, d, backupAt ->
        LibraryState(
            query = u.query,
            picking = u.picking,
            selected = u.selected,
            category = u.category,
            categories = d.categories,
            details = d.details,
            view = u.view,
            sort = u.sort,
            filtersOpen = u.filtersOpen,
            allSeries = d.series,
            moreOpen = u.moreOpen,
            actions = u.actions,
            delete = u.delete,
            categoriesExpanded = u.categoriesExpanded,
            backupModifiedAt = backupAt,
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
            LibraryEvent.TogglePicking -> ui.update { it.copy(picking = !it.picking, selected = emptySet(), moreOpen = false, actions = null, delete = null) }
            is LibraryEvent.SelectCategory -> ui.update { it.copy(category = event.name) }
            is LibraryEvent.SelectView -> ui.update { it.copy(view = event.view) }
            is LibraryEvent.SelectSort -> ui.update { it.copy(sort = event.sort) }
            LibraryEvent.OpenFilters -> ui.update { it.copy(filtersOpen = true) }
            LibraryEvent.CloseFilters -> ui.update { it.copy(filtersOpen = false) }
            LibraryEvent.ExpandCategories -> ui.update { it.copy(categoriesExpanded = true) }
            is LibraryEvent.ToggleSelected -> ui.update {
                it.copy(selected = it.selected.toMutableSet().apply { if (!add(event.seriesKey)) remove(event.seriesKey) })
            }
            LibraryEvent.ToggleSelectAll -> ui.update {
                val visible = state.value.visibleSeries.map { s -> s.seriesKey }
                it.copy(selected = if (state.value.allVisibleSelected) emptySet() else visible.toSet())
            }
            LibraryEvent.OpenMore -> openMore()
            LibraryEvent.CloseMore -> ui.update { it.copy(moreOpen = false, actions = null) }
            LibraryEvent.Install -> performInstall()
            LibraryEvent.Verify -> performJobs(::canReprocess, JobMode.VERIFY) { LibraryToast.Verified(it) }
            LibraryEvent.Retranslate -> performJobs(::canRetranslate, JobMode.RETRANSLATE) { LibraryToast.Retranslated(it) }
            // A : « Copier sur le PC sans traduire » part avec translate = false.
            LibraryEvent.CopyToPc -> performJobs(::canCopy, JobMode.TRANSLATE, translate = false) { LibraryToast.Copied(it) }
            LibraryEvent.Follow -> performFollow()
            LibraryEvent.RequestDelete -> ui.update { u ->
                val a = u.actions
                if (a == null || a.deletable == 0) u else u.copy(moreOpen = false, delete = SeriesSheet.Delete(a.deletable, a.deleteSkipped))
            }
            is LibraryEvent.ToggleDeleteTarget -> ui.update { u ->
                val sheet = u.delete
                if (sheet == null) {
                    u
                } else {
                    u.copy(
                        delete = when (event.target) {
                            DeleteTarget.LocalFr -> sheet.copy(localFr = !sheet.localFr)
                            DeleteTarget.Mihon -> sheet.copy(mihon = !sheet.mihon)
                            DeleteTarget.Pc -> sheet.copy(pc = !sheet.pc)
                        },
                    )
                }
            }
            LibraryEvent.ConfirmDelete -> performDelete()
            LibraryEvent.CloseDelete -> ui.update { it.copy(delete = null, actions = null) }
            LibraryEvent.DismissToast -> ui.update { it.copy(toast = null) }
        }
    }

    /** Ouvre « Que faire ? » ; les comptes arrivent une fois relus depuis Room (seules les series choisies). */
    private fun openMore() {
        if (ui.value.selected.isEmpty()) return
        ui.update { it.copy(moreOpen = true, actions = null) }
        viewModelScope.launch {
            val selected = selectedRows()
            val rows = selected.flatMap { it.second }
            val skipped = rows.count(ChapterDeleter::isSkipped)
            val details = state.value.details
            val actions = LibraryActions(
                counts = actionCounts(rows),
                deletable = rows.size - skipped,
                deleteSkipped = skipped,
                toFollow = selected.count { (series, _) -> details[series.seriesKey]?.followed != true },
            )
            // La feuille a pu etre fermee pendant le calcul : ne pas la rouvrir.
            ui.update { if (it.moreOpen) it.copy(actions = actions) else it }
        }
    }

    /**
     * Chapitres des series selectionnees, recalcules a la demande depuis Room : le cache de la
     * Bibliotheque ne garde que le resume par serie, et seules les series choisies sont relues.
     */
    private suspend fun selectedRows(): List<Pair<SeriesEntity, List<ChapterJoinRow>>> = withContext(Dispatchers.Default) {
        val db = container.db
        val keys = state.value.allSeries.filter { it.seriesKey in state.value.selected }.map { it.seriesKey }
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

    private fun performJobs(
        predicate: (ChapterJoinRow) -> Boolean,
        mode: String,
        translate: Boolean = true,
        toast: (Int) -> LibraryToast,
    ) {
        viewModelScope.launch {
            val result = container.jobRepository.createJobs(mode, collectTargets(predicate), translate = translate)
            showToast(toast(result.createdJobIds.size))
        }
    }

    private fun performInstall() {
        viewModelScope.launch {
            val jobIds = selectedRows().flatMap { (_, rows) ->
                rows.filter { it.facts.latestJob?.phoneState == "RESULT_READY" }.mapNotNull { it.facts.latestJob?.jobId }
            }
            container.jobRepository.fetch(jobIds)
            // Comme la fiche serie : l'index seul se redemande au PC, sans retraduire (R13).
            val redelivered = container.jobRepository
                .createJobs(JobMode.TRANSLATE, collectTargets(::canRedeliver), redeliver = true).createdJobIds.size
            showToast(LibraryToast.Installing(jobIds.size + redelivered))
        }
    }

    /**
     * « Suivre ces series » (B, option 1) : comme la fiche serie, les chapitres deja telecharges
     * partent d'abord, puis le suivi est active (une actualisation ne les recree donc pas).
     */
    private fun performFollow() {
        val details = state.value.details
        val keys = ui.value.selected.filter { details[it]?.followed != true }.toSet()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            val db = container.db
            val created = container.jobRepository.createJobs(JobMode.TRANSLATE, autoTranslateTargets(db, keys)).createdJobIds.size
            keys.forEach { db.seriesSettingsDao().upsert(SeriesSettingsEntity(it, autoTranslate = true)) }
            showToast(LibraryToast.Followed(keys.size, created))
        }
    }

    /** ChapterDeleter ignore lui-meme les chapitres dont le dernier job est actif. */
    private fun performDelete() {
        val sheet = ui.value.delete ?: return
        if (!sheet.anyChecked) return
        viewModelScope.launch {
            val rows = selectedRows().flatMap { it.second }
            val outcome = container.chapterDeleter.delete(rows, DeleteOptions(localFr = sheet.localFr, mihon = sheet.mihon, pc = sheet.pc))
            showToast(LibraryToast.Deleted(outcome.chapters, outcome.failed, outcome.pcUnavailable))
        }
    }

    private fun showToast(toast: LibraryToast) {
        ui.update { it.copy(toast = toast, moreOpen = false, actions = null, delete = null, picking = false, selected = emptySet()) }
    }
}
