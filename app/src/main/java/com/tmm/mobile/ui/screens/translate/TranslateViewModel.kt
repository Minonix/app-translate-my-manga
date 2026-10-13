package com.tmm.mobile.ui.screens.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.data.status.ignoringProgress
import com.tmm.mobile.mihon.DownloadOnlySeries
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.ui.theme.TmmColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feuille « Traduire » (maquette v2, écran 6), qui remplace « Nouvelle traduction » en 2 étapes.
 * Liste les séries ayant au moins un chapitre TO_SEND ; les séries se déplient sur place.
 *
 * Sélection : `toggled` contient les chapitres dont l'état diffère de leur défaut (rien coché, ou
 * `defaultSelected` si la demande précoche tout). Un chapitre qui arrive pendant que la feuille est
 * ouverte prend donc le défaut, et la sélection ne dépend pas de l'ordre d'arrivée des données Room.
 *
 * Doublons : quand plusieurs scantrads fournissent le même numéro, une seule copie est précochée,
 * celle de la scantrad la plus présente dans la série (classement sur tous les téléchargements, pour
 * rester stable quand des jobs se créent) ; aucune si le numéro a déjà une copie envoyée ou traduite.
 */
class TranslateViewModel(private val container: AppContainer) : ViewModel() {
    private val db get() = container.db

    private data class UiState(
        val request: TranslateRequest = TranslateRequest(preselectAll = true),
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
        val chapters: List<ChapterEntity>,
    )

    private val ui = MutableStateFlow(UiState())

    // Hors de `state` : le champ de recherche doit relire sa valeur tout de suite. Repassée par
    // buildState (thread Default, des milliers de chapitres), chaque frappe recevait l'ancienne
    // valeur et les lettres tapées entre-temps étaient perdues.
    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow.asStateFlow()

    // ponytail: caches remplacés d'un bloc à chaque émission (thread Default) et lus par les
    // événements ; @Volatile suffit.
    @Volatile private var seriesCache: List<TranslateSeries> = emptyList()
    @Volatile private var downloadedByPath: Map<String, DownloadedChapterEntity> = emptyMap()
    @Volatile private var chapterByPath: Map<String, TranslateChapter> = emptyMap()

    val state = combine(
        combine(
            combine(
                db.seriesDao().observeAll(),
                db.downloadedChapterDao().observeAll(),
                db.localFrChapterDao().observeAll(),
                db.jobDao().observeAll().ignoringProgress(),
                db.pcIndexChapterDao().observeAll(),
            ) { series, downloaded, localFr, jobs, pcIndex -> DbData(series, downloaded, localFr, jobs, pcIndex, emptyList()) },
            // Catalogue lu pour la scantrad des chapitres rattachés à une série de la sauvegarde.
            db.chapterDao().observeAll(),
        ) { data, chapters -> data.copy(chapters = chapters) },
        ui,
    ) { data, u -> buildState(data, u) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslateState())

    fun onEvent(event: TranslateEvent) {
        when (event) {
            // Rouvrir pendant un envoi garde la phase Busy : repartir à Idle permettrait un second envoi
            // des mêmes chapitres avant que le premier ait créé ses jobs.
            is TranslateEvent.Open -> {
                queryFlow.value = ""
                ui.update { cur ->
                    UiState(request = event.request, phase = if (cur.phase == TranslatePhase.Busy) cur.phase else TranslatePhase.Idle)
                }
            }
            is TranslateEvent.UpdateQuery -> queryFlow.value = event.query
            is TranslateEvent.ToggleExpand -> ui.update { it.copy(expanded = it.expanded.xor(event.seriesKey)) }
            is TranslateEvent.ToggleChapter -> ui.update { it.copy(toggled = it.toggled.xor(event.mihonPath)) }
            is TranslateEvent.ToggleSeries -> ui.update { cur ->
                val series = seriesCache.firstOrNull { it.seriesKey == event.seriesKey } ?: return@update cur
                val selected = series.chapters.map { it.mihonPath }.filter { isSelected(it, cur) }.toSet()
                val all = TranslateState(series = listOf(series), selected = selected).checkOf(series) == SeriesCheck.All
                // Cocher la série = une copie par numéro (le défaut) ; si tous ses numéros sont déjà
                // traités ailleurs, la meilleure copie de chacun (chapitres triés par numéro puis classement).
                val target = if (all) {
                    emptySet()
                } else {
                    series.chapters.filter { it.defaultSelected }.ifEmpty {
                        series.chapters.distinctBy { if (it.duplicate) it.number.toString() else it.mihonPath }
                    }.map { it.mihonPath }.toSet()
                }
                // Coché = défaut XOR basculé : un chapitre est basculé ssi la cible diffère de son défaut.
                val toggled = series.chapters.fold(cur.toggled) { acc, c ->
                    if ((c.mihonPath in target) != isDefault(c.mihonPath, cur)) acc + c.mihonPath else acc - c.mihonPath
                }
                cur.copy(toggled = toggled)
            }
            is TranslateEvent.Send -> send(event.translate)
        }
    }

