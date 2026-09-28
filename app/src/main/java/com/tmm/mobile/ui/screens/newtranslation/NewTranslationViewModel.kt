package com.tmm.mobile.ui.screens.newtranslation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobRepository
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.ui.theme.TmmColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel reel de la feuille "Nouvelle traduction" (E10, NewTranslation.dc.html, plan 7.7).
 *
 * Cette feuille n'est PAS une destination de navigation (pas de NewTranslationRoute) : elle est
 * affichee en overlay (TmmSheet) par n'importe quel ecran (Home, Series...) qui construit ce
 * ViewModel via l'AppContainer, collecte [state] et appelle [onEvent]. Elle deduit elle-meme,
 * depuis Room, la liste des series ayant au moins un chapitre TO_SEND ; l'ecran appelant n'a rien
 * a lui passer en parametre.
 *
 * API publique : constructeur `NewTranslationViewModel(container)`, `state: StateFlow<NewTranslationState>`,
 * `onEvent(NewTranslationEvent)`.
 */
class NewTranslationViewModel(private val container: AppContainer) : ViewModel() {
    private val db: TmmDatabase get() = container.db
    private val jobRepository: JobRepository get() = container.jobRepository

    private val ui = MutableStateFlow(UiState())

    val state = combine(
        combine(
            db.seriesDao().observeAll(),
            db.categoryDao().observeAll(),
            db.seriesCategoryDao().observeAll(),
            container.prefs.translateOnArrival,
        ) { series, categories, links, translateOnArrival -> DbDataA(series, categories, links, translateOnArrival) },
        combine(
            db.downloadedChapterDao().observeAll(),
            db.localFrChapterDao().observeAll(),
            db.jobDao().observeAll(),
            db.pcIndexChapterDao().observeAll(),
        ) { downloaded, localFr, jobs, pcIndex -> DbDataB(downloaded, localFr, jobs, pcIndex) },
        ui,
    ) { a, b, u -> buildState(a, b, u) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewTranslationState())

    // ponytail: cache mutable rafraichi a chaque emission combine (thread Default), lu au Send ;
    // @Volatile suffit, la reference est remplacee d'un bloc.
    @Volatile private var downloadedByPath: Map<String, DownloadedChapterEntity> = emptyMap()

    fun onEvent(event: NewTranslationEvent) {
        when (event) {
            is NewTranslationEvent.UpdateQuery -> ui.update { it.copy(searchQuery = event.query) }
            is NewTranslationEvent.SelectSeries -> ui.update { cur ->
                val series = state.value.seriesList.firstOrNull { it.seriesKey == event.seriesKey }
                cur.copy(
                    selectedSeriesKey = event.seriesKey,
                    selectedChapters = series?.chapters?.map { it.mihonPath }?.toSet() ?: emptySet(),
                    phase = NewTranslationPhase.Idle,
                )
            }
            NewTranslationEvent.BackToSeries -> ui.update {
                it.copy(selectedSeriesKey = null, selectedChapters = emptySet(), phase = NewTranslationPhase.Idle)
            }
            is NewTranslationEvent.ToggleChapter -> ui.update { cur ->
                val next = if (event.mihonPath in cur.selectedChapters) {
                    cur.selectedChapters - event.mihonPath
                } else {
                    cur.selectedChapters + event.mihonPath
                }
                cur.copy(selectedChapters = next)
            }
            NewTranslationEvent.ToggleAllChapters -> ui.update { cur ->
                val series = state.value.selectedSeries ?: return@update cur
                val allPaths = series.chapters.map { it.mihonPath }.toSet()
                val next = if (state.value.isAllChaptersSelected) emptySet() else allPaths
                cur.copy(selectedChapters = next)
            }
            // PLAN_GEMINI.md 7.7 : le bascule ici est local a la feuille, il ne modifie pas la
            // preference translate_on_arrival persistee (pas d'ecriture prefs).
            NewTranslationEvent.ToggleTranslateOnArrival -> ui.update {
                it.copy(translateOnArrivalOverride = !state.value.translateOnArrival)
            }
            NewTranslationEvent.Send -> send()
        }
    }

