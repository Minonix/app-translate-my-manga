package com.tmm.mobile.data

import androidx.room.withTransaction
import androidx.work.WorkManager
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.Clock
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
                            hasActive || indexEntry?.original == true
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

    suspend fun retry(jobId: String) {
        val old = db.jobDao().get(jobId) ?: return
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
