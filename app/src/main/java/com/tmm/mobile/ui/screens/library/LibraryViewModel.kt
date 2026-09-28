package com.tmm.mobile.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ChapterJoinIndex
import com.tmm.mobile.data.status.ChapterJoinInputs
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.SeriesBadge
import com.tmm.mobile.data.status.SeriesSummary
import com.tmm.mobile.data.status.badge
import com.tmm.mobile.data.status.buildChapterJoinRows
import com.tmm.mobile.data.status.deriveSeriesSummary
import com.tmm.mobile.data.status.joinInputsFlow
import com.tmm.mobile.data.status.toAggregateInputs
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel reel de l'ecran Bibliotheque (E10, plan 7.2) : agrege series/categories/chapitres/
 * jobs Room via SeriesSummary (data/status) en LibraryState. Les actions (envoyer/recuperer/
 * verifier/retraduire) passent par JobRepository, comme les autres ecrans de cette milestone.
 */
class LibraryViewModel(
    private val container: AppContainer,
    initialCategoryOrder: Long? = null,
) : ViewModel() {

    private data class UiOnly(
        val query: String = "",
        val picking: Boolean = false,
        val selected: Set<String> = emptySet(),
        val category: String = "Tous",
        val view: LibraryViewFilter = LibraryViewFilter.All,
        val confirmRetranslate: Boolean = false,
        val toast: LibraryToast? = null,
    )

    private data class LibraryData(
        val categories: List<Pair<String, Int>>,
        val allSeries: List<MockSeries>,
        val seriesByTitle: Map<String, SeriesEntity>,
        val rowsByTitle: Map<String, List<ChapterJoinRow>>,
    )

    private val emptyData = LibraryData(categories = listOf("Tous" to 0), allSeries = emptyList(), seriesByTitle = emptyMap(), rowsByTitle = emptyMap())

    private val ui = MutableStateFlow(UiOnly())

    // ponytail: Eagerly (pas WhileSubscribed) pour que onEvent()/state.value soient coherents
    // meme sans collecteur Compose actif (necessaire pour les tests de ViewModel).
    private val dataState: StateFlow<LibraryData> = combine(
        container.db.seriesDao().observeAll(),
        container.db.categoryDao().observeAll(),
        container.db.seriesCategoryDao().observeAll(),
        joinInputsFlow(container.db),
    ) { seriesList, categories, links, joinInputs -> buildLibraryData(seriesList, categories, links, joinInputs) }
        // Hors thread principal : avec ~1000 series le calcul prend du temps (freeze en v1.0.3).
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyData)

    val state: StateFlow<LibraryState> = combine(ui, dataState) { u, d ->
        LibraryState(
            query = u.query,
            picking = u.picking,
            selected = u.selected,
            category = u.category,
            categories = d.categories,
            view = u.view,
            allSeries = d.allSeries,
            confirmRetranslate = u.confirmRetranslate,
            toast = u.toast,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState(categories = emptyData.categories, allSeries = emptyList()))

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
            LibraryEvent.RequestRetranslate -> ui.update {
                if (state.value.selectionToVerify > 0) it.copy(confirmRetranslate = true) else it
            }
            LibraryEvent.ConfirmRetranslate -> performRetranslate()
            LibraryEvent.DismissConfirm -> ui.update { it.copy(confirmRetranslate = false) }
            LibraryEvent.DismissToast -> ui.update { it.copy(toast = null) }
        }
    }

    private fun collectTargets(predicate: (ChapterJoinRow) -> Boolean): List<JobTarget> {
        val d = dataState.value
        return state.value.selected.flatMap { title ->
            val series = d.seriesByTitle[title] ?: return@flatMap emptyList()
            (d.rowsByTitle[title] ?: emptyList()).filter(predicate).map { row ->
                JobTarget(mihonPath = row.mihonPath, downloaded = row.downloaded, series = series, chapter = row.chapter)
            }
        }
    }

    private fun performSend() = viewModelScope.launch {
        val targets = collectTargets { it.status == ChapterStatus.TO_SEND }
        container.jobRepository.createJobs(JobMode.TRANSLATE, targets)
        showToast(LibraryToast.Sent(targets.size))
    }

    private fun performFetch() = viewModelScope.launch {
        val d = dataState.value
        val jobIds = state.value.selected.flatMap { title ->
            (d.rowsByTitle[title] ?: emptyList()).mapNotNull { row ->
                row.facts.latestJob?.takeIf { it.phoneState == "RESULT_READY" }?.jobId
            }
        }
        container.jobRepository.fetch(jobIds)
        showToast(LibraryToast.Received(jobIds.size))
    }

    private fun performVerify() = viewModelScope.launch {
        val targets = collectTargets { it.status == ChapterStatus.UP_TO_DATE }
        container.jobRepository.createJobs(JobMode.VERIFY, targets)
        showToast(LibraryToast.Verified(targets.size))
    }

    private fun performRetranslate() = viewModelScope.launch {
        val targets = collectTargets { it.status == ChapterStatus.UP_TO_DATE }
        container.jobRepository.createJobs(JobMode.RETRANSLATE, targets)
        showToast(LibraryToast.Retranslated(targets.size))
    }

    private fun showToast(toast: LibraryToast) {
        ui.update { it.copy(toast = toast, confirmRetranslate = false, picking = false, selected = emptySet()) }
    }

    private fun buildLibraryData(
        seriesList: List<SeriesEntity>,
        categories: List<CategoryEntity>,
        links: List<SeriesCategoryEntity>,
        joinInputs: ChapterJoinInputs,
    ): LibraryData {
        val linksBySeries = links.groupBy { it.seriesKey }
        val categoryNameByOrder = categories.associate { it.order to it.name }
        val countByCategory = links.groupingBy { it.categoryOrder }.eachCount()
        val chips = listOf("Tous" to seriesList.size) + categories.map { cat -> cat.name to (countByCategory[cat.order] ?: 0) }
        val index = ChapterJoinIndex(joinInputs)
        val entries = seriesList.map { series -> series to buildChapterJoinRows(series.seriesKey, series.sourceName, series.title, index) }
        val mockSeries = entries.map { (series, rows) ->
            val summary = deriveSeriesSummary(series.seriesKey, rows.toAggregateInputs(), emptySet())
            val badge = summary.badge()
            val primaryOrder = linksBySeries[series.seriesKey]?.minOfOrNull { it.categoryOrder }
            val categoryName = primaryOrder?.let { categoryNameByOrder[it] } ?: ""
            val totalChapters = index.chaptersBySeries[series.seriesKey]?.size ?: 0
            MockSeries(
                title = series.title,
                colorHex = "",
                category = categoryName,
                frChapters = summary.frCount,
                totalChapters = totalChapters,
                toSend = summary.toSend,
                toGet = summary.toFetch,
                badge = libraryBadgeLabel(badge, summary),
                tone = badge.toStatusTone(),
                seriesKey = series.seriesKey,
                thumbnailUrl = series.thumbnailUrl,
            )
        }
        return LibraryData(
            categories = chips,
            allSeries = mockSeries,
            seriesByTitle = seriesList.associateBy { it.title },
            rowsByTitle = entries.associate { (series, rows) -> series.title to rows },
        )
    }
}

private fun libraryBadgeLabel(badge: SeriesBadge, s: SeriesSummary): String = when (badge) {
    SeriesBadge.BUSY -> "Traduction en cours"
    SeriesBadge.SEND -> "${s.toSend} à envoyer"
    SeriesBadge.GET -> "${s.toFetch} à récupérer"
    SeriesBadge.ABSENT -> "Non téléchargée"
    SeriesBadge.OK -> "À jour"
}

private fun SeriesBadge.toStatusTone(): StatusTone = when (this) {
    SeriesBadge.BUSY -> StatusTone.Busy
    SeriesBadge.SEND -> StatusTone.Send
    SeriesBadge.GET -> StatusTone.Get
    SeriesBadge.ABSENT -> StatusTone.Absent
    SeriesBadge.OK -> StatusTone.Ok
}
