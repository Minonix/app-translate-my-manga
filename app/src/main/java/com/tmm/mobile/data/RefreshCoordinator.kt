package com.tmm.mobile.data

import androidx.work.WorkManager
import com.tmm.mobile.core.Clock
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.transport.PollResult
import com.tmm.mobile.transport.TransportRouter
import com.tmm.mobile.workers.enqueueInstallJob
import java.io.IOException
import kotlinx.coroutines.CancellationException
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

private const val DRIVE_COOLDOWN_SECONDS = 30
private const val DIRECT_COOLDOWN_SECONDS = 5

// 1 : boite aux lettres dans APP/TranslateMyManga (au lieu de App/TranslateMyManga).
private const val DRIVE_LAYOUT_VERSION = 1

// PLAN_GEMINI.md 5.15/CdC 11.3. autoTranslate est injectee (pas reimplementee ici) : Startup (5.16)
// et refresh() appellent la meme etape de traduction automatique sur les memes chapitres TO_SEND.
// ponytail: DriveApi ne distingue pas encore une erreur de quota Drive (403) d'une IOException
// generique -> RefreshError.DRIVE_QUOTA n'est jamais produit pour l'instant ; a brancher si DriveApi
// gagne une exception dediee.
class RefreshCoordinator(
    private val db: TmmDatabase,
    private val prefs: AppPrefs,
    private val router: TransportRouter,
    private val workManager: WorkManager,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val resendStalledDriveJobs: suspend () -> Unit = {},
    private val autoTranslate: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = _state.asStateFlow()
    private var cooldownJob: Job? = null
    private var lastCooldown = DRIVE_COOLDOWN_SECONDS

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        if (_state.value.cooldownSeconds > 0) {
            mutex.unlock()
            return
        }
        _state.value = _state.value.copy(running = true, error = null)
        try {
            // Direct d'abord (health 2 s) : il renvoie les statuts de tous les jobs et les resultats des
            // jobs directs. Drive n'est interroge que s'il reste des jobs Drive actifs, ou si le direct
            // est indisponible (repli silencieux, et decouverte de l'URL via pc/remote.json).
            var directOk = false
            if (router.activeChannel() == CHANNEL_DIRECT) {
                directOk = try {
                    applyPoll(router.direct.poll(null))
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // IOException, mais aussi reponse illisible (SerializationException) : repli sur Drive.
                    AppLog.d("RefreshCoordinator", "poll direct impossible, repli sur Drive : ${e.message}")
                    false
                }
            }
            if (directOk) {
                try {
                    resendStalledDriveJobs()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.d("RefreshCoordinator", "renvoi en direct impossible : ${e.javaClass.simpleName} ${e.message}")
                }
            }
            val driveActive = db.jobDao().observeAll().first().any { it.channel == CHANNEL_DRIVE && it.isActive() }
            // Apres un nouveau consentement, seul un poll Drive reussi fait tomber authExpired : sans lui,
            // le bandeau "Reconnecter" restait affiche tant que le direct suffisait.
            val authExpired = prefs.authExpired.first()
            var driveResult: PollResult? = null
            if (!directOk || driveActive || authExpired) driveResult = pollDrive()
            autoTranslate()
            // Le curseur direct n'est pas celui de Drive : seul le poll Drive le fait avancer.
            driveResult?.let { prefs.setLastChangesCursor(it.cursor) }
            lastCooldown = if (directOk) DIRECT_COOLDOWN_SECONDS else DRIVE_COOLDOWN_SECONDS
            val now = clock.nowMillis()
            prefs.setLastRefreshAt(now)
            _state.value = _state.value.copy(running = false, lastCheckedAt = now)
        } catch (e: AuthExpiredException) {
            prefs.setAuthExpired(true)
            _state.value = _state.value.copy(running = false, error = RefreshError.AUTH)
        } catch (e: IOException) {
            _state.value = _state.value.copy(running = false, error = RefreshError.NETWORK)
        } catch (e: CancellationException) {
            _state.value = _state.value.copy(running = false)
            throw e
        } catch (e: Exception) {
            // Echec Play Services hors ligne (ApiException), reponse Drive illisible... : jamais de crash
            // au demarrage ni de spinner fige, on l'affiche comme une erreur reseau.
            AppLog.d("RefreshCoordinator", "refresh en echec : ${e.javaClass.simpleName} ${e.message}")
            _state.value = _state.value.copy(running = false, error = RefreshError.NETWORK)
        } finally {
            mutex.unlock()
            startCooldown()
        }
    }

    private suspend fun pollDrive(): PollResult {
        val drive = router.drive
        // Boite aux lettres deplacee de App/ vers APP/ : les ids en cache pointent l'ancienne, on les
        // recherche une fois. Les dossiers de jobs gardent leur id quand le PC les deplace.
        val layoutOutdated = prefs.driveLayoutVersion.first() < DRIVE_LAYOUT_VERSION
        if (layoutOutdated) prefs.resetDriveLayout()
        if (prefs.driveJobsId.first() == null || prefs.drivePcId.first() == null || prefs.driveCommandsId.first() == null) {
            drive.ensureLayout()
        }
        if (layoutOutdated) prefs.setDriveLayoutVersion(DRIVE_LAYOUT_VERSION)
        val result = drive.poll(prefs.lastChangesCursor.first())
        // Rien ne levait ce drapeau : le bandeau "reconnecter" restait affiche apres un nouveau consentement.
        if (prefs.authExpired.first()) prefs.setAuthExpired(false)
        applyPoll(result)
        // Adresse Tailscale publiee par le PC : une URL non nulle ecrase la precedente, null ne l'efface pas.
        result.remote?.url?.let { prefs.setDirectUrl(it) }
        return result
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
            // Le meme result.json revient a chaque poll (marge Drive de 120 s, poll direct complet) :
            // seul SENT -> RESULT_READY/INSTALLED est permis (5.4), sinon un job deja installe
            // repasserait RESULT_READY et serait retelecharge (voire FAILED si le dossier est purge).
            if (job.phoneState != "SENT") continue
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
            for (secondsLeft in lastCooldown downTo 1) {
                _state.value = _state.value.copy(cooldownSeconds = secondsLeft)
                delay(1000)
            }
            _state.value = _state.value.copy(cooldownSeconds = 0)
        }
    }
}
