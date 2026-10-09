package com.tmm.mobile.ui.screens.series

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.ChapterDeleter
import com.tmm.mobile.data.DeleteOptions
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.IgnoredGapEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ChapterJoinInputs
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.buildChapterJoinRows
import com.tmm.mobile.data.status.deriveSeriesSummary
import com.tmm.mobile.data.status.joinInputsFlow
import com.tmm.mobile.data.status.planTranslation
import com.tmm.mobile.data.status.toAggregateInputs
import com.tmm.mobile.data.status.translatingPercent
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.theme.TmmColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel reel de l'ecran Fiche serie (E10, plan 7.3). SeriesState/SeriesEvent/SeriesScreen ne
 * changent pas (cf. SeriesScreenBehaviorTest, qui pilote l'ecran avec son propre reducer) : ce
 * ViewModel se contente de produire le meme type d'etat a partir de Room au lieu du mock.
 *
 * Point ouvert (plan 5.21) : le toggle "Traduction automatique" par serie (SeriesSettingsEntity)
 * n'a volontairement aucune UI ici - la maquette Series.dc.html ne l'affiche pas (R16, la maquette
 * gagne). SeriesSettingsEntity reste ecrit uniquement par le flux d'arrivee de chapitre (E9).
 */
class SeriesViewModel(
    private val container: AppContainer,
    private val seriesKey: String,
) : ViewModel() {

    private data class UiOnly(
        val selected: Set<Int> = emptySet(),
        val expandedStats: Set<Int> = emptySet(),
        val showGap: Boolean = true,
        val sheet: SeriesSheet = SeriesSheet.None,
        val toast: SeriesToast? = null,
        // ponytail: ConfirmRetranslate doit rejouer soit la selection (bouton "Retraduire" de la
        // barre de selection), soit toute la serie (feuille "Toute la serie") ; SeriesSheet.Confirm
        // ne porte que le compte affiche, pas la portee, d'ou ce drapeau prive.
        val retranslateSelectionOnly: Boolean = true,
    )

    private data class SeriesData(
        val series: SeriesEntity?,
        val title: String,
        val categoryLabel: String,
        val formatLabel: String,
        val onPhoneCount: Int,
        val onPcCount: Int,
        val frCount: Int,
        val totalChapters: Int,
        val chapters: List<SeriesChapter>,
        val rows: List<ChapterJoinRow>,
    )

    private val emptyData = SeriesData(null, "", "", "", 0, 0, 0, 0, emptyList(), emptyList())

    private val ui = MutableStateFlow(UiOnly())

    private val core = combine(
        container.db.seriesDao().observe(seriesKey),
        joinInputsFlow(container.db, seriesKey),
        container.db.chapterQualityDao().observeAll(),
        container.db.ignoredGapDao().observeForSeries(seriesKey),
        container.prefs.settingsJson,
    ) { series, joinInputs, qualities, ignoredGaps, settingsJson ->
        CoreInputs(series, joinInputs, qualities, ignoredGaps.map { it.number }.toSet(), settingsJson)
    }

    private data class CoreInputs(
        val series: SeriesEntity?,
        val joinInputs: ChapterJoinInputs,
        val qualities: List<ChapterQualityEntity>,
        val ignoredGaps: Set<Int>,
        val settingsJson: String?,
    )

    // ponytail: Eagerly (pas WhileSubscribed) pour que state.value soit a jour meme sans
    // collecteur Compose actif (necessaire pour les tests de ViewModel).
    private val dataState: StateFlow<SeriesData> = combine(
        core,
        container.db.categoryDao().observeAll(),
        container.db.seriesCategoryDao().observeAll(),
    ) { c, categories, links -> buildSeriesData(c, categories, links) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyData)

    val state: StateFlow<SeriesState> = combine(ui, dataState) { u, d ->
        val selectedRows = d.rows.filter { it.number in u.selected }
        val plan = planTranslation(selectedRows)
        val deleteSkipped = selectedRows.count { ChapterDeleter.isSkipped(it) }
        SeriesState(
            title = d.title,
            categoryLabel = d.categoryLabel,
            formatLabel = d.formatLabel,
            coverColor = TmmColors.placeholderFor(seriesKey),
            onPhoneCount = d.onPhoneCount,
            onPcCount = d.onPcCount,
            frCount = d.frCount,
            totalChapters = d.totalChapters,
            chapters = d.chapters,
            selected = u.selected,
            expandedStats = u.expandedStats,
            showGap = u.showGap,
            sheet = u.sheet,
            toast = u.toast,
            selectionToTranslate = plan.count,
            selectionRetranslates = plan.retranslate,
            selectionToDelete = selectedRows.size - deleteSkipped,
            selectionDeleteSkipped = deleteSkipped,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SeriesState(title = "", categoryLabel = "", formatLabel = "", coverColor = TmmColors.placeholderFor(seriesKey), onPhoneCount = 0, onPcCount = 0, frCount = 0, totalChapters = 0, chapters = emptyList()),
    )

    fun onEvent(event: SeriesEvent) {
        when (event) {
            is SeriesEvent.ToggleChapter -> ui.update {
                val chapter = state.value.chapters.firstOrNull { c -> c.number == event.number }
                if (chapter == null || chapter.locked) {
                    it
                } else {
                    it.copy(selected = it.selected.toMutableSet().apply { if (!add(event.number)) remove(event.number) })
                }
            }
            SeriesEvent.SelectTodo -> ui.update {
                val todo = state.value.chapters.filter { c -> c.kind == StatusTone.Send || c.kind == StatusTone.Get }.map { c -> c.number }
                it.copy(selected = todo.toSet())
            }
            SeriesEvent.ClearSelection -> ui.update { it.copy(selected = emptySet()) }
            SeriesEvent.DismissGap -> dismissGap()
            is SeriesEvent.ToggleStats -> ui.update {
                it.copy(expandedStats = it.expandedStats.toMutableSet().apply { if (!add(event.number)) remove(event.number) })
            }
            SeriesEvent.SendToPc -> performJobs(selected = true, predicate = { it.status == ChapterStatus.TO_SEND }, mode = JobMode.TRANSLATE) { SeriesToast.Sent(it) }
            SeriesEvent.FetchToMihon -> performFetch(selected = true)
            SeriesEvent.VerifyPending -> performJobs(selected = true, predicate = { it.status == ChapterStatus.UP_TO_DATE }, mode = JobMode.VERIFY) { SeriesToast.Verified(it) }
            // "Retraduire" ecrase une traduction : confirmation d'abord. "Traduire" agit directement.
            SeriesEvent.RequestRetranslateSelection -> {
                val current = state.value
                when {
                    current.selectionToTranslate == 0 -> Unit
                    current.selectionRetranslates -> ui.update {
                        it.copy(sheet = SeriesSheet.Confirm(current.selectionToTranslate), retranslateSelectionOnly = true)
                    }
                    else -> performTranslation()
                }
            }
            SeriesEvent.OpenSeriesSheet -> ui.update { it.copy(sheet = SeriesSheet.SeriesActions) }
            SeriesEvent.CloseSheet -> ui.update { it.copy(sheet = SeriesSheet.None) }
            SeriesEvent.SeriesSendMissing -> performJobs(selected = false, predicate = { it.status == ChapterStatus.TO_SEND }, mode = JobMode.TRANSLATE) { SeriesToast.Sent(it) }
            SeriesEvent.SeriesFetchMissing -> performFetch(selected = false)
            SeriesEvent.SeriesVerifyAll -> performJobs(selected = false, predicate = { it.status == ChapterStatus.UP_TO_DATE }, mode = JobMode.VERIFY) { SeriesToast.Verified(it) }
            SeriesEvent.SeriesRequestRetranslateAll -> ui.update { it.copy(sheet = SeriesSheet.Confirm(dataState.value.frCount), retranslateSelectionOnly = false) }
            SeriesEvent.ConfirmRetranslate -> if (ui.value.retranslateSelectionOnly) {
                performTranslation()
            } else {
                performJobs(selected = false, predicate = { it.status == ChapterStatus.UP_TO_DATE }, mode = JobMode.RETRANSLATE) { SeriesToast.Retranslated(it) }
            }
            SeriesEvent.RequestDelete -> {
                val current = state.value
                if (current.selectionToDelete > 0) {
                    ui.update { it.copy(sheet = SeriesSheet.Delete(current.selectionToDelete, current.selectionDeleteSkipped)) }
                }
            }
            is SeriesEvent.ToggleDeleteTarget -> ui.update {
                val sheet = it.sheet as? SeriesSheet.Delete ?: return@update it
                it.copy(
                    sheet = when (event.target) {
                        DeleteTarget.LocalFr -> sheet.copy(localFr = !sheet.localFr)
                        DeleteTarget.Mihon -> sheet.copy(mihon = !sheet.mihon)
                        DeleteTarget.Pc -> sheet.copy(pc = !sheet.pc)
                    },
                )
            }
            SeriesEvent.ConfirmDelete -> performDelete()
            SeriesEvent.DismissToast -> ui.update { it.copy(toast = null) }
        }
    }

    /** Feuille "Supprimer" : ChapterDeleter ignore lui-meme les chapitres a job actif. */
    private fun performDelete() {
        val sheet = ui.value.sheet as? SeriesSheet.Delete ?: return
        if (!sheet.anyChecked) return
        viewModelScope.launch {
            val outcome = container.chapterDeleter.delete(
                rowsFor(selected = true) { true },
                DeleteOptions(localFr = sheet.localFr, mihon = sheet.mihon, pc = sheet.pc),
            )
            // Les flux Room (local_fr_chapter, downloaded_chapter) rafraichissent la liste d'eux-memes.
            showToast(SeriesToast.Deleted(outcome.chapters, outcome.failed, outcome.pcUnavailable))
        }
    }

    private fun rowsFor(selected: Boolean, predicate: (ChapterJoinRow) -> Boolean): List<ChapterJoinRow> {
        val d = dataState.value
        val nums = ui.value.selected
        return d.rows.filter { row -> (!selected || row.number in nums) && predicate(row) }
    }

    /** Bouton "Traduire"/"Retraduire" de la selection : cibles et libelle du toast via planTranslation. */
    private fun performTranslation() {
        viewModelScope.launch {
            val plan = planTranslation(rowsFor(selected = true) { true })
            val targets = plan.targets.map { JobTarget(mihonPath = it.mihonPath, downloaded = it.downloaded, series = dataState.value.series, chapter = it.chapter) }
            container.jobRepository.createJobs(JobMode.RETRANSLATE, targets)
            showToast(if (plan.retranslate) SeriesToast.Retranslated(targets.size) else SeriesToast.Translated(targets.size))
        }
    }

    private fun performJobs(selected: Boolean, predicate: (ChapterJoinRow) -> Boolean, mode: String, toast: (Int) -> SeriesToast) {
        viewModelScope.launch {
            val rows = rowsFor(selected, predicate)
            val targets = rows.map { JobTarget(mihonPath = it.mihonPath, downloaded = it.downloaded, series = dataState.value.series, chapter = it.chapter) }
            container.jobRepository.createJobs(mode, targets)
            showToast(toast(targets.size))
        }
    }

    private fun performFetch(selected: Boolean) {
        viewModelScope.launch {
            val jobIds = rowsFor(selected) { it.facts.latestJob?.phoneState == "RESULT_READY" }
                .mapNotNull { it.facts.latestJob?.jobId }
            container.jobRepository.fetch(jobIds)
            showToast(SeriesToast.Received(jobIds.size))
        }
    }

    private fun dismissGap() {
        viewModelScope.launch {
            val missing = deriveSeriesSummary(seriesKey, dataState.value.rows.toAggregateInputs(), emptySet()).missing
            missing.forEach { number -> container.db.ignoredGapDao().upsert(IgnoredGapEntity(seriesKey, number)) }
        }
        ui.update { it.copy(showGap = false) }
    }

    private fun showToast(toast: SeriesToast) {
        ui.update { it.copy(toast = toast, sheet = SeriesSheet.None, selected = emptySet()) }
    }

    private fun buildSeriesData(c: CoreInputs, categories: List<CategoryEntity>, links: List<SeriesCategoryEntity>): SeriesData {
        val series = c.series ?: return emptyData
        val rows = buildChapterJoinRows(seriesKey, series.sourceName, series.title, c.joinInputs)
        val summary = deriveSeriesSummary(seriesKey, rows.toAggregateInputs(), c.ignoredGaps)
        val qualityByPath = c.qualities.associateBy { it.mihonPath }
        val realChapters = rows.map { row -> row.toSeriesChapter(qualityByPath[row.flatPath]) }
        val gapChapters = summary.missing.map { number ->
            SeriesChapter(number, StatusTone.Gap, "Manquant partout", TmmColors.dangerText, onPhone = false, onPc = false, onFr = false)
        }
        val categoryNameByOrder = categories.associate { it.order to it.name }
        val primaryOrder = links.filter { it.seriesKey == seriesKey }.minOfOrNull { it.categoryOrder }
        val categoryLabel = primaryOrder?.let { categoryNameByOrder[it] } ?: ""
        val formatLabel = formatLabelFor(series, c.settingsJson)
        return SeriesData(
            series = series,
            title = series.title,
            categoryLabel = categoryLabel,
            formatLabel = formatLabel,
            onPhoneCount = summary.onPhone,
            onPcCount = summary.onPc,
            frCount = summary.frCount,
            totalChapters = c.joinInputs.chapters.count { it.seriesKey == seriesKey },
            chapters = (realChapters + gapChapters).sortedByDescending { it.number },
            rows = rows,
        )
    }

    private fun formatLabelFor(series: SeriesEntity, settingsJson: String?): String {
        val kind = series.mangaType?.replaceFirstChar { it.uppercase() } ?: "Manga"
        val settings = settingsJson?.let {
            runCatching { ProtocolJson.decodeFromString(PcSettings.serializer(), it) }.getOrNull()
        }
        val values = settings?.values ?: return kind
        val source = Formatters.langLabelFr(values.sourceLanguage)
        val target = Formatters.langLabelFr(values.targetLanguage)
        return "$kind · $source vers $target"
    }
}

/** Numero affiche (et cle de selection) d'une ligne : les hors-serie 7.5 se rangent sous 7. */
private val ChapterJoinRow.number: Int get() = (chapter?.number ?: downloaded?.number ?: 0f).toInt()

private fun ChapterJoinRow.toSeriesChapter(quality: ChapterQualityEntity?): SeriesChapter {
    val job = facts.latestJob
    val untranslatedPages = quality?.alerts?.map { it.page }?.distinct()?.size ?: 0
    val pagesTranslated = (quality?.totalPages ?: 0) - untranslatedPages
    val (kind, label, color) = when (status) {
        ChapterStatus.TO_SEND -> Triple(StatusTone.Send, "Sur le téléphone · à envoyer", TmmColors.dangerText)
        ChapterStatus.QUEUED -> Triple(StatusTone.Busy, "En file sur le PC", TmmColors.ink)
        ChapterStatus.TRANSLATING -> {
            val pct = job?.let { translatingPercent(it) } ?: 0
            Triple(StatusTone.Busy, "Traduction en cours · $pct %", TmmColors.ink)
        }
        ChapterStatus.TO_FETCH -> Triple(StatusTone.Get, "Traduit · à récupérer", TmmColors.info)
        ChapterStatus.UP_TO_DATE -> if (untranslatedPages > 0) {
            val s = if (untranslatedPages > 1) "s" else ""
            Triple(StatusTone.Ok, "À jour · $untranslatedPages page$s restée$s en VO", TmmColors.absentText)
        } else {
            Triple(StatusTone.Ok, "À jour partout", TmmColors.success)
        }
        ChapterStatus.FAILED -> {
            val suffix = job?.pcErrorPage?.let { " · page $it" } ?: ""
            Triple(StatusTone.Send, "Échec$suffix", TmmColors.dangerText)
        }
        ChapterStatus.NOT_TRANSLATED -> Triple(StatusTone.Absent, "Original sur le PC · non traduit", TmmColors.textMuted)
        ChapterStatus.CATALOG_ONLY -> Triple(StatusTone.Absent, "Dans Mihon, non téléchargé", TmmColors.textMuted)
    }
    val progress = if (status == ChapterStatus.TRANSLATING) (job?.let { translatingPercent(it) } ?: 0) / 100f else null
    val stats = quality?.let {
        SeriesChapterStats(
            pagesTotal = it.totalPages,
            pagesTranslated = pagesTranslated,
            bubblesTranslated = it.translatedBubbles,
            bubblesUntranslated = it.untranslatedBubbles,
            sfxKept = it.sfxKept,
            verifiedDate = it.verifiedAt?.let { at -> Formatters.verifiedDateFr(at) },
        )
    }
    return SeriesChapter(
        number = number,
        kind = kind,
        statusLabel = label,
        statusColor = color,
        onPhone = facts.downloaded,
        onPc = facts.index?.original == true,
        onFr = facts.localFr,
        translationProgress = progress,
        stats = stats,
    )
}
