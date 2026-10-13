package com.tmm.mobile.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.ChapterDeleter
import com.tmm.mobile.data.DeleteOptions
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.autoTranslateTargets
import com.tmm.mobile.data.flatten
import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.IgnoredGapEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.SeriesSettingsEntity
import com.tmm.mobile.data.status.ChapterJoinInputs
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.buildChapterJoinRows
import com.tmm.mobile.data.status.deriveSeriesSummary
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.data.status.joinInputsFlow
import com.tmm.mobile.data.status.toAggregateInputs
import com.tmm.mobile.data.status.translatingPercent
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
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
 * ViewModel de la Fiche serie (maquette v2, ecrans 3 a 5) : liste a etiquette unique, feuille
 * « Que faire ? » pour la selection ou toute la serie, et interrupteur « Suivre cette serie »
 * (SeriesSettingsEntity.autoTranslate, lu par runAutoTranslate).
 */
class SeriesViewModel(
    private val container: AppContainer,
    private val seriesKey: String,
) : ViewModel() {

    private data class UiOnly(
        val selecting: Boolean = false,
        val selected: Set<Int> = emptySet(),
        val expandedStats: Set<Int> = emptySet(),
        val showGap: Boolean = true,
        val sheet: SeriesSheet = SeriesSheet.None,
        val toast: SeriesToast? = null,
        val sentOnFollow: List<Int> = emptyList(),
    )

    private data class SeriesData(
        val series: SeriesEntity?,
        val categoryLabel: String,
        val subtitle: String,
        val followed: Boolean,
        val joinRows: List<ChapterJoinRow>,
        val rows: List<SeriesRow>,
        val progress: SeriesProgress,
        val missing: List<Int>,
    )

    private val emptyData = SeriesData(null, "", "", false, emptyList(), emptyList(), SeriesProgress(0, 0, 0, 0, 0, 0), emptyList())

    private val ui = MutableStateFlow(UiOnly())

    // Jobs crees a l'activation du suivi, annulables depuis le toast tant qu'il est affiche.
    private var followJobIds: List<String> = emptyList()

    private data class CoreInputs(
        val series: SeriesEntity?,
        val joinInputs: ChapterJoinInputs,
        val qualities: List<ChapterQualityEntity>,
        val ignoredGaps: Set<Int>,
        val settingsJson: String?,
    )

    private val core = combine(
        container.db.seriesDao().observe(seriesKey),
        joinInputsFlow(container.db, seriesKey),
        container.db.chapterQualityDao().observeAll(),
        container.db.ignoredGapDao().observeForSeries(seriesKey),
        container.prefs.settingsJson,
    ) { series, joinInputs, qualities, ignoredGaps, settingsJson ->
        CoreInputs(series, joinInputs, qualities, ignoredGaps.map { it.number }.toSet(), settingsJson)
    }

    // ponytail: Eagerly (pas WhileSubscribed) pour que state.value soit a jour meme sans
    // collecteur Compose actif (necessaire pour les tests de ViewModel).
    private val dataState: StateFlow<SeriesData> = combine(
        core,
        container.db.categoryDao().observeAll(),
        container.db.seriesCategoryDao().observeAll(),
        container.db.seriesSettingsDao().observe(seriesKey),
    ) { c, categories, links, settings -> buildSeriesData(c, categories, links, settings?.autoTranslate == true) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyData)

    val state: StateFlow<SeriesState> = combine(ui, dataState) { u, d ->
        val selectedRows = d.joinRows.filter { it.number in u.selected }
        val deleteSkipped = selectedRows.count { ChapterDeleter.isSkipped(it) }
        val maxNumber = d.joinRows.maxOfOrNull { it.number }
        SeriesState(
            title = d.series?.title.orEmpty(),
            categoryLabel = d.categoryLabel,
            subtitle = d.subtitle,
            coverUrl = d.series?.thumbnailUrl,
            coverColor = TmmColors.placeholderFor(seriesKey),
            followed = d.followed,
            progress = d.progress,
            rows = d.rows,
            missing = d.missing,
            showGap = u.showGap,
            nextExpected = maxNumber?.plus(1),
            sentOnFollow = u.sentOnFollow,
            selecting = u.selecting,
            selected = u.selected,
            expandedStats = u.expandedStats,
            sheet = u.sheet,
            toast = u.toast,
            seriesCounts = actionCounts(d.joinRows),
            selectionCounts = actionCounts(selectedRows),
            toTranslateNumbers = d.joinRows.filter(::canTranslate).map { it.number }.distinct().sortedDescending(),
            selectionToDelete = selectedRows.size - deleteSkipped,
            selectionDeleteSkipped = deleteSkipped,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SeriesState(title = "", categoryLabel = "", subtitle = "", coverColor = TmmColors.placeholderFor(seriesKey)),
    )

    fun onEvent(event: SeriesEvent) {
        when (event) {
            SeriesEvent.StartSelection -> ui.update { it.copy(selecting = true) }
            SeriesEvent.EndSelection -> ui.update { it.copy(selecting = false, selected = emptySet()) }
            is SeriesEvent.ToggleChapter -> ui.update {
                val row = state.value.rows.firstOrNull { r -> r.number == event.number }
                if (row == null || row.locked || row.kind == ChapterKind.NotDownloaded) {
                    it
                } else {
                    it.copy(selected = it.selected.toMutableSet().apply { if (!add(event.number)) remove(event.number) })
                }
            }
            is SeriesEvent.ToggleStats -> ui.update {
                it.copy(expandedStats = it.expandedStats.toMutableSet().apply { if (!add(event.number)) remove(event.number) })
            }
            SeriesEvent.DismissGap -> dismissGap()
            SeriesEvent.TranslateAll -> performTranslate(allSeries = true)
            SeriesEvent.OpenSeriesMore -> ui.update { it.copy(sheet = SeriesSheet.More(allSeries = true)) }
            SeriesEvent.OpenSelectionMore -> ui.update { if (it.selected.isEmpty()) it else it.copy(sheet = SeriesSheet.More(allSeries = false)) }
            SeriesEvent.CloseSheet -> ui.update { it.copy(sheet = SeriesSheet.None) }
            SeriesEvent.Translate -> performTranslate(allSeries = isAllSeriesSheet())
            SeriesEvent.Install -> performInstall(allSeries = isAllSeriesSheet())
            SeriesEvent.Verify -> performJobs(isAllSeriesSheet(), ::canReprocess, JobMode.VERIFY) { SeriesToast.Verified(it) }
            SeriesEvent.Retranslate -> performJobs(isAllSeriesSheet(), ::canReprocess, JobMode.RETRANSLATE) { SeriesToast.Retranslated(it) }
            SeriesEvent.CopyToPc -> performJobs(isAllSeriesSheet(), ::canCopy, JobMode.TRANSLATE, translate = false) { SeriesToast.Copied(it) }
            SeriesEvent.RequestDelete -> requestDelete()
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
            is SeriesEvent.SetFollow -> if (event.follow) openFollowSheet() else setFollowed(false)
            SeriesEvent.ConfirmFollow -> confirmFollow()
            SeriesEvent.UndoFollow -> undoFollow()
            SeriesEvent.DismissToast -> ui.update { it.copy(toast = null) }
        }
    }

    private fun isAllSeriesSheet(): Boolean = (ui.value.sheet as? SeriesSheet.More)?.allSeries ?: false

    // La feuille « Supprimer » remplace « Que faire ? » : elle garde la portee (selection ou serie)
    // dans deleteAllSeries, SeriesSheet.Delete ne portant que les comptes affiches.
    private var deleteAllSeries = false

    private fun requestDelete() {
        val all = isAllSeriesSheet()
        val rows = rowsFor(all) { true }
        val skipped = rows.count { ChapterDeleter.isSkipped(it) }
        if (rows.size - skipped == 0) return
        deleteAllSeries = all
        ui.update { it.copy(sheet = SeriesSheet.Delete(rows.size - skipped, skipped)) }
    }

    /** ChapterDeleter ignore lui-meme les chapitres a job actif. */
    private fun performDelete() {
        val sheet = ui.value.sheet as? SeriesSheet.Delete ?: return
        if (!sheet.anyChecked) return
        viewModelScope.launch {
            val outcome = container.chapterDeleter.delete(
                rowsFor(deleteAllSeries) { true },
                DeleteOptions(localFr = sheet.localFr, mihon = sheet.mihon, pc = sheet.pc),
            )
            // Les flux Room (local_fr_chapter, downloaded_chapter) rafraichissent la liste d'eux-memes.
            showToast(SeriesToast.Deleted(outcome.chapters, outcome.failed, outcome.pcUnavailable))
        }
    }

    private fun rowsFor(allSeries: Boolean, predicate: (ChapterJoinRow) -> Boolean): List<ChapterJoinRow> {
        val nums = ui.value.selected
        return dataState.value.joinRows.filter { row -> (allSeries || row.number in nums) && predicate(row) }
    }

    private fun targetOf(row: ChapterJoinRow) =
        JobTarget(mihonPath = row.mihonPath, downloaded = row.downloaded, series = dataState.value.series, chapter = row.chapter)

    /**
     * Traduire (A) : un echec est relance tel quel (retry, reprise a la page en erreur), un original
     * deja sur le PC part en RETRANSLATE, un chapitre seulement telecharge en TRANSLATE.
     */
    private fun performTranslate(allSeries: Boolean) {
        viewModelScope.launch {
            val rows = rowsFor(allSeries, ::canTranslate)
            val repo = container.jobRepository
            val failed = rows.filter { it.status == ChapterStatus.FAILED }
            failed.forEach { row -> row.facts.latestJob?.let { repo.retry(it.jobId) } }
            val fromPc = rows.filter { it.status != ChapterStatus.FAILED && it.facts.index?.original == true }
            val fromPhone = rows.filter { it.status != ChapterStatus.FAILED && it.facts.index?.original != true }
            val created = repo.createJobs(JobMode.RETRANSLATE, fromPc.map(::targetOf)).createdJobIds.size +
                repo.createJobs(JobMode.TRANSLATE, fromPhone.map(::targetOf)).createdJobIds.size
            showToast(SeriesToast.Translated(created + failed.size))
        }
    }

    private fun performJobs(
        allSeries: Boolean,
        predicate: (ChapterJoinRow) -> Boolean,
        mode: String,
        translate: Boolean = true,
        toast: (Int) -> SeriesToast,
    ) {
        viewModelScope.launch {
            val targets = rowsFor(allSeries, predicate).map(::targetOf)
            val result = container.jobRepository.createJobs(mode, targets, translate = translate)
            showToast(toast(result.createdJobIds.size))
        }
    }

    private fun performInstall(allSeries: Boolean) {
        viewModelScope.launch {
            val jobIds = rowsFor(allSeries, ::canInstall).mapNotNull { it.facts.latestJob?.jobId }
            container.jobRepository.fetch(jobIds)
            showToast(SeriesToast.Installing(jobIds.size))
        }
    }

    private fun dismissGap() {
        viewModelScope.launch {
            dataState.value.missing.forEach { number -> container.db.ignoredGapDao().upsert(IgnoredGapEntity(seriesKey, number)) }
        }
        ui.update { it.copy(showGap = false) }
    }

    /** B, option 1 : la feuille annonce les chapitres deja telecharges qui partiront avec le suivi. */
    private fun openFollowSheet() {
        viewModelScope.launch {
            val numbers = autoTranslateTargets(container.db, setOf(seriesKey)).map { it.number }.sortedDescending()
            ui.update { it.copy(sheet = SeriesSheet.Follow(numbers)) }
        }
    }

    /**
     * Cree les jobs AVANT d'activer le suivi : sinon une actualisation (runAutoTranslate) pourrait
     * les creer entre-temps, et leurs ids manqueraient a « Annuler ».
     */
    private fun confirmFollow() {
        viewModelScope.launch {
            val targets = autoTranslateTargets(container.db, setOf(seriesKey))
            val ids = container.jobRepository.createJobs(JobMode.TRANSLATE, targets).createdJobIds
            container.db.seriesSettingsDao().upsert(SeriesSettingsEntity(seriesKey, autoTranslate = true))
            followJobIds = ids
            val sentPaths = container.db.jobDao().getAll(ids).map { it.mihonPath }.toSet()
            val sent = targets.filter { it.mihonPath.flatten() in sentPaths }.map { it.number }.sortedDescending()
            ui.update { it.copy(sheet = SeriesSheet.None, toast = SeriesToast.Followed(ids.size), sentOnFollow = sent) }
        }
    }

    /** « Annuler » du toast : coupe le suivi puis annule les envois qu'il vient de lancer. */
    private fun undoFollow() {
        val ids = followJobIds
        followJobIds = emptyList()
        ui.update { it.copy(toast = null, sentOnFollow = emptyList()) }
        viewModelScope.launch {
            container.db.seriesSettingsDao().upsert(SeriesSettingsEntity(seriesKey, autoTranslate = false))
            ids.forEach { container.jobRepository.cancel(it) }
        }
    }

    /** Desactiver : sans confirmation, les chapitres deja partis continuent. */
    private fun setFollowed(follow: Boolean) {
        followJobIds = emptyList()
        ui.update { it.copy(sentOnFollow = emptyList()) }
        viewModelScope.launch {
            container.db.seriesSettingsDao().upsert(SeriesSettingsEntity(seriesKey, autoTranslate = follow))
        }
    }

    private fun showToast(toast: SeriesToast) {
        ui.update { it.copy(toast = toast, sheet = SeriesSheet.None, selected = emptySet(), selecting = false) }
    }

    private fun buildSeriesData(
        c: CoreInputs,
        categories: List<CategoryEntity>,
        links: List<SeriesCategoryEntity>,
        followed: Boolean,
    ): SeriesData {
        val series = c.series ?: return emptyData
        val joinRows = buildChapterJoinRows(seriesKey, series.sourceName, series.title, c.joinInputs)
        val missing = deriveSeriesSummary(seriesKey, joinRows.toAggregateInputs(), c.ignoredGaps).missing
        val qualityByPath = c.qualities.associateBy { it.mihonPath }
        val chapterRows = dedupeCatalogOnly(joinRows).map { row -> row.toSeriesRow(qualityByPath[row.flatPath]) }
        val gapRows = missing.map { SeriesRow(it, ChapterKind.Missing) }
        val categoryNameByOrder = categories.associate { it.order to it.name }
        val primaryOrder = links.filter { it.seriesKey == seriesKey }.minOfOrNull { it.categoryOrder }
        return SeriesData(
            series = series,
            categoryLabel = primaryOrder?.let { categoryNameByOrder[it] } ?: "",
            subtitle = subtitleFor(series, c.settingsJson),
            followed = followed,
            joinRows = joinRows,
            rows = (chapterRows + gapRows).sortedByDescending { it.number },
            progress = progressOf(chapterRows),
            missing = missing,
        )
    }

    private fun subtitleFor(series: SeriesEntity, settingsJson: String?): String {
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
internal val ChapterJoinRow.number: Int get() = (chapter?.number ?: downloaded?.number ?: 0f).toInt()

private val JobTarget.number: Int get() = (chapter?.number ?: downloaded?.number ?: 0f).toInt()

private fun ChapterJoinRow.active(): Boolean = facts.latestJob?.isActive() == true

// Predicats des actions : memes regles que JobRepository.createJobs, pour que les comptes affiches
// correspondent a ce qui partira vraiment.
internal fun canTranslate(row: ChapterJoinRow): Boolean = !row.active() &&
    (row.status == ChapterStatus.TO_SEND || row.status == ChapterStatus.NOT_TRANSLATED || row.status == ChapterStatus.FAILED)

internal fun canInstall(row: ChapterJoinRow): Boolean = row.facts.latestJob?.phoneState == "RESULT_READY"

internal fun canReprocess(row: ChapterJoinRow): Boolean =
    !row.active() && row.status == ChapterStatus.UP_TO_DATE && row.facts.index?.original == true

internal fun canCopy(row: ChapterJoinRow): Boolean = !row.active() && row.status == ChapterStatus.TO_SEND

internal fun actionCounts(rows: List<ChapterJoinRow>) = ActionCounts(
    translate = rows.count(::canTranslate),
    install = rows.count(::canInstall),
    verify = rows.count(::canReprocess),
    retranslate = rows.count(::canReprocess),
    copy = rows.count(::canCopy),
)

private fun progressOf(rows: List<SeriesRow>) = SeriesProgress(
    inMihon = rows.count { it.kind == ChapterKind.InMihon },
    ready = rows.count { it.kind == ChapterKind.Ready },
    busy = rows.count { it.kind == ChapterKind.Waiting || it.kind is ChapterKind.Translating },
    toTranslate = rows.count { it.kind == ChapterKind.ToTranslate || it.kind == ChapterKind.OnPc || it.kind is ChapterKind.Failed },
    notDownloaded = rows.count { it.kind == ChapterKind.NotDownloaded },
    total = rows.size,
)

/**
 * Une ligne par numero de chapitre : un exemplaire present (telecharge, sur le PC, traduit) masque
 * ses doublons seulement au catalogue (autre scantrad, serie ajoutee deux fois), et un seul de ces
 * doublons reste quand aucun n'est present. Les numeros inconnus (-1 chez Mihon) restent tous.
 */
internal fun dedupeCatalogOnly(rows: List<ChapterJoinRow>): List<ChapterJoinRow> {
    fun exactNumber(row: ChapterJoinRow): Float? = row.chapter?.number ?: row.downloaded?.number
    val present = rows.filter { it.status != ChapterStatus.CATALOG_ONLY }.mapNotNullTo(HashSet(), ::exactNumber)
    val keptCatalog = HashSet<Float>()
    return rows.filter { row ->
        val number = exactNumber(row)
        row.status != ChapterStatus.CATALOG_ONLY || number == null || number < 0 || (number !in present && keptCatalog.add(number))
    }
}

private fun ChapterJoinRow.toSeriesRow(quality: ChapterQualityEntity?): SeriesRow {
    val job = facts.latestJob
    val kind = when (status) {
        ChapterStatus.TO_SEND -> ChapterKind.ToTranslate
        ChapterStatus.NOT_TRANSLATED -> ChapterKind.OnPc
        ChapterStatus.QUEUED -> ChapterKind.Waiting
        ChapterStatus.TRANSLATING -> ChapterKind.Translating(job?.let { translatingPercent(it) } ?: 0)
        ChapterStatus.FAILED -> ChapterKind.Failed(job?.pcErrorPage)
        ChapterStatus.TO_FETCH -> ChapterKind.Ready
        ChapterStatus.UP_TO_DATE -> ChapterKind.InMihon
        ChapterStatus.CATALOG_ONLY -> ChapterKind.NotDownloaded
    }
    val stats = quality?.let {
        val untranslatedPages = it.alerts.map { a -> a.page }.distinct().size
        SeriesChapterStats(
            pagesTotal = it.totalPages,
            pagesTranslated = it.totalPages - untranslatedPages,
            bubblesTranslated = it.translatedBubbles,
            bubblesUntranslated = it.untranslatedBubbles,
            sfxKept = it.sfxKept,
            verifiedDate = it.verifiedAt?.let { at -> Formatters.verifiedDateFr(at) },
        )
    }
    return SeriesRow(number = number, kind = kind, stats = stats)
}
