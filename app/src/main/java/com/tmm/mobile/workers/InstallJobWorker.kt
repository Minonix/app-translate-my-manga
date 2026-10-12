package com.tmm.mobile.workers

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.tmm.mobile.R
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.mihon.LocalInstaller
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonTree
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.transport.AuthExpiredException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val NOTIFICATION_ID_INSTALL = 2

// PLAN_GEMINI.md 5.12/5.13/8.4 : RESULT_READY -> DOWNLOADING (telechargement + verif sha256/taille
// contre ResultJson.file, pas contre JobEntity.sha256/sizeBytes qui decrivent l'envoi) -> installation
// locale -> INSTALLED, puis enqueue de l'OutboxWorker (c'est lui qui ecrit received.json).
class InstallJobWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        transferForegroundInfo(applicationContext, NOTIFICATION_ID_INSTALL, R.string.notification_receiving)

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val container = (applicationContext as TmmApp).container
        val db = container.db
        val clock = container.clock
        val job = db.jobDao().get(jobId) ?: return Result.success()
        if (job.phoneState != "RESULT_READY" && job.phoneState != "DOWNLOADING") return Result.success()

        return try {
            setForeground(getForegroundInfo())
            val resultJson = requireNotNull(job.resultJson) { "job RESULT_READY sans resultJson" }
            val result = ProtocolJson.decodeFromString(ResultJson.serializer(), resultJson)
            val fileRef = requireNotNull(result.file) { "resultJson sans fichier" }
            val fileId = requireNotNull(job.resultFileId) { "job sans resultFileId" }

            // PLAN_GEMINI.md 5.4 : RESULT_READY -> DOWNLOADING avant le reseau, ecrit avant l'appel (CdC 11.2).
            db.jobDao().upsert(job.copy(phoneState = "DOWNLOADING", transferredBytes = 0,
                transferTotal = fileRef.size, updatedAt = clock.nowMillis()))

            val cacheDir = File(applicationContext.cacheDir, "jobs/${job.jobId}").apply { mkdirs() }
            val dest = File(cacheDir, "result.cbz")
            var lastProgressWrite = 0L
            container.router.forJob(job).downloadResult(job, fileId, dest) { received, total ->
                val now = android.os.SystemClock.elapsedRealtime()
                // total = -1 si la taille n'est pas annoncee : sans le test > 0, chaque bloc serait ecrit.
                if ((total > 0 && received >= total) || now - lastProgressWrite >= 1000) {
                    lastProgressWrite = now
                    db.jobDao().get(jobId)?.takeIf { it.phoneState == "DOWNLOADING" }?.let { current ->
                        db.jobDao().upsert(current.copy(transferredBytes = received,
                            transferTotal = total.takeIf { it > 0 } ?: fileRef.size, updatedAt = clock.nowMillis()))
                    }
                }
            }

            val actualSize = dest.length()
            val actualSha = sha256(dest)
            if (actualSize != fileRef.size || actualSha != fileRef.sha256) {
                dest.delete()
                return failOrRetry(jobId, db, clock, "sha256/taille du resultat different de resultJson.file")
            }

            // Le job a pu etre oublie ou change d'etat pendant le telechargement (remise a plat, suppression) :
            // on n'installe pas un resultat que plus rien n'attend.
            if (db.jobDao().get(jobId)?.phoneState != "DOWNLOADING") {
                dest.parentFile?.deleteRecursively()
                return Result.success()
            }

            val treeUri = Uri.parse(requireNotNull(container.prefs.mihonTreeUri.first()) { "aucun arbre Mihon configure" })
            val fs: MihonFs = MihonTree(applicationContext.contentResolver, treeUri)
            val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val localRootDocId = fs.findChild(rootDocId, "local")?.docId ?: error("dossier local/ introuvable")

            val content = dest.readBytes()
            val installResult = LocalInstaller(fs).install(
                localRootDocId = localRootDocId,
                localFolderName = result.localFolderName,
                chapterFileName = result.chapterFileName,
                jobId = job.jobId,
                content = content,
                expectedSize = fileRef.size,
                expectedSha256 = fileRef.sha256,
            )
            dest.parentFile?.deleteRecursively()
            if (!installResult.success) {
                return failOrRetry(jobId, db, clock, installResult.error ?: "installation locale echouee")
            }

            val localPath = "${result.localFolderName}/${result.chapterFileName}"
            db.localFrChapterDao().upsert(
                LocalFrChapterEntity(
                    localPath = localPath,
                    folderName = result.localFolderName,
                    fileName = result.chapterFileName,
                    documentUri = requireNotNull(installResult.installedDocId),
                    lastModified = clock.nowMillis(),
                    jobId = job.jobId,
                    mihonPath = job.mihonPath,
                ),
            )
            result.quality?.let { q ->
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
            // Relu plutot que `job` (lu avant le telechargement) : sinon l'ecriture finale remettait a zero
            // la progression et ecrasait tout changement fait entre-temps sur la ligne.
            val current = db.jobDao().get(jobId) ?: return Result.success()
            db.jobDao().upsert(
                current.copy(
                    phoneState = "INSTALLED",
                    transferredBytes = fileRef.size,
                    transferTotal = fileRef.size,
                    installedAt = clock.nowMillis(),
                    installedPath = localPath,
                    updatedAt = clock.nowMillis(),
                ),
            )
            container.workManager.enqueueOutbox(container.prefs.wifiOnly.first())
            Result.success()
        } catch (e: AuthExpiredException) {
            Result.retry()
        } catch (e: IOException) {
            failOrRetry(jobId, db, clock, e.message ?: "erreur reseau")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // resultJson illisible, dossier local/ absent, arbre SAF revoque : sans FAILED, le job resterait
            // DOWNLOADING a vie (plus jamais relance, puisque refresh ne relance que RESULT_READY).
            markFailed(jobId, db, clock, e.message ?: e.javaClass.simpleName)
            Result.failure()
        }
    }

    private suspend fun failOrRetry(jobId: String, db: TmmDatabase, clock: Clock, message: String): Result =
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            markFailed(jobId, db, clock, message)
            Result.failure()
        } else {
            Result.retry()
        }

    private suspend fun markFailed(jobId: String, db: TmmDatabase, clock: Clock, message: String) {
        db.jobDao().get(jobId)?.let {
            db.jobDao().upsert(
                it.copy(phoneState = "FAILED", failedStep = "download", errorMessage = message, updatedAt = clock.nowMillis()),
            )
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
