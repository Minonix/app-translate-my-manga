package com.tmm.mobile.ui.screens.main

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.PC_ONLINE_MILLIS
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.data.status.ignoringProgress
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.data.status.translatingPercent
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.ui.theme.TmmColors
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
        val getState: BusyState = BusyState.Idle,
        val getFraction: Float = 0f,
        val installTotal: Int = 0,
        val toast: MainToast? = null,
    )

    private val ephemeral = MutableStateFlow(Ephemeral())
    private var refreshJob: Job? = null

    private data class RoomSnapshot(
        val jobs: List<JobEntity>,
        val downloaded: List<DownloadedChapterEntity>,
        val localFrPaths: Set<String>,
        val pcIndex: List<PcIndexChapterEntity>,
    ) {
        // Calcules une fois par instantane : statusFor les reconstruisait pour chaque chapitre (O(n^2),
        // l'accueil figeait avec ~2000 chapitres telecharges).
        val latestJobByPath by lazy { jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { job -> job.createdAt } } }
        val indexByPath by lazy { pcIndex.associateBy { it.mihonPath } }
    }

    private val roomSnapshot = combine(
        db.jobDao().observeAll(),
        db.downloadedChapterDao().observeAll(),
        db.localFrChapterDao().observeAll(),
        db.pcIndexChapterDao().observeAll(),
    ) { jobs, downloaded, localFr, pcIndex ->
        RoomSnapshot(jobs, downloaded, localFr.mapNotNull { it.mihonPath }.toSet(), pcIndex)
    }

    private data class PrefsSnapshot(val heartbeatJson: String?, val lastRefreshAt: Long, val lastScanAt: Long, val heartbeatSeenAt: Long?)

    private val prefsSnapshot = combine(
        container.prefs.heartbeatJson,
        container.prefs.lastRefreshAt,
        container.prefs.lastScanAt,
        container.prefs.heartbeatSeenAt,
    ) { heartbeatJson, lastRefreshAt, lastScanAt, seenAt -> PrefsSnapshot(heartbeatJson, lastRefreshAt, lastScanAt, seenAt) }

    private val derived = combine(roomSnapshot, prefsSnapshot, db.seriesDao().observeAll()) { room, prefsSnap, series ->
        buildState(room, prefsSnap, series)
    }.flowOn(Dispatchers.Default)

    private val followed = combine(db.seriesDao().observeAll(), db.seriesSettingsDao().observeAutoTranslateSeriesKeys()) { series, keys ->
        val followedKeys = keys.toSet()
        series.filter { it.seriesKey in followedKeys }.sortedBy { it.title }
            .map { MainFollowedSeries(it.seriesKey, it.title) }
    }

    val state: StateFlow<MainState> = combine(derived, ephemeral, followed) { base, eph, followedSeries ->
        base.copy(getState = eph.getState, getFraction = eph.getFraction, toast = eph.toast, installTotal = eph.installTotal, followedSeries = followedSeries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainState(
        pcTone = PcTone.Offline, toSendCount = 0, toSendSeriesCount = 0, toGetCount = 0, toGetSummary = "",
        queueCount = 0, queueParallelCount = 0, queueMachineName = "PC", queueNextCount = 0,
        queueJobs = emptyList(), lastScanLabel = "", recentChapters = emptyList(),
    ))

    fun onEvent(event: MainEvent) {
        when (event) {
            MainEvent.Refresh -> {
                if (refreshJob?.isActive != true) {
                    // Startup scanne Mihon avant runAutoTranslate puis refresh, comme au démarrage.
                    refreshJob = viewModelScope.launch { container.startup.run() }
                }
            }
            // ponytail: RefreshButton anime son spin/anneau localement (pas pilote par
            // RefreshCoordinator.state) - composant partage, hors peripherie de cet ecran.
            MainEvent.RefreshFinished -> {}
            MainEvent.FetchTranslated -> fetchTranslated()
            MainEvent.CopyOriginals -> copyOriginals()
            MainEvent.DismissToast -> ephemeral.update { it.copy(toast = null) }
        }
    }

    // Meme perimetre que le compteur « a envoyer » de l'accueil, envoye avec translate = false : le PC
    // range les originaux sans les traduire (comme « Copier sur le PC sans traduire » d'une serie).
    private fun copyOriginals() {
        viewModelScope.launch {
            val room = roomSnapshot.first()
            val seriesByKey = db.seriesDao().observeAll().first().associateBy { it.seriesKey }
            val targets = room.downloaded
                .filter { dl -> dl.seriesKey in seriesByKey && statusFor(dl, room) == ChapterStatus.TO_SEND }
                .map { dl ->
                    JobTarget(
                        mihonPath = MihonPath(dl.sourceDir, dl.seriesDir, dl.chapterDir),
                        downloaded = dl,
                        series = seriesByKey[dl.seriesKey],
                        chapter = dl.chapterKey?.let { db.chapterDao().get(it) },
                    )
                }
            val result = container.jobRepository.createJobs(JobMode.TRANSLATE, targets, translate = false)
            ephemeral.update { it.copy(toast = MainToast.Copied(result.createdJobIds.size)) }
        }
    }

    private fun fetchTranslated() {
        if (ephemeral.value.getState == BusyState.Busy) return
        ephemeral.update { it.copy(getState = BusyState.Busy, getFraction = 0f) }
        viewModelScope.launch {
            val ids = db.jobDao().observeAll().first().filter { it.phoneState == "RESULT_READY" }.map { it.jobId }
            val total = ids.size
            ephemeral.update { it.copy(installTotal = total) }
            container.jobRepository.fetch(ids)
            if (total == 0) {
                ephemeral.update { it.copy(getState = BusyState.Done, toast = MainToast.Received(0)) }
                return@launch
            }
            // Set des ids et flowOn : l'ancien firstOrNull par id etait O(ids x jobs) sur le thread
            // principal, a chaque ecriture en base pendant toute la recuperation.
            val idSet = ids.toHashSet()
            db.jobDao().observeAll().ignoringProgress()
                .map { all -> all.count { job -> job.jobId in idSet && job.phoneState == "INSTALLED" } }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .takeWhile { it < total }
                .collect { installed -> ephemeral.update { it.copy(getFraction = installed / total.toFloat()) } }
            ephemeral.update { it.copy(getState = BusyState.Done, getFraction = 1f, toast = MainToast.Received(total)) }
        }
    }

    private fun statusFor(dl: DownloadedChapterEntity, room: RoomSnapshot): ChapterStatus {
        return deriveStatus(
            ChapterFacts(
                downloaded = true,
                inCatalog = dl.chapterKey != null,
                index = room.indexByPath[dl.mihonPath],
                latestJob = room.latestJobByPath[dl.mihonPath],
                localFr = dl.mihonPath in room.localFrPaths,
            ),
        )
    }

    private fun buildState(room: RoomSnapshot, prefsSnap: PrefsSnapshot, series: List<SeriesEntity>): MainState {
        val now = container.clock.nowMillis()
        val seriesByKey = series.associateBy { it.seriesKey }
        val downloadedByPath = room.downloaded.associateBy { it.mihonPath }

        // Même périmètre que la feuille Traduire (TranslateViewModel) : un chapitre dont le dossier
        // Mihon ne correspond à aucune série connue n'y est pas proposé, il ne compte donc pas ici.
        val toSendChapters = room.downloaded.filter { dl ->
            dl.seriesKey in seriesByKey && statusFor(dl, room) == ChapterStatus.TO_SEND
        }
        val toSendSeriesCount = toSendChapters.mapNotNull { it.seriesKey }.distinct().size

        val toFetchJobs = room.jobs.filter { it.phoneState == "RESULT_READY" }
        val toGetTitles = toFetchJobs.map { seriesByKey[it.seriesKey]?.title ?: it.seriesDir }.distinct()

        val heartbeat = prefsSnap.heartbeatJson?.let { json ->
            runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), json) }.getOrNull()
        }
        // R9 : dernier signal deduit de l'heure serveur (RefreshCoordinator), pas de updatedAt du PC
        // compare a l'horloge du telephone ; updatedAt seulement a defaut (cache d'avant la mise a jour).
        val heartbeatMillis = heartbeat?.let { hb ->
            prefsSnap.heartbeatSeenAt ?: runCatching { Instant.parse(hb.updatedAt).toEpochMilli() }.getOrNull()
        }
        val pcTone = when {
            heartbeatMillis == null -> PcTone.Offline
            now - heartbeatMillis < PC_ONLINE_MILLIS -> PcTone.Online
            now - heartbeatMillis < 24 * 60 * 60_000L -> PcTone.Seen
            else -> PcTone.Offline
        }
        val pcSeenSinceLabel = if (pcTone == PcTone.Seen) bareDuration(heartbeatMillis!!, now) else null
        val offlineSinceLabel = if (pcTone == PcTone.Offline && heartbeatMillis != null) Formatters.sinceFr(heartbeatMillis, now) else null

        val activeIds = heartbeat?.activeJobIds.orEmpty()
        val jobsById = room.jobs.associateBy { it.jobId }
        val localActiveCount = room.jobs.count { it.isActive() }
        val queueJobs = (activeIds.mapNotNull { jobsById[it] }.filter { it.isActive() } +
            room.jobs.filter { it.isActive() && it.pcState == "running" }).distinctBy { it.jobId }.take(2).map { job -> job.toQueueJob(seriesByKey, downloadedByPath) }

        return MainState(
            pcTone = pcTone,
            pcSeenSinceLabel = pcSeenSinceLabel,
            lastCheckLabel = if (prefsSnap.lastRefreshAt == 0L) null else Formatters.relativeFr(prefsSnap.lastRefreshAt, now),
            offlineSinceLabel = offlineSinceLabel,
            toSendCount = toSendChapters.size,
            toSendSeriesCount = toSendSeriesCount,
            toGetCount = toFetchJobs.size,
            toGetSummary = toGetTitles.joinToString(", "),
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
        val title = (seriesByKey[key]?.title ?: key) + " · ch. " + (downloadedByPath[mihonPath]?.number?.let(::fmtNumber) ?: chapterDir)
        val type = when (mode) {
            JobMode.VERIFY -> MainJobType.Verification
            JobMode.RETRANSLATE -> MainJobType.Retranslation
            else -> MainJobType.Translation
        }
        return MainQueueJob(title = title, color = colorFor(key), type = type, progressFraction = translatingPercent(this) / 100f, page = pcPage, pages = pcPages)
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
                // Maquette v2 : « ch. 140 · dans Mihon », minuscule dans la vignette.
                chapterLabel = Formatters.chapterRangeFr(numbers).replaceFirstChar { it.lowercase() },
                color = colorFor(key),
                inMihon = inMihon,
                onPc = !inMihon,
                seriesKey = key,
            )
        }
    }


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
