package com.tmm.mobile.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.Formatters
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.db.isDirectChannel
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.status.ErrorCatalog
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.data.status.ignoringProgress
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.data.status.isFailed
import com.tmm.mobile.data.status.isInActivity
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.ui.theme.TmmColors
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de l'écran Activité (maquette v2, écran 7), fusion de File et Transferts.
 * Le heartbeat (activeJobIds/slots) dit quels jobs tournent réellement sur le PC ; les envois et
 * retours en cours viennent de job.phoneState (UPLOADING, DOWNLOADING) ; le reste de la pipeline
 * (« Ensuite ») est dérivé de job.phoneState comme dans ChapterStatus.deriveStatus().
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
        val pcIndex: List<PcIndexChapterEntity>,
        val heartbeatJson: String?,
        val autoSeries: Set<String>,
        val doneClearedAt: Long,
    )

    // combine() type s'arrete a 5 flux : les trois premiers sont regroupes en amont.
    private val raw = combine(
        combine(db.jobDao().observeAll().ignoringProgress(), db.chapterQualityDao().observeAll(), db.seriesDao().observeAll(), ::Triple),
        db.downloadedChapterDao().observeAll(),
        db.pcIndexChapterDao().observeAll(),
        combine(prefs.heartbeatJson, prefs.doneClearedAt, ::Pair),
        db.seriesSettingsDao().observeAutoTranslateSeriesKeys(),
    ) { (jobs, quality, seriesList), downloaded, pcIndex, (heartbeatJson, doneClearedAt), autoSeries ->
        Raw(jobs, quality, seriesList, downloaded, pcIndex, heartbeatJson, autoSeries.toSet(), doneClearedAt ?: 0L)
    }

    private val _ui = MutableStateFlow(QueueUi())

    /** Etat lourd + titrage des transferts, pour ne pas rejouer l'agregation a chaque tick d'envoi. */
    private class Built(val state: QueueState, val titleOf: (JobEntity) -> String)

    // Jobs en cours de transfert, a chaque tick de progression. La partie lourde ignore ces ticks
    // (ignoringProgress) et n'est recalculee que sur un vrai changement.
    private val liveTransfers = db.jobDao().observeAll()
        .map { jobs -> jobs.filter { it.isInActivity() && (it.phoneState == "UPLOADING" || it.phoneState == "DOWNLOADING") } }
        .distinctUntilChanged()

    // Hors thread principal : avec ~2000 jobs, buildState figeait l'ecran a chaque ecriture en base.
    val state: StateFlow<QueueState> = combine(combine(raw, _ui, ::buildState), liveTransfers) { built, live ->
        built.state.copy(
            transfers = live.sortedBy { it.createdAt }.map { job ->
                TransferRow(
                    title = built.titleOf(job),
                    jobId = job.jobId,
                    isUpload = job.phoneState == "UPLOADING",
                    doneBytes = job.transferredBytes,
                    totalBytes = job.transferTotal,
                )
            },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueState())

    fun onEvent(event: QueueEvent) {
        when (event) {
            is QueueEvent.SelectTab -> _ui.update { it.copy(activeTab = event.tab) }
            is QueueEvent.FocusJob -> _ui.update { it.copy(focusedJobIndex = event.index) }
            is QueueEvent.RemoveWaitingJob -> launchSafely { jobRepository.cancel(event.id) }
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
            is QueueEvent.VerifyPage -> launchSafely {
                val mihonPathStr = state.value.doneJobs.getOrNull(event.doneIndex)?.mihonPath ?: return@launchSafely
                val anyJob = db.jobDao().getAllForMihonPath(mihonPathStr).firstOrNull() ?: return@launchSafely
                val mihonPath = MihonPath(anyJob.sourceDir, anyJob.seriesDir, anyJob.chapterDir)
                // event.page est en base 1 (affichage) ; JobRepository.verifyPage attend une page0 base 0.
                jobRepository.verifyPage(mihonPath, event.page - 1)
            }
            is QueueEvent.RetryFailedJob -> launchSafely { jobRepository.retry(event.jobId) }
            // Ids lus a l'instant du clic : JobRepository.cancel ignore un job deja parti plus loin.
            QueueEvent.CancelAllWaiting -> cancelEach(state.value.waitingJobs.map { it.id })
            QueueEvent.CancelAllUploads -> cancelEach(state.value.transfers.filter { it.isUpload }.map { it.jobId })
            QueueEvent.ClearDone -> launchSafely { prefs.setDoneClearedAt(clock.nowMillis()) }
            QueueEvent.ClearFailed -> clearFailedEach(state.value.failedJobs.map { it.jobId })
        }
    }

    // Relu en base job par job (FailedJob ne porte pas phoneState). Un pc_stalled est encore vivant
    // cote PC : on l'annule au lieu de le supprimer. Un echec n'arrete pas les suivants.
    private fun clearFailedEach(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    val job = db.jobDao().get(id) ?: return@forEach
                    when {
                        job.isStalled() -> jobRepository.cancel(id)
                        // R3 : clos comme annule, pas supprime. Sans job, le chapitre redevenait « a envoyer »
                        // et le suivi automatique le renvoyait au PC.
                        job.phoneState == "FAILED" -> {
                            val now = clock.nowMillis()
                            db.jobDao().upsert(
                                job.copy(phoneState = "CANCEL_REQUESTED", pcState = "cancelled", cancelWrittenAt = now, updatedAt = now),
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.d("QueueViewModel", "vidage des echecs en echec : ${e.javaClass.simpleName} ${e.message}")
                }
            }
        }
    }

    // Une a une dans une seule coroutine (des centaines de suppressions Drive en parallele sinon) ;
    // un echec n'arrete pas les suivantes.
    private fun cancelEach(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    jobRepository.cancel(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.d("QueueViewModel", "annulation en echec : ${e.javaClass.simpleName} ${e.message}")
                }
            }
        }
    }

    // R15 : une erreur (base, reseau du best-effort Drive) est journalisee, jamais propagee au scope.
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("QueueViewModel", "action en echec : ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    private fun buildState(r: Raw, ui: QueueUi): Built {
        val now = clock.nowMillis()
        val heartbeat = r.heartbeatJson?.let {
            runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }.getOrNull()
        }
        val pcName = heartbeat?.pcName ?: "PC"
        val slotsMax = heartbeat?.slots ?: 4
        val eligiblePcJobs = r.jobs.filter { it.isInActivity() && it.phoneState in setOf("SENT", "CANCEL_REQUESTED") }
            .mapTo(HashSet()) { it.jobId }
        val activeJobIds = heartbeat?.activeJobIds.orEmpty().filter { it in eligiblePcJobs }.distinct()

        val jobsById = r.jobs.associateBy { it.jobId }
        val seriesByKey = r.seriesList.associateBy { it.seriesKey }
        val downloadedByPath = r.downloaded.associateBy { it.mihonPath }
        val qualityByPath = r.quality.associateBy { it.mihonPath }

        fun seriesKey(job: JobEntity) = job.seriesKey ?: job.seriesDir
        fun seriesTitle(job: JobEntity) = job.seriesKey?.let { seriesByKey[it]?.title } ?: job.seriesDir
        fun coverUrl(job: JobEntity) = job.seriesKey?.let { seriesByKey[it]?.thumbnailUrl }
        fun seriesColor(job: JobEntity) = TmmColors.placeholderFor(seriesKey(job))
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

        // En cours : jobs que le PC traduit reellement en ce moment (heartbeat.activeJobIds, dans l'ordre PC).
        val activeJobs = activeJobIds.mapNotNull { id -> jobsById[id] }.map { job ->
            ActiveJob(
                seriesKey = seriesKey(job),
                coverUrl = coverUrl(job),
                title = jobLabel(job),
                color = seriesColor(job),
                type = jobType(job.mode),
                currentPage = job.pcPage ?: 0,
                totalPages = job.pcPages ?: 0,
                currentStepIndex = stepIndexOf(job.pcStage),
                engine = job.pcEngine ?: "",
            )
        }

        // Ensuite : jobs encore dans la pipeline, ni actifs sur le PC ni en cours d'envoi (ceux-la
        // sont des lignes « Envoi »). Pas les annulations demandees : deja annulees pour l'utilisateur,
        // leur croix ne faisait plus rien (cancel() ignore CANCEL_REQUESTED) et elles restaient
        // jusqu'a la reponse du PC. Le chapitre reste occupe (deriveStatus), rien n'est renvoye (R13).
        val pipelinePhoneStates = setOf("PREPARING", "SENT")
        val waitingJobs = r.jobs
            // pc_stalled (R8) : affiche dans Echecs avec « Relancer », pas dans Ensuite.
            .filter { it.isInActivity() && it.phoneState in pipelinePhoneStates && it.jobId !in activeJobIds && !it.isStalled() }
            .sortedWith(
                compareByDescending<JobEntity> { it.phoneState == "PREPARING" }
                    .thenBy { it.pcQueuePosition ?: Int.MAX_VALUE }
                    .thenBy { it.createdAt },
            )
            .map { job ->
                WaitingJob(
                    id = job.jobId,
                    seriesKey = seriesKey(job),
                    coverUrl = coverUrl(job),
                    title = jobLabel(job),
                    color = seriesColor(job),
                    type = jobType(job.mode),
                    // ponytail: le job ne dit pas qui l'a cree ; on marque « suivi auto » les traductions
                    // des series suivies. Upgrade : une colonne origine sur JobEntity.
                    auto = job.mode == JobMode.TRANSLATE && job.seriesKey in r.autoSeries,
                    handoff = handoffOf(job),
                    pcQueuePosition = job.pcQueuePosition,
                )
            }

        // Terminees (7 derniers jours) : dernier job par mihonPath, pret a installer ou deja installe.
        val latestJobByPath = r.jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { job -> job.createdAt } }
        // Pages en cours de verification par chapitre, calculees une fois (et non un parcours de
        // r.jobs par alerte).
        val verifyingPagesByPath = r.jobs.asSequence()
            .filter { it.mode == JobMode.VERIFY && it.isActive() }
            .groupBy({ it.mihonPath }, { it.pages.orEmpty() })
            .mapValues { (_, pages) -> pages.flatten().toSet() }
        val doneJobs = latestJobByPath.values
            .filter { it.phoneState == "RESULT_READY" || it.phoneState == "INSTALLED" }
            .map { it to jobReferenceMillis(it) }
            .filter { (_, at) -> now - at <= SEVEN_DAYS_MS }
            // Un RESULT_READY n'est pas encore dans Mihon : « Vider » ne le masque jamais.
            .filter { (job, at) -> job.phoneState == "RESULT_READY" || at > r.doneClearedAt }
            .sortedByDescending { (_, at) -> at }
            .map { (job, at) ->
                val quality = qualityByPath[job.mihonPath]
                val alerts = quality?.alerts.orEmpty().map { alert ->
                    val isChecked = verifyingPagesByPath[job.mihonPath]?.contains(alert.page) == true
                    // alert.page est base 0 (protocole) ; affichage attendu base 1 (plan 7.5).
                    DoneJobAlert(page = alert.page + 1, code = alert.kind, isChecked = isChecked)
                }
                DoneJob(
                    mihonPath = job.mihonPath,
                    seriesKey = seriesKey(job),
                    coverUrl = coverUrl(job),
                    title = jobLabel(job),
                    whenText = whenLabel(at, now),
                    installed = job.phoneState == "INSTALLED",
                    color = seriesColor(job),
                    totalPages = quality?.totalPages ?: 0,
                    // ponytail: ChapterQualityEntity n'a pas de compteur "pages traduites" distinct de
                    // totalPages - on suppose la traduction complete faute de champ dedie.
                    translatedPages = quality?.totalPages ?: 0,
                    totalBubbles = quality?.let(::countedBubbles),
                    untranslatedBubbles = quality?.untranslatedBubbles ?: 0,
                    sfxCount = quality?.sfxKept ?: 0,
                    alerts = alerts,
                    isExpanded = job.mihonPath in ui.expandedDoneMihonPaths,
                )
            }

        // Echecs : jobs en echec sans job plus recent sur le meme mihonPath (= pas encore relances).
        // Filtre d'affichage seulement (aucune ligne supprimee) : un echec dont le chapitre n'est ni
        // telecharge ni present en original sur le PC est masque, retry() le refuserait (createJobs).
        // Ce sont surtout les envois de chapitres non telecharges des versions < 1.0.6 (~1800 lignes).
        // Ensembles calcules une fois : pas de requete ni de parcours de r.jobs par echec.
        val pcOriginalPaths = r.pcIndex.asSequence().filter { it.original }.mapTo(HashSet()) { it.mihonPath }
        val failedJobs = r.jobs.filter { it.isFailed() || it.isStalled() }
            .filter { failed -> latestJobByPath.getValue(failed.mihonPath).createdAt <= failed.createdAt }
            .filter { failed -> failed.mihonPath in downloadedByPath || failed.mihonPath in pcOriginalPaths }
            .sortedByDescending { it.updatedAt }
            .map { job ->
                val page = job.pcErrorPage
                val refAt = job.statusUpdatedAt?.let { parseIsoMillis(it) } ?: job.updatedAt
                val origin = job.pcEngine ?: Formatters.relativeFr(refAt, now).replaceFirstChar { it.uppercase() }
                val info = ErrorCatalog.forJob(job)
                // JobRepository.retry ne reprend a la page d'echec qu'hors mode translate.
                val resumeAtPage = page != null && job.mode != JobMode.TRANSLATE
                FailedJob(
                    jobId = job.jobId,
                    seriesKey = seriesKey(job),
                    coverUrl = coverUrl(job),
                    title = jobLabel(job),
                    color = seriesColor(job),
                    page = page,
                    label = info.label,
                    keptInfo = if (page != null && resumeAtPage && page > 1) "$origin · les pages 1 à ${page - 1} sont conservées" else origin,
                    quota = job.pcErrorCode == "provider_quota",
                    detail = job.pcErrorMessage ?: job.errorMessage,
                    action = info.action,
                    resumeAtPage = resumeAtPage,
                )
            }

        val state = QueueState(
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
        return Built(state, ::jobLabel)
    }

    private fun JobEntity.isStalled(): Boolean = phoneState == "SENT" && errorCode == LocalErrorCode.PC_STALLED

    private fun jobReferenceMillis(job: JobEntity): Long =
        job.installedAt ?: job.finishedAt?.let { parseIsoMillis(it) } ?: job.updatedAt

    private fun parseIsoMillis(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

    /** « Il y a 12 min » le jour même, « Hier » la veille, puis « Il y a 3 jours ». */
    private fun whenLabel(millis: Long, now: Long): String {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (day == today.minusDays(1)) "Hier" else Formatters.relativeFr(millis, now).replaceFirstChar { it.uppercase() }
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

        /** Etapes PC fetch, ocr, translate, lettering, verify ramenees a Lecture, Traduction, Lettrage, Verification. */
        fun stepIndexOf(stage: String?): Int = when (stage) {
            "translate" -> 1
            "lettering" -> 2
            "verify" -> 3
            else -> 0
        }
    }
}

/** Etat de la remise au PC, d'apres ce que le telephone sait deja : en direct, la reponse du PC a la
 *  demande vaut accuse de reception ; sur Drive, il faut son premier statut (pcState). */
internal fun handoffOf(job: JobEntity): Handoff = when {
    job.phoneState == "PREPARING" -> if (job.mode == JobMode.TRANSLATE && !job.redeliver) Handoff.Pending else Handoff.RequestSending
    job.pcState != null || isDirectChannel(job.channel) -> Handoff.ReceivedByPc
    else -> Handoff.OnDrive
}

/** Le PC ne remplit les bulles du .tmm.json qu'a la revérification : sans elle, les compteurs
 *  valent 0 et « 0 bulle » laissait croire a une page vide. Null = nombre inconnu (non affiche). */
internal fun countedBubbles(q: ChapterQualityEntity): Int? =
    q.translatedBubbles.takeIf { q.verifiedAt != null || it > 0 || q.untranslatedBubbles > 0 }
