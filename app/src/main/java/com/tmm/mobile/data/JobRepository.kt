package com.tmm.mobile.data

import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.transport.TransportRouter
import com.tmm.mobile.workers.enqueueInstallJob
import com.tmm.mobile.workers.enqueueOutbox
import com.tmm.mobile.workers.enqueueSendJob
import com.tmm.mobile.workers.sendWorkName
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object JobMode {
    const val TRANSLATE = "translate"
    const val VERIFY = "verify"
    const val RETRANSLATE = "retranslate"
}

data class JobTarget(
    val mihonPath: MihonPath,
    val downloaded: DownloadedChapterEntity?,
    val series: SeriesEntity?,
    val chapter: ChapterEntity?,
)

data class CreateJobsResult(val createdJobIds: List<String>, val refused: Int)

// Delai avant de considerer qu'un job Drive SENT n'a jamais ete vu par le PC (aucun status.json) :
// laisse au PC le temps d'un cycle de lecture Drive avant de renvoyer le chapitre en direct.
const val STALLED_SENT_MILLIS = 10 * 60 * 1000L

// 1 : remise a plat de la v1.3.0 (traductions lancees sans demande par les versions precedentes).
private const val JOB_RESET_VERSION = 1
private val FINISHED_PHONE_STATES = setOf("RESULT_READY", "DOWNLOADING", "INSTALLED")
// Codes locaux dont l'action est « redemander au PC » (docs/ERREURS_PROTOCOLE.md 1.2).
private val REDELIVER_LOCAL_CODES = setOf(LocalErrorCode.DOWNLOAD, LocalErrorCode.RESULT_MISSING)
private val RECEPTION_STEPS = setOf("download", "install")

