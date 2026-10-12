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
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.protocol.MihonPath
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
    ): CreateJobsResult {
        val createdIds = mutableListOf<String>()
        var refused = 0
        // Un seul verrou et une seule transaction pour tout le lot : envoyer 2000 chapitres declenchait
        // 2000 invalidations Room, et l'accueil se recalculait a chacune.
        mutex.withLock {
            db.withTransaction {
                for (target in targets) {
                    val jobId = run {
                        val flatPath = target.mihonPath.flatten()
                        val hasActive = db.jobDao().getAllForMihonPath(flatPath).any { it.isActive() }
                        val indexEntry = db.pcIndexChapterDao().get(flatPath)
                        val refuse = if (mode == JobMode.TRANSLATE) {
                            // Sans chapitre telecharge, SendJobWorker.prepareCbz echouerait ("chapitre
                            // telecharge introuvable"). Lu en base et non dans target.downloaded : Serie et
                            // Bibliotheque passent null pour les lignes catalogue, et c'est cette meme
                            // ligne que le worker relira.
                            hasActive || indexEntry?.original == true || db.downloadedChapterDao().get(flatPath) == null
                        } else {
                            hasActive || indexEntry?.original != true
                        }
                        if (refuse) {
                            refused++
                            return@run null
                        }
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
        for (job in unseen) {
            try {
                router.forJob(job).deleteJobFolder(job)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("JobRepository", "dossier Drive du job non supprime : ${e.message}")
            }
            db.jobDao().delete(job)
            recreate(job)
        }
        if (unseen.isNotEmpty()) AppLog.d("JobRepository", "${unseen.size} job(s) Drive sans reponse renvoye(s) en direct")
    }

    suspend fun retry(jobId: String) {
        recreate(db.jobDao().get(jobId) ?: return)
    }

    private suspend fun recreate(old: JobEntity) {
        val flatPath = old.mihonPath
        val indexEntry = db.pcIndexChapterDao().get(flatPath)
        val newMode = if (old.mode == JobMode.TRANSLATE && indexEntry?.original == true) {
            JobMode.RETRANSLATE
        } else {
            old.mode
        }
        val target = JobTarget(
            mihonPath = MihonPath(old.sourceDir, old.seriesDir, old.chapterDir),
            downloaded = db.downloadedChapterDao().get(flatPath),
            series = old.seriesKey?.let { db.seriesDao().get(it) },
            chapter = old.chapterKey?.let { db.chapterDao().get(it) },
        )
        // ponytail: reprise best-effort a la seule page d'erreur ; §5.17 ne precise pas si la PC
        // attend une plage complete (errorPage..pages) plutot qu'une page isolee.
        val pages = old.pcErrorPage?.let { listOf(it) } ?: old.pages
        createJobs(newMode, listOf(target), pages, old.translate)
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
