package com.tmm.mobile.ui.screens.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.data.status.ignoringProgress
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.ui.theme.TmmColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feuille « Traduire » (maquette v2, écran 6), qui remplace « Nouvelle traduction » en 2 étapes.
 * Liste les séries ayant au moins un chapitre TO_SEND ; les séries se déplient sur place.
 *
 * Sélection : `toggled` contient les chapitres dont l'état diffère du défaut de la demande
 * (tout coché ou rien coché). Un chapitre qui arrive pendant que la feuille est ouverte prend donc
 * le défaut, et la sélection ne dépend pas de l'ordre d'arrivée des données Room.
 */
class TranslateViewModel(private val container: AppContainer) : ViewModel() {
    private val db get() = container.db

    private data class UiState(
        val request: TranslateRequest = TranslateRequest(preselectAll = true),
        val query: String = "",
        val toggled: Set<String> = emptySet(),
        val expanded: Set<String> = emptySet(),
        val phase: TranslatePhase = TranslatePhase.Idle,
    )

    private data class DbData(
        val series: List<SeriesEntity>,
        val downloaded: List<DownloadedChapterEntity>,
        val localFr: List<LocalFrChapterEntity>,
        val jobs: List<JobEntity>,
        val pcIndex: List<PcIndexChapterEntity>,
    )

    private val ui = MutableStateFlow(UiState())

    // ponytail: caches remplacés d'un bloc à chaque émission (thread Default) et lus par les
    // événements ; @Volatile suffit.
    @Volatile private var seriesCache: List<TranslateSeries> = emptyList()
    @Volatile private var downloadedByPath: Map<String, DownloadedChapterEntity> = emptyMap()

    val state = combine(
        combine(
            db.seriesDao().observeAll(),
            db.downloadedChapterDao().observeAll(),
            db.localFrChapterDao().observeAll(),
            db.jobDao().observeAll().ignoringProgress(),
            db.pcIndexChapterDao().observeAll(),
        ) { series, downloaded, localFr, jobs, pcIndex -> DbData(series, downloaded, localFr, jobs, pcIndex) },
        ui,
    ) { data, u -> buildState(data, u) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslateState())

    fun onEvent(event: TranslateEvent) {
        when (event) {
            // Rouvrir pendant un envoi garde la phase Busy : repartir à Idle permettrait un second envoi
            // des mêmes chapitres avant que le premier ait créé ses jobs.
            is TranslateEvent.Open -> ui.update { cur ->
                UiState(request = event.request, phase = if (cur.phase == TranslatePhase.Busy) cur.phase else TranslatePhase.Idle)
            }
            is TranslateEvent.UpdateQuery -> ui.update { it.copy(query = event.query) }
            is TranslateEvent.ToggleExpand -> ui.update { it.copy(expanded = it.expanded.xor(event.seriesKey)) }
            is TranslateEvent.ToggleChapter -> ui.update { it.copy(toggled = it.toggled.xor(event.mihonPath)) }
            is TranslateEvent.ToggleSeries -> ui.update { cur ->
                val series = seriesCache.firstOrNull { it.seriesKey == event.seriesKey } ?: return@update cur
                val paths = series.chapters.map { it.mihonPath }
                val target = !paths.all { isSelected(it, cur) }
                // Coché = défaut XOR basculé : un chapitre est basculé ssi la cible diffère du défaut.
                val toggled = if (target != cur.request.preselectAll) cur.toggled + paths else cur.toggled - paths.toSet()
                cur.copy(toggled = toggled)
            }
            is TranslateEvent.Send -> send(event.translate)
        }
    }

    private fun isSelected(path: String, u: UiState) = (path in u.toggled) != u.request.preselectAll

    private fun send(translate: Boolean) {
        // Sélection lue dans `ui` (synchrone) : `state` est calculé hors thread principal.
        val u = ui.value
        if (u.phase != TranslatePhase.Idle) return
        val paths = seriesCache.flatMap { s -> s.chapters.map { it.mihonPath } }.filter { isSelected(it, u) }
        if (paths.isEmpty()) return
        ui.update { it.copy(phase = TranslatePhase.Busy) }
        viewModelScope.launch {
            val targets = paths.mapNotNull { path ->
                val dl = downloadedByPath[path] ?: return@mapNotNull null
                JobTarget(
                    mihonPath = MihonPath(dl.sourceDir, dl.seriesDir, dl.chapterDir),
                    downloaded = dl,
                    series = dl.seriesKey?.let { db.seriesDao().get(it) },
                    chapter = dl.chapterKey?.let { db.chapterDao().get(it) },
                )
            }
            try {
                val result = container.jobRepository.createJobs(mode = JobMode.TRANSLATE, targets = targets, translate = translate)
                ui.update { it.copy(phase = TranslatePhase.Done(result.createdJobIds.size, translate)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Base indisponible : la feuille reste ouverte avec sa sélection, on peut réessayer.
                AppLog.d("Traduire", "Envoi impossible : ${e.message}")
                ui.update { it.copy(phase = TranslatePhase.Idle) }
            }
        }
    }

    private fun buildState(d: DbData, u: UiState): TranslateState {
        downloadedByPath = d.downloaded.associateBy { it.mihonPath }
        val localFrPaths = d.localFr.mapNotNull { it.mihonPath }.toSet()
        val indexByPath = d.pcIndex.associateBy { it.mihonPath }
        val latestJobByPath = d.jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { it.createdAt } }
        val downloadedBySeries = d.downloaded.groupBy { it.seriesKey }
        val only = u.request.seriesKeys

        val series = d.series
            .filter { only == null || it.seriesKey in only }
            .mapNotNull { s ->
                val toSend = downloadedBySeries[s.seriesKey].orEmpty().filter { dl ->
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
                TranslateSeries(
                    seriesKey = s.seriesKey,
                    title = s.title,
                    coverUrl = s.thumbnailUrl,
                    color = TmmColors.placeholderFor(s.seriesKey),
                    chapters = toSend
                        .map { TranslateChapter(mihonPath = it.mihonPath, number = it.number ?: 0f, pages = it.pageCount) }
                        .sortedBy { it.number },
                )
            }
            .sortedByDescending { it.chapters.size }
        seriesCache = series

        val selected = series.flatMap { s -> s.chapters.map { it.mihonPath } }.filter { isSelected(it, u) }.toSet()
        return TranslateState(query = u.query, series = series, selected = selected, expanded = u.expanded, phase = u.phase)
    }

    private fun <T> Set<T>.xor(item: T): Set<T> = if (item in this) this - item else this + item
}