// PLAN_GEMINI.md 5.17. Le verrou est global (pas par mihonPath) : createJobs() n'est appele qu'en
// rafales courtes (bouton UI ou auto-translate au demarrage), jamais a haute frequence.
class JobRepository(
    private val db: TmmDatabase,
    private val prefs: AppPrefs,
    private val router: TransportRouter,
    private val workManager: WorkManager,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    // Envois Drive deja relances dans ce processus : RefreshCoordinator appelle toutes les ~5 s, un
    // REPLACE a chaque fois couperait sans cesse le worker en cours.
    private val relaunchedSends = mutableSetOf<String>()

    suspend fun createJobs(
        mode: String,
        targets: List<JobTarget>,
        pages: List<Int>? = null,
        translate: Boolean = true,
        redeliver: Boolean = false,
    ): CreateJobsResult {
        val createdIds = mutableListOf<String>()
        var refused = 0
        // R2 : numeros (serie, numero) dont une edition part en traduction dans ce lot.
        val translatingNumbers = HashSet<Pair<String, Float>>()
        // Un seul verrou et une seule transaction pour tout le lot : envoyer 2000 chapitres declenchait
        // 2000 invalidations Room, et l'accueil se recalculait a chacune.
        mutex.withLock {
            db.withTransaction {
                for (target in targets) {
                    val jobId = run {
                        val flatPath = target.mihonPath.flatten()
                        val hasActive = db.jobDao().getAllForMihonPath(flatPath).any { it.isActive() }
                        // R2 : jamais deux editions du meme numero traduites en meme temps. Un simple
                        // envoi (translate = false) n'est pas une traduction : il reste libre.
                        val number = (target.downloaded?.number ?: target.chapter?.number)?.takeIf { it >= 0f }
                        val seriesKey = target.series?.seriesKey ?: target.downloaded?.seriesKey
                        val edition = if (translate && number != null && seriesKey != null) seriesKey to number else null
                        val otherEditionBusy = edition != null && (
                            edition in translatingNumbers ||
                                db.downloadedChapterDao().getForNumber(edition.first, edition.second).any { other ->
                                    other.mihonPath != flatPath &&
                                        db.jobDao().getAllForMihonPath(other.mihonPath).any { it.isActive() && it.translate }
                                }
                            )
                        val indexEntry = db.pcIndexChapterDao().get(flatPath)
                        val refuse = if (redeliver) {
                            // Redemande sans fichier (docs/ERREURS_PROTOCOLE.md 2) : rien a lire localement.
                            hasActive
                        } else if (mode == JobMode.TRANSLATE) {
                            // Sans chapitre telecharge, SendJobWorker.prepareCbz echouerait ("chapitre
                            // telecharge introuvable"). Lu en base et non dans target.downloaded : Serie et
                            // Bibliotheque passent null pour les lignes catalogue, et c'est cette meme
                            // ligne que le worker relira.
                            hasActive || indexEntry?.original == true || db.downloadedChapterDao().get(flatPath) == null
                        } else {
                            hasActive || indexEntry?.original != true
                        }
                        if (refuse || otherEditionBusy) {
                            refused++
                            return@run null
                        }
                        if (edition != null) translatingNumbers += edition
                        val id = UUID.randomUUID().toString()
                        val now = clock.nowMillis()
                        db.jobDao().upsert(
                            JobEntity(
                                jobId = id,
                                mode = mode,
                                pages = pages,
                                translate = translate,
                                mihonPath = flatPath,
                                sourceDir = target.mihonPath.source,
                                seriesDir = target.mihonPath.series,
                                chapterDir = target.mihonPath.chapter,
                                seriesKey = target.series?.seriesKey,
                                chapterKey = target.chapter?.chapterKey,
                                phoneState = "PREPARING",
                                failedStep = null,
                                errorMessage = null,
                                driveFolderId = null,
                                uploadSessionUrl = null,
                                cachePath = null,
                                sha256 = null,
                                sizeBytes = null,
                                transferredBytes = 0L,
                                transferTotal = 0L,
                                pcState = null,
                                pcStage = null,
                                pcPage = null,
                                pcPages = null,
                                pcEngine = null,
                                pcQueuePosition = null,
                                pcErrorCode = null,
                                pcErrorMessage = null,
                                pcErrorPage = null,
                                statusUpdatedAt = null,
                                resultJson = null,
                                resultFileId = null,
                                createdAt = now,
                                updatedAt = now,
                                finishedAt = null,
                                installedAt = null,
                                installedPath = null,
                                receivedWrittenAt = null,
                                cancelWrittenAt = null,
                                driveDeletedAt = null,
                                redeliver = redeliver,
                            ),
                        )
                        id
                    }
                    if (jobId != null) createdIds += jobId
                }
            }
        }
        if (createdIds.isNotEmpty()) {
            val wifiOnly = prefs.wifiOnly.first()
            createdIds.forEach { workManager.enqueueSendJob(it, wifiOnly) }
        }
        return CreateJobsResult(createdIds, refused)
    }

    suspend fun fetch(jobIds: List<String>) {
        val wifiOnly = prefs.wifiOnly.first()
        db.jobDao().getAll(jobIds)
            .filter { it.phoneState == "RESULT_READY" }
            .forEach { workManager.enqueueInstallJob(it.jobId, wifiOnly) }
    }

    suspend fun cancel(jobId: String) {
        val job = db.jobDao().get(jobId) ?: return
        when (job.phoneState) {
            "PREPARING", "UPLOADING" -> {
                workManager.cancelUniqueWork(sendWorkName(jobId))
                // Best-effort : hors ligne, le dossier Drive reste orphelin mais l'annulation locale
                // aboutit (une IOException ici faisait planter l'app depuis l'ecran File).
                if (job.driveFolderId != null) {
                    try {
                        router.forJob(job).deleteJobFolder(job)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.d("JobRepository", "suppression du dossier du job impossible : ${e.message}")
                    }
                }
                db.jobDao().delete(job)
            }
            "SENT" -> {
                db.jobDao().upsert(job.copy(phoneState = "CANCEL_REQUESTED", updatedAt = clock.nowMillis()))
                workManager.enqueueOutbox(prefs.wifiOnly.first())
            }
        }
    }

    /**
     * Appele quand le PC repond en direct : les jobs restes sur Drive sans reponse du PC y passent.
     * - PREPARING/UPLOADING : le worker est relance (REPLACE, sinon il attendrait son backoff) et
     *   bascule lui-meme en direct, request.json n'ayant pas encore ete ecrit.
     * - SENT sans aucun statut PC depuis STALLED_SENT_MILLIS : SENT -> PREPARING est interdit, le job
     *   est donc supprime (dossier Drive compris, en best-effort, pour que le PC ne le traite pas une
     *   seconde fois) puis recree, comme retry().
     */
    /**
     * Remise a plat unique : plus rien en file ni en cours, rien ne se traduit sans demande. Les jobs
     * termines (resultat pret, telechargement, installe) sont gardes ; les autres sont oublies cote
     * telephone sans toucher a leur dossier Drive (le PC les range en originaux non traduits).
     */
    suspend fun resetPendingJobsOnce() {
        if (prefs.jobResetVersion.first() >= JOB_RESET_VERSION) return
        db.seriesSettingsDao().clearAutoTranslate()
        // Une seule requete DELETE : supprimer job par job relancait la lecture de toute la table a
        // chaque ligne, et faisait planter les lectures concurrentes sur une grosse file.
        val states = FINISHED_PHONE_STATES.toList()
        val pendingIds = db.jobDao().jobIdsNotIn(states)
        pendingIds.forEach { workManager.cancelUniqueWork(sendWorkName(it)) }
        val deleted = db.jobDao().deleteNotIn(states)
        prefs.setJobResetVersion(JOB_RESET_VERSION)
        AppLog.d("JobRepository", "remise a plat : $deleted job(s) non termine(s) oublie(s)")
    }

    suspend fun resendStalledDriveJobs() {
        val driveJobs = db.jobDao().observeAll().first().filter { it.channel == CHANNEL_DRIVE }
        val wifiOnly = prefs.wifiOnly.first()
        driveJobs
            .filter { (it.phoneState == "PREPARING" || it.phoneState == "UPLOADING") && relaunchedSends.add(it.jobId) }
            .forEach { workManager.enqueueSendJob(it.jobId, wifiOnly, ExistingWorkPolicy.REPLACE) }

        val now = clock.nowMillis()
        // Un job que recreate() ne pourrait pas recreer (chapitre ni telecharge ni en original sur le
        // PC, refuse par createJobs) reste tel quel sur Drive plutot que d'etre supprime sans remplacant.
        val unseen = driveJobs.filter {
            it.phoneState == "SENT" && it.pcState == null && now - it.updatedAt >= STALLED_SENT_MILLIS &&
                (db.downloadedChapterDao().get(it.mihonPath) != null || db.pcIndexChapterDao().get(it.mihonPath)?.original == true)
        }
        var resent = 0
        for (job in unseen) {
            // Dossier non supprime (hors ligne...) : le PC pourrait encore le traiter, on ne cree pas de
            // doublon ; nouvel essai au prochain rafraichissement.
            if (!deleteFolder(job)) continue
            db.jobDao().delete(job)
            recreate(job)
            resent++
        }
        if (resent > 0) AppLog.d("JobRepository", "$resent job(s) Drive sans reponse renvoye(s) en direct")
    }

    /**
     * Relance d'un job en echec selon l'etape et le code (docs/ERREURS_PROTOCOLE.md 1) :
     * - echec de reception local (install, reseau, place, acces) avec resultat connu : reprise sur le meme
     *   job (FAILED -> RESULT_READY), le CBZ en cache n'est pas retelecharge (R10) ;
     * - download / result_missing / drive_quota du PC : redemande sans fichier (options.redeliver) ;
     * - pc_stalled (R8) : dossier supprime puis job recree, seulement si la suppression a reussi ;
     * - sinon : nouveau job (renvoi complet).
     */
    suspend fun retry(jobId: String) {
        val job = db.jobDao().get(jobId) ?: return
        when {
            job.phoneState == "FAILED" && job.resultJson != null && job.resultFileId != null &&
                job.errorCode !in REDELIVER_LOCAL_CODES && job.failedStep in RECEPTION_STEPS -> {
                db.jobDao().upsert(
                    job.copy(
                        phoneState = "RESULT_READY", failedStep = null, errorCode = null, errorMessage = null,
                        updatedAt = clock.nowMillis(),
                    ),
                )
                workManager.enqueueInstallJob(job.jobId, prefs.wifiOnly.first())
            }
            job.errorCode in REDELIVER_LOCAL_CODES || (job.pcState == "failed" && job.pcErrorCode == "drive_quota") ->
                createJobs(JobMode.TRANSLATE, listOf(targetOf(job)), redeliver = true)
            job.errorCode == LocalErrorCode.PC_STALLED -> {
                if (!deleteFolder(job)) return
                db.jobDao().delete(job)
                recreate(job)
            }
            else -> recreate(job)
        }
    }

    // Suppression best-effort du dossier du job ; false si elle a echoue (le PC peut encore le voir).
    private suspend fun deleteFolder(job: JobEntity): Boolean = try {
        router.forJob(job).deleteJobFolder(job)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.d("JobRepository", "dossier du job non supprime : ${e.message}")
        false
    }

    private suspend fun targetOf(old: JobEntity): JobTarget = JobTarget(
        mihonPath = MihonPath(old.sourceDir, old.seriesDir, old.chapterDir),
        downloaded = db.downloadedChapterDao().get(old.mihonPath),
        series = old.seriesKey?.let { db.seriesDao().get(it) },
        chapter = old.chapterKey?.let { db.chapterDao().get(it) },
    )

    private suspend fun recreate(old: JobEntity) {
        val flatPath = old.mihonPath
        val indexEntry = db.pcIndexChapterDao().get(flatPath)
        val newMode = if (old.mode == JobMode.TRANSLATE && indexEntry?.original == true) {
            JobMode.RETRANSLATE
        } else {
            old.mode
        }
        val target = targetOf(old)
        // ponytail: reprise best-effort a la seule page d'erreur ; §5.17 ne precise pas si la PC
        // attend une plage complete (errorPage..pages) plutot qu'une page isolee. Jamais en mode
        // translate : le chapitre entier est renvoye et doit etre traduit en entier.
        val pages = if (old.mode == JobMode.TRANSLATE) old.pages else old.pcErrorPage?.let { listOf(it) } ?: old.pages
        createJobs(newMode, listOf(target), pages, old.translate)
    }

    /**
     * R14 : « Recuperer depuis Google Drive ». Chaque dossier jobs/<jobId> avec result.json sans
     * received.json redevient un job local RESULT_READY (InstallJobWorker l'installe). Ignores : job
     * deja connu en base, chapitre deja installe (fichier FR rattache a ce jobId ou de meme nom), dossier
     * sans request.json lisible (chemin Mihon inconnu). Retourne le nombre de jobs recrees.
     */
    suspend fun recoverFromDrive(): Int {
        val now = clock.nowMillis()
        val recovered = mutableListOf<String>()
        for (item in router.drive.listUnreceivedResults()) {
            val result = item.result
            val request = item.request ?: continue
            if (db.jobDao().get(result.jobId) != null) continue
            val installed = db.localFrChapterDao().getForFolder(result.localFolderName)
                .any { it.jobId == result.jobId || it.fileName == result.chapterFileName }
            if (installed) continue
            val path = MihonPath(request.mihonPath.source, request.mihonPath.series, request.mihonPath.chapter)
            val flatPath = path.flatten()
            val downloaded = db.downloadedChapterDao().get(flatPath)
            db.jobDao().upsert(
                JobEntity(
                    jobId = result.jobId, mode = result.mode, pages = request.options.pages,
                    translate = request.options.translate, mihonPath = flatPath,
                    sourceDir = path.source, seriesDir = path.series, chapterDir = path.chapter,
                    seriesKey = downloaded?.seriesKey, chapterKey = downloaded?.chapterKey,
                    phoneState = "RESULT_READY", failedStep = null, errorMessage = null,
                    driveFolderId = item.folderId, uploadSessionUrl = null, cachePath = null, sha256 = null,
                    sizeBytes = null, transferredBytes = 0L, transferTotal = 0L,
                    pcState = "done", pcStage = null, pcPage = null, pcPages = null, pcEngine = result.engine,
                    pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
                    statusUpdatedAt = null,
                    resultJson = ProtocolJson.encodeToString(ResultJson.serializer(), result),
                    resultFileId = item.resultFileId,
                    createdAt = now, updatedAt = now, finishedAt = result.finishedAt, installedAt = null,
                    installedPath = null, receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
                    channel = CHANNEL_DRIVE,
                ),
            )
            recovered += result.jobId
        }
        val wifiOnly = prefs.wifiOnly.first()
        recovered.forEach { workManager.enqueueInstallJob(it, wifiOnly) }
        if (recovered.isNotEmpty()) AppLog.d("JobRepository", "recuperation Drive : ${recovered.size} job(s) recree(s)")
        return recovered.size
    }

    suspend fun verifyPage(mihonPath: MihonPath, page0: Int) {
        val flatPath = mihonPath.flatten()
        val downloaded = db.downloadedChapterDao().get(flatPath)
        val target = JobTarget(
            mihonPath = mihonPath,
            downloaded = downloaded,
            series = downloaded?.seriesKey?.let { db.seriesDao().get(it) },
            chapter = downloaded?.chapterKey?.let { db.chapterDao().get(it) },
        )
        createJobs(JobMode.VERIFY, listOf(target), pages = listOf(page0), translate = true)
    }
}
