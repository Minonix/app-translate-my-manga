package com.tmm.mobile.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.data.status.isFailed
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel reel de l'ecran File (E10, PLAN_GEMINI.md 7.5, Queue.dc.html).
 * Le heartbeat (activeJobIds/slots) dit quels jobs tournent reellement sur le PC ; le reste de la
 * pipeline ("Ensuite") est derive de job.phoneState comme dans ChapterStatus.deriveStatus().
 */
class QueueViewModel(private val container: AppContainer) : ViewModel() {
    private val db = container.db
    private val prefs = container.prefs
    private val jobRepository = container.jobRepository
    private val clock = container.clock

    private data class QueueUi(
        val activeTab: QueueTab = QueueTab.Active,
        val focusedJobIndex: Int = 0,
        val expandedDoneMihonPaths: Set<String> = emptySet(),
    )

    private data class Raw(
        val jobs: List<JobEntity>,
        val quality: List<ChapterQualityEntity>,
        val seriesList: List<SeriesEntity>,
        val downloaded: List<DownloadedChapterEntity>,
        val heartbeatJson: String?,
    )

    private val raw = combine(
        db.jobDao().observeAll(),
        db.chapterQualityDao().observeAll(),
        db.seriesDao().observeAll(),
        db.downloadedChapterDao().observeAll(),
        prefs.heartbeatJson,
    ) { jobs, quality, seriesList, downloaded, heartbeatJson -> Raw(jobs, quality, seriesList, downloaded, heartbeatJson) }

    private val _ui = MutableStateFlow(QueueUi())

    val state: StateFlow<QueueState> = combine(raw, _ui) { r, ui -> buildState(r, ui) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MockupData.queue)

    fun onEvent(event: QueueEvent) {
        when (event) {
            is QueueEvent.SelectTab -> _ui.update { it.copy(activeTab = event.tab) }
            is QueueEvent.FocusJob -> _ui.update { it.copy(focusedJobIndex = event.index) }
            is QueueEvent.RemoveWaitingJob -> viewModelScope.launch { jobRepository.cancel(event.id) }
            is QueueEvent.ToggleDoneJobDetails -> {
                val mihonPath = state.value.doneJobs.getOrNull(event.index)?.mihonPath ?: return
                _ui.update {
                    it.copy(
                        expandedDoneMihonPaths = if (mihonPath in it.expandedDoneMihonPaths) {
                            it.expandedDoneMihonPaths - mihonPath
                        } else {
                            it.expandedDoneMihonPaths + mihonPath
                        },
                    )
                }
            }
            is QueueEvent.VerifyPage -> viewModelScope.launch {
                val mihonPathStr = state.value.doneJobs.getOrNull(event.doneIndex)?.mihonPath ?: return@launch
                val anyJob = db.jobDao().getAllForMihonPath(mihonPathStr).firstOrNull() ?: return@launch
                val mihonPath = MihonPath(anyJob.sourceDir, anyJob.seriesDir, anyJob.chapterDir)
                // event.page est en base 1 (affichage) ; JobRepository.verifyPage attend une page0 base 0.
                jobRepository.verifyPage(mihonPath, event.page - 1)
            }
            is QueueEvent.RetryFailedJob -> viewModelScope.launch { jobRepository.retry(event.jobId) }
        }
    }

    private fun buildState(r: Raw, ui: QueueUi): QueueState {
        val now = clock.nowMillis()
        val heartbeat = r.heartbeatJson?.let {
            runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }.getOrNull()
        }
        val pcName = heartbeat?.pcName ?: "PC"
        val slotsMax = heartbeat?.slots ?: 4
        val activeJobIds = heartbeat?.activeJobIds ?: emptyList()

        val jobsById = r.jobs.associateBy { it.jobId }
        val seriesByKey = r.seriesList.associateBy { it.seriesKey }
        val downloadedByPath = r.downloaded.associateBy { it.mihonPath }
        val qualityByPath = r.quality.associateBy { it.mihonPath }

        fun seriesTitle(job: JobEntity) = job.seriesKey?.let { seriesByKey[it]?.title } ?: job.seriesDir
        fun seriesColor(job: JobEntity) = TmmColors.placeholderFor(job.seriesKey ?: job.seriesDir)
        fun chapterLabel(job: JobEntity): String {
            val number = downloadedByPath[job.mihonPath]?.number
            return number?.let { Formatters.chapterRangeFr(listOf(it)) } ?: job.chapterDir
        }
        fun jobLabel(job: JobEntity) = "${seriesTitle(job)} · ${chapterLabel(job)}"
        fun jobType(mode: String) = when (mode) {
            JobMode.VERIFY -> QueueJobType.Verification
            JobMode.RETRANSLATE -> QueueJobType.Retranslation
            else -> QueueJobType.Translation
        }

        // Actives : jobs que le PC traduit reellement en ce moment (heartbeat.activeJobIds, dans l'ordre PC).
        val activeJobs = activeJobIds.mapNotNull { id -> jobsById[id] }.map { job ->
            ActiveJob(
                title = jobLabel(job),
                color = seriesColor(job),
                type = jobType(job.mode),
                currentPage = job.pcPage ?: 0,
                totalPages = job.pcPages ?: 0,
                currentStepIndex = STEP_NAMES.indexOf(job.pcStage).coerceAtLeast(0),
                // ponytail: pas de source fiable pour "N bulles a revoir" (verification) hors du
                // moteur pcEngine - upgrade si le protocole PC expose un resume de verification.
                extra = job.pcEngine ?: "",
            )
        }

