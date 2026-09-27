package com.tmm.mobile.ui.screens.transfers

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Formatters
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
import com.tmm.mobile.data.status.missingChapters
import com.tmm.mobile.mihon.DownloadScanner
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonTree
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel reel de l'ecran Transferts (E10, PLAN_GEMINI.md 7.4, Sync.dc.html).
 * Agrege les DAO comme GlobalCounts.kt (source unique de verite : chapitres telecharges,
 * jobs, fichiers FR locaux, index PC) pour produire les listes/compteurs affiches.
 */
class SyncViewModel(private val container: AppContainer) : ViewModel() {
    private val db = container.db
    private val prefs = container.prefs
    private val jobRepository = container.jobRepository
    private val clock = container.clock

    private val _scanning = MutableStateFlow(false)
    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)

    // ponytail: cible/jobIds recalcules a chaque emission de raw9 et memorises ici pour que
    // SendUp/GetDown n'aient pas a rejouer toute l'agregation - source de verite reste db.*.observeAll().
    @Volatile private var toSendTargets: List<JobTarget> = emptyList()
    @Volatile private var resultReadyJobIds: List<String> = emptyList()

    private data class Raw5(
        val downloaded: List<DownloadedChapterEntity>,
        val jobs: List<JobEntity>,
        val localFr: List<LocalFrChapterEntity>,
        val pcIndex: List<PcIndexChapterEntity>,
        val seriesList: List<SeriesEntity>,
    )

    private data class Raw9(
        val raw5: Raw5,
        val chapters: List<ChapterEntity>,
        val autoTranslate: Boolean,
        val heartbeatJson: String?,
        val lastScanAt: Long,
    )

    private val raw5 = combine(
        db.downloadedChapterDao().observeAll(),
        db.jobDao().observeAll(),
        db.localFrChapterDao().observeAll(),
        db.pcIndexChapterDao().observeAll(),
        db.seriesDao().observeAll(),
    ) { downloaded, jobs, localFr, pcIndex, seriesList -> Raw5(downloaded, jobs, localFr, pcIndex, seriesList) }

    private val raw9 = combine(
        raw5,
        db.chapterDao().observeAll(),
        prefs.translateOnArrival,
        prefs.heartbeatJson,
        prefs.lastScanAt,
    ) { r5, chapters, autoTranslate, heartbeatJson, lastScanAt -> Raw9(r5, chapters, autoTranslate, heartbeatJson, lastScanAt) }

    val state: StateFlow<SyncState> = combine(raw9, _scanning, _sendState, _fetchState) { raw, scanning, sendState, fetchState ->
        buildState(raw, scanning).copy(sendState = sendState, fetchState = fetchState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MockupData.sync)

    fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.ToggleAutoTranslate -> viewModelScope.launch {
                prefs.setTranslateOnArrival(!prefs.translateOnArrival.first())
            }
            SyncEvent.SendUp -> viewModelScope.launch {
                val targets = toSendTargets
                if (targets.isEmpty()) return@launch
                _sendState.value = SendState.Busy(0f)
                val result = jobRepository.createJobs(JobMode.TRANSLATE, targets)
                _sendState.value = SendState.Done(result.createdJobIds.size)
            }
            SyncEvent.GetDown -> viewModelScope.launch {
                val ids = resultReadyJobIds
                if (ids.isEmpty()) return@launch
                _fetchState.value = FetchState.Busy(0, ids.size)
                jobRepository.fetch(ids)
                _fetchState.value = FetchState.Done
            }
            SyncEvent.Rescan -> viewModelScope.launch { rescan() }
        }
    }

    // ponytail: duplique la logique privee de Startup.scanDownloads() (non reutilisable telle
    // quelle depuis ce package) - meme sequence MihonTree/DownloadScanner/setLastScanAt.
    private suspend fun rescan() {
        _scanning.value = true
        try {
            val treeUriString = prefs.mihonTreeUri.first() ?: return
            val treeUri = Uri.parse(treeUriString)
            val fs: MihonFs = MihonTree(container.context.contentResolver, treeUri)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val scanner = DownloadScanner(fs, db)
            fs.findChild(rootDocId, "downloads")?.let { scanner.scanDownloads(it.docId) }
            fs.findChild(rootDocId, "local")?.let { scanner.scanLocal(it.docId) }
            prefs.setLastScanAt(clock.nowMillis())
        } finally {
            _scanning.value = false
        }
    }

    private fun buildState(raw: Raw9, scanning: Boolean): SyncState {
        val now = clock.nowMillis()
        val downloaded = raw.raw5.downloaded
        val jobs = raw.raw5.jobs
        val localFrPaths = raw.raw5.localFr.mapNotNull { it.mihonPath }.toSet()
        val indexByPath = raw.raw5.pcIndex.associateBy { it.mihonPath }
        val seriesByKey = raw.raw5.seriesList.associateBy { it.seriesKey }
        val chapterByKey = raw.chapters.associateBy { it.chapterKey }
        val latestJobByPath = jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { job -> job.createdAt } }
        val downloadedByPath = downloaded.associateBy { it.mihonPath }

        val heartbeat = raw.heartbeatJson?.let {
            runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }.getOrNull()
        }
        val pcName = heartbeat?.pcName ?: "PC"

        fun seriesTitle(seriesKey: String?, fallbackDir: String) = seriesKey?.let { seriesByKey[it]?.title } ?: fallbackDir
        fun seriesColor(seriesKey: String?, fallbackDir: String) = TmmColors.placeholderFor(seriesKey ?: fallbackDir)

        // À envoyer : chapitres telecharges sur le telephone, jamais partis vers le PC.
        val toSendChapters = downloaded.filter { dl ->
            val facts = ChapterFacts(
                downloaded = true,
                inCatalog = dl.chapterKey != null,
                index = indexByPath[dl.mihonPath],
                latestJob = latestJobByPath[dl.mihonPath],
                localFr = dl.mihonPath in localFrPaths,
            )
            deriveStatus(facts) == ChapterStatus.TO_SEND
        }
        toSendTargets = toSendChapters.map { dl ->
            JobTarget(
                mihonPath = MihonPath(dl.sourceDir, dl.seriesDir, dl.chapterDir),
                downloaded = dl,
                series = dl.seriesKey?.let { seriesByKey[it] },
                chapter = dl.chapterKey?.let { chapterByKey[it] },
            )
        }
        val upGroups = toSendChapters.groupBy { it.seriesKey ?: it.seriesDir }
            .toList()
            .sortedByDescending { (_, list) -> list.size }
        val upSeries = upGroups.take(3).map { (_, list) ->
            val sample = list.first()
            SyncSeriesItem(
                title = seriesTitle(sample.seriesKey, sample.seriesDir),
                chapters = Formatters.chapterRangeFr(list.mapNotNull { it.number }),
                color = seriesColor(sample.seriesKey, sample.seriesDir),
                size = Formatters.sizeFr(list.sumOf { it.sizeBytes }),
            )
        }
        val otherUpGroups = upGroups.drop(3)

        // À récupérer : jobs dont le resultat attend sur Drive.
        val resultReadyJobs = latestJobByPath.values.filter { it.phoneState == "RESULT_READY" }
        resultReadyJobIds = resultReadyJobs.map { it.jobId }
        fun jobResultSize(job: JobEntity): Long = job.resultJson?.let {
            runCatching { ProtocolJson.decodeFromString(ResultJson.serializer(), it).file?.size }.getOrNull()
        } ?: 0L
        val downGroups = resultReadyJobs.groupBy { it.seriesKey ?: it.seriesDir }
            .toList()
            .sortedByDescending { (_, list) -> list.size }
        val downSeries = downGroups.take(3).map { (_, list) ->
            val sample = list.first()
            val numbers = list.mapNotNull { downloadedByPath[it.mihonPath]?.number }
            SyncFetchItem(
                title = seriesTitle(sample.seriesKey, sample.seriesDir),
                chapters = Formatters.chapterRangeFr(numbers),
                color = seriesColor(sample.seriesKey, sample.seriesDir),
                size = Formatters.sizeFr(list.sumOf { jobResultSize(it) }),
                isDone = false,
            )
        }

        // Transferts en cours (envoi = SendJobWorker, reception = InstallJobWorker).
        fun liveTitle(job: JobEntity): String {
            val title = seriesTitle(job.seriesKey, job.seriesDir)
            val number = downloadedByPath[job.mihonPath]?.number
            val chapterLabel = number?.let { Formatters.chapterRangeFr(listOf(it)) } ?: job.chapterDir
            return "$title · $chapterLabel"
        }
        fun jobPercent(job: JobEntity): Int =
            if (job.transferTotal > 0) ((job.transferredBytes * 100) / job.transferTotal).toInt() else 0
        val liveTransfers = jobs.filter { it.phoneState == "UPLOADING" }.map { job ->
            LiveTransferItem(liveTitle(job), "envoi ${jobPercent(job)} %", jobPercent(job) / 100f, isUpload = true)
        } + jobs.filter { it.phoneState == "DOWNLOADING" }.map { job ->
            // ponytail: InstallJobWorker n'ecrit pas transferredBytes/transferTotal pendant le
            // telechargement (callback ignore) - ce pourcentage restera a 0% tant que ce gap
            // cote workers/InstallJobWorker.kt n'est pas comble.
            LiveTransferItem(liveTitle(job), "réception ${jobPercent(job)} %", jobPercent(job) / 100f, isUpload = false)
        }

        val scannedAgo = if (raw.lastScanAt > 0) Formatters.relativeFr(raw.lastScanAt, now) else "jamais"

        // Manquants : chapitres catalogue jamais vus ni sur le telephone (telecharge) ni en FR local.
        // ponytail: n'exclut pas les ignored_gap (pas d'observeAll() sur IgnoredGapDao) et ne compte
        // pas comme "present" un chapitre original-only reste uniquement sur le PC (pas de numero de
        // chapitre fiable pour un mihonPath jamais telecharge) - ecart mineur vs la Fiche serie (E10),
        // upgrade : ajouter IgnoredGapDao.observeAll() si ce delta genere du bruit.
        val downloadedChapterKeys = downloaded.mapNotNull { it.chapterKey }.toSet()
        val localFrChapterKeys = raw.raw5.localFr.mapNotNull { lf -> downloadedByPath[lf.mihonPath]?.chapterKey }.toSet()
        var missingCount = 0
        var missingSeriesKey: String? = null
        raw.chapters.groupBy { it.seriesKey }.toSortedMap().forEach { (seriesKey, seriesChapters) ->
            val present = seriesChapters
                .filter { it.chapterKey in downloadedChapterKeys || it.chapterKey in localFrChapterKeys }
                .map { it.number }
            val missing = missingChapters(present, emptySet())
            if (missing.isNotEmpty()) {
                missingCount += missing.size
                if (missingSeriesKey == null) missingSeriesKey = seriesKey
            }
        }

        return SyncState(
            pcName = pcName,
            upCount = toSendChapters.size,
            downCount = resultReadyJobs.size,
            liveTransfers = liveTransfers,
            upSeries = upSeries,
            otherSeriesCount = otherUpGroups.size,
            otherChaptersCount = otherUpGroups.sumOf { it.second.size },
            autoTranslate = raw.autoTranslate,
            downSeries = downSeries,
            scanning = scanning,
            scannedAgo = scannedAgo,
            missingCount = missingCount,
            missingSeriesKey = missingSeriesKey,
        )
    }
}
