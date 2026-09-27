package com.tmm.mobile.ui.screens.main

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.data.status.translatingPercent
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * Ecran Accueil (Main.dc.html, E10, docs/PLAN_GEMINI.md section 7.1). Combine les flux Room et
 * les prefs (heartbeat, dernier rafraichissement/scan) en un MainState pret a afficher ; l'envoi
 * et la recuperation passent par le vrai JobRepository (pas de simulation avec delay()).
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val db = container.db

    // PLAN_GEMINI.md 5.20 : les etats ephemeres (busy/done des deux boutons, toast) ne viennent
    // pas de Room - ils sont pilotes par onEvent et fusionnes avec l'etat derive ci-dessous.
    private data class Ephemeral(
        val sendState: BusyState = BusyState.Idle,
        val getState: BusyState = BusyState.Idle,
        val getFraction: Float = 0f,
        val toast: MainToast? = null,
    )

    private val ephemeral = MutableStateFlow(Ephemeral())

    private data class RoomSnapshot(
        val jobs: List<JobEntity>,
        val downloaded: List<DownloadedChapterEntity>,
        val localFrPaths: Set<String>,
        val pcIndex: List<PcIndexChapterEntity>,
    )

    private val roomSnapshot = combine(
        db.jobDao().observeAll(),
        db.downloadedChapterDao().observeAll(),
        db.localFrChapterDao().observeAll(),
        db.pcIndexChapterDao().observeAll(),
    ) { jobs, downloaded, localFr, pcIndex ->
        RoomSnapshot(jobs, downloaded, localFr.mapNotNull { it.mihonPath }.toSet(), pcIndex)
    }

    private data class PrefsSnapshot(val heartbeatJson: String?, val lastRefreshAt: Long, val lastScanAt: Long)

    private val prefsSnapshot = combine(
        container.prefs.heartbeatJson,
        container.prefs.lastRefreshAt,
        container.prefs.lastScanAt,
    ) { heartbeatJson, lastRefreshAt, lastScanAt -> PrefsSnapshot(heartbeatJson, lastRefreshAt, lastScanAt) }

    private val derived = combine(roomSnapshot, prefsSnapshot, db.seriesDao().observeAll()) { room, prefsSnap, series ->
        buildState(room, prefsSnap, series)
    }

    val state: StateFlow<MainState> = combine(derived, ephemeral) { base, eph ->
        base.copy(sendState = eph.sendState, getState = eph.getState, getFraction = eph.getFraction, toast = eph.toast)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MockupData.main)

    fun onEvent(event: MainEvent) {
        when (event) {
            MainEvent.Refresh -> viewModelScope.launch { container.refreshCoordinator.refresh() }
            // ponytail: RefreshButton anime son spin/anneau localement (pas pilote par
            // RefreshCoordinator.state) - composant partage, hors peripherie de cet ecran.
            MainEvent.RefreshFinished -> {}
            MainEvent.SendPending -> sendPending()
            MainEvent.FetchTranslated -> fetchTranslated()
            MainEvent.DismissToast -> ephemeral.update { it.copy(toast = null) }
        }
    }

    private fun sendPending() {
        if (ephemeral.value.sendState != BusyState.Idle) return
        ephemeral.update { it.copy(sendState = BusyState.Busy) }
        viewModelScope.launch {
            val room = roomSnapshotOnce()
            val seriesByKey = db.seriesDao().observeAll().first().associateBy { it.seriesKey }
            val toSend = room.downloaded.filter { statusFor(it, room) == ChapterStatus.TO_SEND }
            val translateOnArrival = container.prefs.translateOnArrival.first()
            val targets = toSend.map { dl ->
                JobTarget(
                    mihonPath = MihonPath(dl.sourceDir, dl.seriesDir, dl.chapterDir),
                    downloaded = dl,
                    series = dl.seriesKey?.let { seriesByKey[it] },
                    chapter = dl.chapterKey?.let { db.chapterDao().get(it) },
                )
            }
            val result = container.jobRepository.createJobs(JobMode.TRANSLATE, targets, translate = translateOnArrival)
            ephemeral.update { it.copy(sendState = BusyState.Done, toast = MainToast.Sent(result.createdJobIds.size)) }
        }
    }

    private fun fetchTranslated() {
        if (ephemeral.value.getState != BusyState.Idle) return
        ephemeral.update { it.copy(getState = BusyState.Busy, getFraction = 0f) }
        viewModelScope.launch {
            val ids = db.jobDao().observeAll().first().filter { it.phoneState == "RESULT_READY" }.map { it.jobId }
            container.jobRepository.fetch(ids)
            val total = ids.size
            if (total == 0) {
                ephemeral.update { it.copy(getState = BusyState.Done, toast = MainToast.Received(0)) }
                return@launch
            }
            db.jobDao().observeAll()
                .map { all -> ids.count { id -> all.firstOrNull { job -> job.jobId == id }?.phoneState == "INSTALLED" } }
                .takeWhile { it < total }
                .collect { installed -> ephemeral.update { it.copy(getFraction = installed / total.toFloat()) } }
            ephemeral.update { it.copy(getState = BusyState.Done, getFraction = 1f, toast = MainToast.Received(total)) }
        }
    }

    private suspend fun roomSnapshotOnce() = RoomSnapshot(
        jobs = db.jobDao().observeAll().first(),
        downloaded = db.downloadedChapterDao().observeAll().first(),
        localFrPaths = db.localFrChapterDao().observeAll().first().mapNotNull { it.mihonPath }.toSet(),
        pcIndex = db.pcIndexChapterDao().observeAll().first(),
    )

    private fun statusFor(dl: DownloadedChapterEntity, room: RoomSnapshot): ChapterStatus {
        val latestJobByPath = room.jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { job -> job.createdAt } }
        val indexByPath = room.pcIndex.associateBy { it.mihonPath }
        return deriveStatus(
            ChapterFacts(
                downloaded = true,
                inCatalog = dl.chapterKey != null,
                index = indexByPath[dl.mihonPath],
                latestJob = latestJobByPath[dl.mihonPath],
                localFr = dl.mihonPath in room.localFrPaths,
            ),
        )
    }

    private fun buildState(room: RoomSnapshot, prefsSnap: PrefsSnapshot, series: List<SeriesEntity>): MainState {
        val now = container.clock.nowMillis()
        val seriesByKey = series.associateBy { it.seriesKey }
        val downloadedByPath = room.downloaded.associateBy { it.mihonPath }

        val toSendChapters = room.downloaded.filter { dl -> statusFor(dl, room) == ChapterStatus.TO_SEND }
        val toSendSeriesCount = toSendChapters.mapNotNull { it.seriesKey }.distinct().size
        val toSendSizeBytes = toSendChapters.sumOf { it.sizeBytes }

        val toFetchJobs = room.jobs.filter { it.phoneState == "RESULT_READY" }
        val toGetSeriesCount = toFetchJobs.mapNotNull { it.seriesKey }.distinct().size

        val heartbeat = prefsSnap.heartbeatJson?.let { json ->
            runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), json) }.getOrNull()
        }
        val heartbeatMillis = heartbeat?.updatedAt?.let { iso -> runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() }
        val pcTone = when {
            heartbeatMillis == null -> PcTone.Offline
            now - heartbeatMillis < 5 * 60_000L -> PcTone.Online
            now - heartbeatMillis < 24 * 60 * 60_000L -> PcTone.Seen
            else -> PcTone.Offline
        }
        val pcSeenSinceLabel = if (pcTone == PcTone.Seen) bareDuration(heartbeatMillis!!, now) else null
        val offlineSinceLabel = if (pcTone == PcTone.Offline && heartbeatMillis != null) Formatters.sinceFr(heartbeatMillis, now) else null

        val activeIds = heartbeat?.activeJobIds.orEmpty()
        val jobsById = room.jobs.associateBy { it.jobId }
        val localActiveCount = room.jobs.count { it.isActive() }
        val queueJobs = activeIds.mapNotNull { jobsById[it] }.take(4).map { job -> job.toQueueJob(seriesByKey, downloadedByPath) }

        return MainState(
            pcTone = pcTone,
            pcSeenSinceLabel = pcSeenSinceLabel,
            lastCheckLabel = if (prefsSnap.lastRefreshAt == 0L) null else Formatters.relativeFr(prefsSnap.lastRefreshAt, now),
            offlineSinceLabel = offlineSinceLabel,
            toSendCount = toSendChapters.size,
            toSendSummary = seriesSummary(toSendSeriesCount) + " · environ " + Formatters.sizeFr(toSendSizeBytes),
            toGetCount = toFetchJobs.size,
            toGetSummary = seriesSummary(toGetSeriesCount) + " · vers Mihon/local",
            queueCount = heartbeat?.let { it.queue.active + it.queue.waiting } ?: localActiveCount,
            queueParallelCount = heartbeat?.activeJobIds?.size ?: localActiveCount,
            queueMachineName = heartbeat?.pcName ?: "PC",
            queueNextCount = heartbeat?.queue?.waiting ?: 0,
            queueJobs = queueJobs,
            lastScanLabel = if (prefsSnap.lastScanAt == 0L) "Jamais scanné" else "Dernier scan " + Formatters.relativeFr(prefsSnap.lastScanAt, now),
            recentChapters = recentChapters(room.jobs, seriesByKey, downloadedByPath),
        )
    }

    private fun JobEntity.toQueueJob(seriesByKey: Map<String, SeriesEntity>, downloadedByPath: Map<String, DownloadedChapterEntity>): MainQueueJob {
        val key = seriesKey ?: seriesDir
        val title = (seriesByKey[key]?.title ?: key) + " · " + fmtNumber(downloadedByPath[mihonPath]?.number ?: 0f)
        val type = when (mode) {
            JobMode.VERIFY -> MainJobType.Verification
            JobMode.RETRANSLATE -> MainJobType.Retranslation
            else -> MainJobType.Translation
        }
        return MainQueueJob(title = title, color = colorFor(key), type = type, progressFraction = translatingPercent(this) / 100f)
    }

    private fun recentChapters(
        jobs: List<JobEntity>,
        seriesByKey: Map<String, SeriesEntity>,
        downloadedByPath: Map<String, DownloadedChapterEntity>,
    ): List<MainRecentChapter> {
        val doneJobs = jobs
            .filter { it.phoneState == "RESULT_READY" || it.phoneState == "INSTALLED" }
            .sortedByDescending { it.finishedAt.orEmpty() }
            .take(10)
        val grouped = LinkedHashMap<String, MutableList<JobEntity>>()
        for (job in doneJobs) {
            val key = job.seriesKey ?: job.seriesDir
            grouped.getOrPut(key) { mutableListOf() } += job
        }
        return grouped.entries.take(4).map { (key, jobsForSeries) ->
            val numbers = jobsForSeries.map { downloadedByPath[it.mihonPath]?.number ?: 0f }
            val inMihon = jobsForSeries.all { it.phoneState == "INSTALLED" }
            MainRecentChapter(
                title = seriesByKey[key]?.title ?: key,
                chapterLabel = Formatters.chapterRangeFr(numbers),
                color = colorFor(key),
                inMihon = inMihon,
                onPc = !inMihon,
                seriesKey = key,
            )
        }
    }

    private fun seriesSummary(count: Int): String = if (count <= 1) "$count série" else "$count séries"

    // ponytail: relativeFr() prefixe "il y a" ; home_pc_seen_fmt attend la duree seule (%1$s dans
    // "PC vu il y a %1$s", cf mockup_texts.tsv ligne 6) - on retire le prefixe plutot que dupliquer
    // les seuils de Formatters.
    private fun bareDuration(t: Long, now: Long): String = Formatters.relativeFr(t, now).removePrefix("il y a ")

    private fun fmtNumber(n: Float): String = if (n == n.toInt().toFloat()) n.toInt().toString() else n.toString()

    // ponytail: pas de couleur de serie stockee en base - palette fixe indexee par hash stable du
    // seriesKey, seuil connu : deux series peuvent partager la meme teinte (pas de collision-free).
    private val palette = listOf(
        TmmColors.CD8D2E4, TmmColors.CC9D3DE, TmmColors.CE6DCC0, TmmColors.CE2C6BB,
        TmmColors.CCFD8C6, TmmColors.CDCCFC4, TmmColors.CE9E4D8, TmmColors.CECE7DC,
    )

    private fun colorFor(seriesKey: String): Color = palette[Math.floorMod(seriesKey.hashCode(), palette.size)]

    private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
        value = transform(value)
    }
}
