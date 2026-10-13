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
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.transport.DriveQuotaException
import com.tmm.mobile.transport.PollResult
import com.tmm.mobile.transport.TransportRouter
import com.tmm.mobile.workers.enqueueInstallJob
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

// 1 : boite aux lettres dans APP/TranslateMyManga. 2 : retour dans App/TranslateMyManga.
private const val DRIVE_LAYOUT_VERSION = 2

// R9 : PC « en ligne » si son dernier heartbeat date de moins de 5 min (heure serveur).
const val PC_ONLINE_MILLIS = 5 * 60_000L

// R8 : aucun status.json 15 min apres l'envoi, ou translating hors activeJobIds depuis 10 min.
const val PC_STALLED_NO_STATUS_MILLIS = 15 * 60_000L
const val PC_STALLED_INACTIVE_MILLIS = 10 * 60_000L
// Annulation demandee, ecrite (ou non) et jamais acquittee : sans reponse du PC apres ce delai, le job est
// clos localement (le chapitre ne reste plus « en file » indefiniment, le resultat tardif sera ignore).
const val CANCEL_UNANSWERED_MILLIS = 15 * 60_000L
// N16 : app ouverte, etat relu toutes les minutes (le PC ecrit son heartbeat toutes les 60 s).
const val FOREGROUND_REFRESH_MILLIS = 60_000L
private val PC_TERMINAL_STATES = setOf("done", "failed", "cancelled")
// heartbeat.activeJobIds ne liste que les jobs en traduction (processing/paused cote PC) : un job
// « fetching » (recuperation de la source, parfois longue) n'y figure jamais et ne doit pas etre juge bloque.
private val PC_WORKING_STATES = setOf("translating")