    private fun isDefault(path: String, u: UiState) = u.request.preselectAll && chapterByPath[path]?.defaultSelected != false

    private fun isSelected(path: String, u: UiState) = (path in u.toggled) != isDefault(path, u)

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

        val scanlatorByKey = d.chapters.associate { it.chapterKey to it.scanlator }

        val series = d.series
            .filter { only == null || it.seriesKey in only }
            .mapNotNull { s ->
                val all = downloadedBySeries[s.seriesKey].orEmpty()
                val toSend = all.filter { dl ->
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
                    chapters = chaptersOf(all, toSend.map { it.mihonPath }.toSet()) { dl ->
                        dl.chapterKey?.let { scanlatorByKey[it] } ?: DownloadOnlySeries.guessScanlator(dl.chapterDir)
                    },
                )
            }
            .sortedByDescending { it.chapters.size }
        seriesCache = series
        chapterByPath = series.flatMap { it.chapters }.associateBy { it.mihonPath }

        val selected = series.flatMap { s -> s.chapters.map { it.mihonPath } }.filter { isSelected(it, u) }.toSet()
        return TranslateState(series = series, selected = selected, expanded = u.expanded, phase = u.phase)
    }

    /**
     * Chapitres TO_SEND d'une série, triés par numéro puis par scantrad préférée. `all` (tous les
     * téléchargements de la série) sert au classement des scantrads et aux numéros déjà traités.
     */
    private fun chaptersOf(
        all: List<DownloadedChapterEntity>,
        toSendPaths: Set<String>,
        scanlatorOf: (DownloadedChapterEntity) -> String?,
    ): List<TranslateChapter> {
        val scanlators = all.associate { it.mihonPath to scanlatorOf(it) }
        // Plus de chapitres d'abord, puis ordre alphabétique, scantrad inconnue en dernier.
        val ranking = all.groupingBy { scanlators[it.mihonPath] }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String?, Int>> { it.value }.thenBy { it.key == null }.thenBy { it.key })
            .mapIndexed { i, e -> e.key to i }.toMap()
        val copiesByNumber = all.mapNotNull { it.number }.groupingBy { it }.eachCount()
        val handledNumbers = all.filter { it.mihonPath !in toSendPaths }.mapNotNull { it.number }.toSet()
        val chosen = mutableSetOf<Float>()
        return all.filter { it.mihonPath in toSendPaths }
            .sortedWith(compareBy({ it.number ?: 0f }, { ranking[scanlators[it.mihonPath]] }))
            .map { dl ->
                val n = dl.number
                // Sans numéro, impossible de savoir s'il double un autre chapitre : il reste unique.
                val duplicate = n != null && (copiesByNumber[n] ?: 0) > 1
                TranslateChapter(
                    mihonPath = dl.mihonPath,
                    number = n ?: 0f,
                    pages = dl.pageCount,
                    scanlator = scanlators[dl.mihonPath],
                    duplicate = duplicate,
                    defaultSelected = n == null || !duplicate || (n !in handledNumbers && chosen.add(n)),
                )
            }
    }

    private fun <T> Set<T>.xor(item: T): Set<T> = if (item in this) this - item else this + item
}
