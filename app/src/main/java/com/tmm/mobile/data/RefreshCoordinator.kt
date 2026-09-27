package com.tmm.mobile.data

import androidx.work.WorkManager
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.JobTransport
import com.tmm.mobile.transport.PollResult
import com.tmm.mobile.workers.enqueueInstallJob
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class RefreshState(
    val running: Boolean = false,
    val cooldownSeconds: Int = 0,
    val lastCheckedAt: Long? = null,
    val error: RefreshError? = null,
)

enum class RefreshError { NETWORK, DRIVE_QUOTA, AUTH }

// PLAN_GEMINI.md 5.15/CdC 11.3. autoTranslate est injectee (pas reimplementee ici) : Startup (5.16)
// et refresh() appellent la meme etape de traduction automatique sur les memes chapitres TO_SEND.
// ponytail: DriveApi ne distingue pas encore une erreur de quota Drive (403) d'une IOException
// generique -> RefreshError.DRIVE_QUOTA n'est jamais produit pour l'instant ; a brancher si DriveApi
// gagne une exception dediee.
class RefreshCoordinator(
    private val db: TmmDatabase,
    private val prefs: AppPrefs,
    private val transport: JobTransport,
    private val workManager: WorkManager,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val autoTranslate: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = _state.asStateFlow()
    private var cooldownJob: Job? = null

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        if (_state.value.cooldownSeconds > 0) {
            mutex.unlock()
            return
        }
        _state.value = _state.value.copy(running = true, error = null)
        try {
            if (prefs.driveJobsId.first() == null || prefs.drivePcId.first() == null || prefs.driveCommandsId.first() == null) {
                transport.ensureLayout()
            }
            val result = transport.poll(prefs.lastChangesCursor.first())
            applyPoll(result)
            // SettingsSync (E9) confirmera les commandes en attente ici via settings_json/pendingCommandsJson.
            autoTranslate()
            prefs.setLastChangesCursor(result.cursor)
            val now = clock.nowMillis()
            prefs.setLastRefreshAt(now)
            _state.value = _state.value.copy(running = false, lastCheckedAt = now)
        } catch (e: AuthExpiredException) {
            prefs.setAuthExpired(true)
            _state.value = _state.value.copy(running = false, error = RefreshError.AUTH)
        } catch (e: IOException) {
            _state.value = _state.value.copy(running = false, error = RefreshError.NETWORK)
        } finally {
            mutex.unlock()
            startCooldown()
        }
    }

    private suspend fun applyPoll(result: PollResult) {
        if (result.protocolTooNew) prefs.setProtocolTooNew(true)
        val ownJobIds = db.jobDao().observeAll().first().map { it.jobId }.toSet()

        for (status in result.statuses) {
            if (status.jobId !in ownJobIds) continue
            val job = db.jobDao().get(status.jobId) ?: continue
            db.jobDao().upsert(
                job.copy(
                    pcState = status.state,
                    pcStage = status.stage,
                    pcPage = status.page,
                    pcPages = status.pages,
                    pcEngine = status.engine,
                    pcQueuePosition = status.queuePosition,
                    pcErrorCode = status.error?.code,
                    pcErrorMessage = status.error?.message,
                    pcErrorPage = status.error?.page,
                    statusUpdatedAt = status.updatedAt,
                    // PLAN_GEMINI.md 5.4 : phoneState ne doit jamais devenir FAILED depuis SENT/RESULT_READY
                    // (hors table de transitions). Un echec cote PC est deja visible via isFailed()
                    // (pcState == "failed", independant de phoneState) - pas besoin de le dupliquer ici.
                    updatedAt = clock.nowMillis(),
                ),
            )
        }

        for ((resultJson, resultFileId) in result.results) {
            if (resultJson.jobId !in ownJobIds) continue
            val job = db.jobDao().get(resultJson.jobId) ?: continue
            val nextPhoneState = if (resultJson.file == null) "INSTALLED" else "RESULT_READY"
            db.jobDao().upsert(
                job.copy(
                    resultJson = ProtocolJson.encodeToString(ResultJson.serializer(), resultJson),
                    resultFileId = resultFileId,
                    finishedAt = resultJson.finishedAt,
                    phoneState = nextPhoneState,
                    installedAt = if (nextPhoneState == "INSTALLED") clock.nowMillis() else job.installedAt,
                    updatedAt = clock.nowMillis(),
                ),
            )
            resultJson.quality?.let { q ->
                db.chapterQualityDao().upsert(
                    ChapterQualityEntity(
                        mihonPath = job.mihonPath,
                        totalPages = q.totalPages,
                        translatedBubbles = q.translatedBubbles,
                        untranslatedBubbles = q.untranslatedBubbles,
                        sfxKept = q.sfxKept,
                        verifiedAt = q.verifiedAt,
                        alerts = q.alerts,
                        jobId = job.jobId,
                    ),
                )
            }
        }

        result.index?.let { index ->
            db.pcIndexChapterDao().replaceAll(
                index.chapters.map { PcIndexChapterEntity(it.mihonPath.flatten(), it.original, it.fr) },
            )
            prefs.setIndexUpdatedAt(index.updatedAt)
        }
        result.heartbeat?.let { prefs.setHeartbeatJson(ProtocolJson.encodeToString(Heartbeat.serializer(), it)) }
        result.settings?.let { prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), it)) }

        val wifiOnly = prefs.wifiOnly.first()
        db.jobDao().observeAll().first()
            .filter { it.phoneState == "RESULT_READY" }
            .forEach { workManager.enqueueInstallJob(it.jobId, wifiOnly) }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            for (secondsLeft in 30 downTo 1) {
                _state.value = _state.value.copy(cooldownSeconds = secondsLeft)
                delay(1000)
            }
            _state.value = _state.value.copy(cooldownSeconds = 0)
        }
    }
}