    private fun send() {
        // Selection lue dans `ui` (synchrone) : `state` est calcule hors thread principal et peut
        // ne pas encore refleter le dernier chapitre coche/decoche.
        val u = ui.value
        if (u.selectedSeriesKey == null || u.selectedChapters.isEmpty()) return
        val translate = u.translateOnArrivalOverride ?: state.value.translateOnArrival
        ui.update { it.copy(phase = NewTranslationPhase.Busy) }
        viewModelScope.launch {
            val targets = u.selectedChapters.mapNotNull { path ->
                val dl = downloadedByPath[path] ?: return@mapNotNull null
                JobTarget(
                    mihonPath = MihonPath(dl.sourceDir, dl.seriesDir, dl.chapterDir),
                    downloaded = dl,
                    series = dl.seriesKey?.let { db.seriesDao().get(it) },
                    chapter = dl.chapterKey?.let { db.chapterDao().get(it) },
                )
            }
            jobRepository.createJobs(mode = JobMode.TRANSLATE, targets = targets, translate = translate)
            ui.update { it.copy(phase = NewTranslationPhase.Done) }
        }
    }

    private fun buildState(a: DbDataA, b: DbDataB, u: UiState): NewTranslationState {
        downloadedByPath = b.downloaded.associateBy { it.mihonPath }
        val localFrPaths = b.localFr.mapNotNull { it.mihonPath }.toSet()
        val indexByPath = b.pcIndex.associateBy { it.mihonPath }
        val latestJobByPath = b.jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { job -> job.createdAt } }
        val categoryNameByOrder = a.categories.associate { it.order to it.name }
        // ponytail: "premiere categorie" = plus petit ordre lie a la serie (pas d'ordre "priorite"
        // dedie dans series_category).
        val firstCategoryOrderBySeries = a.links.groupBy { it.seriesKey }
            .mapValues { (_, ls) -> ls.minOf { it.categoryOrder } }

        val downloadedBySeries = b.downloaded.groupBy { it.seriesKey }
        val seriesList = a.series.mapNotNull { series ->
            val toSend = downloadedBySeries[series.seriesKey].orEmpty().filter { dl ->
                val facts = ChapterFacts(
                    downloaded = true,
                    inCatalog = dl.chapterKey != null,
                    index = indexByPath[dl.mihonPath],
                    latestJob = latestJobByPath[dl.mihonPath],
                    localFr = dl.mihonPath in localFrPaths,
                )
                deriveStatus(facts) == ChapterStatus.TO_SEND
            }
            if (toSend.isEmpty()) return@mapNotNull null
            // ponytail: la langue d'une serie n'est pas persistee en V1 (pas de champ dans
            // SeriesEntity) - le sous-titre ne montre donc que la categorie, contrairement a la
            // maquette qui affiche "statut · langue" (donnees mock uniquement, cf MockupData.kt).
            val subtitle = firstCategoryOrderBySeries[series.seriesKey]?.let { categoryNameByOrder[it] }.orEmpty()
            NewTranslationSeries(
                seriesKey = series.seriesKey,
                title = series.title,
                subtitle = subtitle,
                color = TmmColors.placeholderFor(series.seriesKey),
                chapters = toSend
                    .map { dl -> NewTranslationChapter(mihonPath = dl.mihonPath, number = dl.number ?: 0f, pages = dl.pageCount) }
                    .sortedBy { it.number },
            )
        }.sortedByDescending { it.chapters.size }

        val selectedSeriesKey = seriesList.firstOrNull { it.seriesKey == u.selectedSeriesKey }?.seriesKey
        return NewTranslationState(
            searchQuery = u.searchQuery,
            seriesList = seriesList,
            selectedSeriesKey = selectedSeriesKey,
            selectedChapters = u.selectedChapters,
            translateOnArrival = u.translateOnArrivalOverride ?: a.translateOnArrival,
            phase = u.phase,
        )
    }

    private data class DbDataA(
        val series: List<SeriesEntity>,
        val categories: List<CategoryEntity>,
        val links: List<SeriesCategoryEntity>,
        val translateOnArrival: Boolean,
    )

    private data class DbDataB(
        val downloaded: List<DownloadedChapterEntity>,
        val localFr: List<LocalFrChapterEntity>,
        val jobs: List<JobEntity>,
        val pcIndex: List<PcIndexChapterEntity>,
    )

    private data class UiState(
        val searchQuery: String = "",
        val selectedSeriesKey: String? = null,
        val selectedChapters: Set<String> = emptySet(),
        val translateOnArrivalOverride: Boolean? = null,
        val phase: NewTranslationPhase = NewTranslationPhase.Idle,
    )
}