// PLAN_GEMINI.md 5.15/CdC 11.3. autoTranslate est injectee (pas reimplementee ici) : Startup (5.16)
// et refresh() appellent la meme etape de traduction automatique sur les memes chapitres TO_SEND.
class RefreshCoordinator(
    private val db: TmmDatabase,
    private val prefs: AppPrefs,
    private val router: TransportRouter,
    private val workManager: WorkManager,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val resendStalledDriveJobs: suspend () -> Unit = {},
    // R12 : rapproche les commandes de reglages en attente de chaque settings.json recu.
    private val onSettings: suspend (PcSettings) -> Unit = {},
    private val autoTranslate: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = _state.asStateFlow()
    private var cooldownJob: Job? = null
    private var lastCooldown = DRIVE_COOLDOWN_SECONDS
    // R8 : premier instant (horloge telephone) ou un job fetching/translating est absent d'activeJobIds.
    // ponytail: en memoire seulement, un redemarrage de l'app relance le delai de 10 min ; a persister
    // si des jobs bloques passent inapercus apres des redemarrages frequents.
    private val absentSince = mutableMapOf<String, Long>()

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
        } catch (e: DriveQuotaException) {
            // R5 : 403 storageQuotaExceeded -> bandeau « Google Drive plein ».
            prefs.setDriveFull(true)
            _state.value = _state.value.copy(running = false, error = RefreshError.DRIVE_QUOTA)
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

    /**
     * N16 : refresh() ne tournait qu'a ON_START et sur Actualiser ; app ouverte, « PC vu il y a 6 min »,
     * resultats et annulations restaient figes. Lance sous repeatOnLifecycle(STARTED) : s'arrete en
     * arriere-plan. Un tick pendant le cooldown est ignore par refresh().
     */
    suspend fun refreshWhileForeground(intervalMillis: Long = FOREGROUND_REFRESH_MILLIS) {
        while (true) {
            delay(intervalMillis)
            refresh()
        }
    }

    private suspend fun pollDrive(): PollResult {
        val drive = router.drive
        // Boite aux lettres deplacee (APP/ puis de nouveau App/) : les ids en cache pointent l'ancienne, on les
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
        // Le drapeau retombe des qu'un fichier PC lisible en protocole 1 est vu (PC mis a jour).
        if (result.protocolTooNew) {
            prefs.setProtocolTooNew(true)
        } else if (result.protocolSeen) {
            prefs.setProtocolTooNew(false)
        }
        val ownJobIds = db.jobDao().observeAll().first().map { it.jobId }.toSet()

        for (status in result.statuses) {
            if (status.jobId !in ownJobIds) continue
            val job = db.jobDao().get(status.jobId) ?: continue
            // R11 : etat terminal apres une demande d'annulation -> clos localement comme annule (le
            // chapitre n'est plus bloque par un job actif, rien n'est installe).
            val cancelledLocally = job.phoneState == "CANCEL_REQUESTED" && status.state in PC_TERMINAL_STATES
            db.jobDao().upsert(
                job.copy(
                    pcState = if (cancelledLocally) "cancelled" else status.state,
                    // Un statut nouveau leve le diagnostic « bloque sur le PC » (R8), reevalue plus bas ; le
                    // meme statut relu a chaque poll ne le fait pas clignoter.
                    errorCode = if (job.errorCode == LocalErrorCode.PC_STALLED && status.updatedAt != job.statusUpdatedAt) {
                        null
                    } else {
                        job.errorCode
                    },
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
            // R11 : resultat arrive apres la demande d'annulation -> clos localement, non installe.
            if (job.phoneState == "CANCEL_REQUESTED") {
                if (job.pcState != "cancelled") {
                    db.jobDao().upsert(job.copy(pcState = "cancelled", updatedAt = clock.nowMillis()))
                }
                continue
            }
            // Le meme result.json revient a chaque poll (marge Drive de 120 s, poll direct complet) :
            // seul SENT -> RESULT_READY/INSTALLED est permis (5.4), sinon un job deja installe
            // repasserait RESULT_READY et serait retelecharge (voire FAILED si le dossier est purge).
            if (job.phoneState != "SENT") continue
            val nextPhoneState = if (resultJson.file == null) "INSTALLED" else "RESULT_READY"
            db.jobDao().upsert(
                job.copy(
                    resultJson = ProtocolJson.encodeToString(ResultJson.serializer(), resultJson),
                    resultFileId = resultFileId,
                    // Le resultat est la : le diagnostic « bloque sur le PC » n'a plus lieu d'etre.
                    errorCode = if (job.errorCode == LocalErrorCode.PC_STALLED) null else job.errorCode,
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

        // Un meme refresh peut lire le direct (frais) puis Drive (en retard, voire fige si le PC n'y
        // ecrit plus) : sans ce garde, l'accueil alternait "PC en ligne"/"hors ligne" a chaque refresh.
        result.index?.takeIf { isNotOlder(it.updatedAt, prefs.indexUpdatedAt.first()) }?.let { index ->
            db.pcIndexChapterDao().replaceAll(
                index.chapters.map { PcIndexChapterEntity(it.mihonPath.flatten(), it.original, it.fr) },
            )
            prefs.setIndexUpdatedAt(index.updatedAt)
        }
        result.heartbeat?.let { heartbeat ->
            val known = prefs.heartbeatJson.first()
                ?.let { runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }.getOrNull() }
            if (isNotOlder(heartbeat.updatedAt, known?.updatedAt)) {
                prefs.setHeartbeatJson(ProtocolJson.encodeToString(Heartbeat.serializer(), heartbeat))
                prefs.setHeartbeatSeenAt(heartbeatSeenAt(result))
                // §2 : driveOk == false signale une panne du Drive du PC (lastErrorCode null ou drive_quota,
                // jamais auth) : bandeau neutre, sans toucher au drapeau Drive plein ni a l'auth du telephone.
                heartbeat.driveOk?.let { prefs.setPcDriveDown(!it) }
            }
        }
        val latestHeartbeat = result.heartbeat ?: prefs.heartbeatJson.first()
            ?.let { runCatching { ProtocolJson.decodeFromString(Heartbeat.serializer(), it) }.getOrNull() }
        detectStalledJobs(latestHeartbeat)
        closeUnansweredCancels(latestHeartbeat)
        result.settings?.let { settings ->
            val known = prefs.settingsJson.first()
                ?.let { runCatching { ProtocolJson.decodeFromString(PcSettings.serializer(), it) }.getOrNull() }
            if (isNotOlder(settings.updatedAt, known?.updatedAt)) {
                prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), settings))
                onSettings(settings)
            }
        }

        val wifiOnly = prefs.wifiOnly.first()
        db.jobDao().observeAll().first()
            .filter { it.phoneState == "RESULT_READY" }
            .forEach { workManager.enqueueInstallJob(it.jobId, wifiOnly) }
    }

    // R9 : age du heartbeat mesure cote serveur (en-tete Date - modifiedTime Drive), reporte sur
    // l'horloge du telephone. En direct (ni l'un ni l'autre), l'heure de reception fait foi.
    private fun heartbeatSeenAt(result: PollResult): Long {
        val now = clock.nowMillis()
        val modified = result.heartbeatModifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: return now
        val server = result.serverTime
            ?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
            ?: return now
        return now - (server - modified).coerceAtLeast(0)
    }

    /**
     * R8 : PC en ligne (R9) et job SENT sans nouvelles. Le job garde son etat (SENT -> FAILED est hors
     * table, 5.4) ; seul errorCode = pc_stalled le fait apparaitre en echec avec « Relancer ».
     */
    private suspend fun detectStalledJobs(heartbeat: Heartbeat?) {
        val now = clock.nowMillis()
        val seenAt = prefs.heartbeatSeenAt.first()
        val pcOnline = heartbeat != null && seenAt != null && now - seenAt < PC_ONLINE_MILLIS
        val activeIds = heartbeat?.activeJobIds.orEmpty().toSet()
        val sent = db.jobDao().observeAll().first().filter { it.phoneState == "SENT" }
        absentSince.keys.retainAll(sent.map { it.jobId }.toSet())
        for (job in sent) {
            val working = job.pcState in PC_WORKING_STATES && job.jobId !in activeIds
            if (working) absentSince.getOrPut(job.jobId) { now } else absentSince.remove(job.jobId)
            if (!pcOnline || job.errorCode == LocalErrorCode.PC_STALLED) continue
            val noStatus = job.pcState == null && now - job.updatedAt >= PC_STALLED_NO_STATUS_MILLIS
            val inactive = working && now - (absentSince[job.jobId] ?: now) >= PC_STALLED_INACTIVE_MILLIS
            if (noStatus || inactive) {
                db.jobDao().upsert(job.copy(errorCode = LocalErrorCode.PC_STALLED))
            }
        }
    }

    /**
     * Une annulation que le PC n'acquitte pas (hors ligne, job inconnu, annulation perdue) laissait le
     * job en CANCEL_REQUESTED, affiche « En file » sans fin. Passe le delai, et si le PC n'est pas en train
     * de le traduire, le job est clos localement comme annule ; cancel.json reste a ecrire si l'outbox
     * ne l'a pas encore fait.
     */
    private suspend fun closeUnansweredCancels(heartbeat: Heartbeat?) {
        val now = clock.nowMillis()
        val seenAt = prefs.heartbeatSeenAt.first()
        val pcOnline = heartbeat != null && seenAt != null && now - seenAt < PC_ONLINE_MILLIS
        val activeIds = heartbeat?.activeJobIds.orEmpty().toSet()
        for (job in db.jobDao().observeAll().first()) {
            if (job.phoneState != "CANCEL_REQUESTED" || job.pcState in PC_TERMINAL_STATES) continue
            if (now - (job.cancelWrittenAt ?: job.updatedAt) < CANCEL_UNANSWERED_MILLIS) continue
            if (pcOnline && job.jobId in activeIds) continue
            db.jobDao().upsert(job.copy(pcState = "cancelled", updatedAt = now))
        }
    }

    // Date illisible d'un cote ou de l'autre : on prend la nouvelle valeur (comportement d'avant).
    private fun isNotOlder(candidate: String, known: String?): Boolean {
        if (known == null) return true
        val c = runCatching { Instant.parse(candidate) }.getOrNull() ?: return true
        val k = runCatching { Instant.parse(known) }.getOrNull() ?: return true
        return !c.isBefore(k)
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