        // Ensuite : jobs encore dans la pipeline de traduction, pas actifs sur le PC actuellement.
        val pipelinePhoneStates = setOf("PREPARING", "UPLOADING", "SENT", "CANCEL_REQUESTED")
        val waitingCandidates = r.jobs.filter { it.phoneState in pipelinePhoneStates && it.jobId !in activeJobIds }
        val sortedWaiting = waitingCandidates.sortedWith(
            compareByDescending<JobEntity> { it.phoneState == "PREPARING" || it.phoneState == "UPLOADING" }
                .thenBy { it.pcQueuePosition ?: Int.MAX_VALUE }
                .thenBy { it.createdAt },
        )
        val waitingJobs = sortedWaiting.mapIndexed { index, job ->
            val isPhoneSource = job.phoneState == "PREPARING" || job.phoneState == "UPLOADING"
            WaitingJob(
                id = job.jobId,
                position = String.format(Locale.FRANCE, "%02d", activeJobIds.size + index + 1),
                title = jobLabel(job),
                color = seriesColor(job),
                source = "${jobType(job.mode).label} · " + if (isPhoneSource) "depuis le téléphone" else "original déjà sur le PC",
                isPhone = isPhoneSource,
            )
        }

        // Terminees (7 derniers jours) : dernier job par mihonPath, pret a recuperer ou deja installe.
        val latestJobByPath = r.jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { job -> job.createdAt } }
        val doneJobs = latestJobByPath.values
            .filter { it.phoneState == "RESULT_READY" || it.phoneState == "INSTALLED" }
            .map { it to jobReferenceMillis(it) }
            .filter { (_, at) -> now - at <= SEVEN_DAYS_MS }
            .sortedByDescending { (_, at) -> at }
            .map { (job, at) ->
                val quality = qualityByPath[job.mihonPath]
                val alerts = quality?.alerts.orEmpty().map { alert ->
                    val isChecked = r.jobs.any { active ->
                        active.mihonPath == job.mihonPath && active.mode == JobMode.VERIFY &&
                            active.isActive() && active.pages?.contains(alert.page) == true
                    }
                    // alert.page est base 0 (protocole) ; affichage attendu base 1 (plan 7.5).
                    DoneJobAlert(page = alert.page + 1, code = alert.kind, isChecked = isChecked)
                }
                DoneJob(
                    mihonPath = job.mihonPath,
                    title = jobLabel(job),
                    whenText = whenLabel(at, now),
                    stateText = if (job.phoneState == "INSTALLED") "Dans Mihon" else "À récupérer",
                    stateColor = if (job.phoneState == "INSTALLED") TmmColors.success else TmmColors.info,
                    color = seriesColor(job),
                    totalPages = quality?.totalPages ?: 0,
                    // ponytail: ChapterQualityEntity n'a pas de compteur "pages traduites" distinct de
                    // totalPages - on suppose la traduction complete faute de champ dedie (cf rapport).
                    translatedPages = quality?.totalPages ?: 0,
                    totalBubbles = quality?.translatedBubbles ?: 0,
                    untranslatedBubbles = quality?.untranslatedBubbles ?: 0,
                    sfxCount = quality?.sfxKept ?: 0,
                    alerts = alerts,
                    isExpanded = job.mihonPath in ui.expandedDoneMihonPaths,
                )
            }

        // Echecs : jobs en echec sans job plus recent sur le meme mihonPath (= pas encore relances).
        val failedJobs = r.jobs.filter { it.isFailed() }
            .filterNot { failed -> r.jobs.any { it.mihonPath == failed.mihonPath && it.createdAt > failed.createdAt } }
            .sortedByDescending { it.updatedAt }
            .map { job ->
                val page = job.pcErrorPage
                val refAt = job.statusUpdatedAt?.let { parseIsoMillis(it) } ?: job.updatedAt
                val relative = Formatters.relativeFr(refAt, now)
                FailedJob(
                    jobId = job.jobId,
                    title = jobLabel(job),
                    color = seriesColor(job),
                    page = page,
                    message = job.pcErrorMessage ?: job.errorMessage ?: "erreur inconnue",
                    keptInfo = if (page != null && page > 1) "$relative · les pages 1 à ${page - 1} sont conservées" else relative,
                )
            }

        return QueueState(
            activeTab = ui.activeTab,
            slotsCount = activeJobIds.size,
            slotsMax = slotsMax,
            pcName = pcName,
            focusedJobIndex = ui.focusedJobIndex.coerceIn(0, (activeJobs.size - 1).coerceAtLeast(0)),
            activeJobs = activeJobs,
            waitingJobs = waitingJobs,
            doneJobs = doneJobs,
            failedJobs = failedJobs,
        )
    }

    private fun jobReferenceMillis(job: JobEntity): Long =
        job.installedAt ?: job.finishedAt?.let { parseIsoMillis(it) } ?: job.updatedAt

    private fun parseIsoMillis(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

    private fun whenLabel(millis: Long, now: Long): String {
        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        val nowDt = Instant.ofEpochMilli(now).atZone(zone)
        val time = String.format(Locale.FRANCE, "%02d:%02d", dt.hour, dt.minute)
        return when {
            dt.toLocalDate() == nowDt.toLocalDate() -> "Aujourd’hui $time"
            dt.toLocalDate() == nowDt.toLocalDate().minusDays(1) -> "Hier $time"
            else -> String.format(Locale.FRANCE, "%02d/%02d %s", dt.dayOfMonth, dt.monthValue, time)
        }
    }

    private companion object {
        val STEP_NAMES = listOf("fetch", "ocr", "translate", "lettering", "verify")
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
