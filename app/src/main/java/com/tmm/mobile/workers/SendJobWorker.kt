package com.tmm.mobile.workers

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.mihon.CbzPacker
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonTree
import com.tmm.mobile.protocol.ChapterRef
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.MihonPath as ProtoMihonPath
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.RequestOptions
import com.tmm.mobile.protocol.SeriesRef
import com.tmm.mobile.transport.AuthExpiredException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.flow.first

private const val NOTIFICATION_ID_SEND = 1

// PLAN_GEMINI.md 5.12 : PREPARING (cbz + sha256 + taille en cache) -> UPLOADING (envoi resumable,
// session/progression persistees en Room) -> request.json -> SENT. verify/retranslate n'ont pas de
// fichier : request.json (file = null) directement.
class SendJobWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        transferForegroundInfo(applicationContext, NOTIFICATION_ID_SEND, "Envoi en cours")

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val container = (applicationContext as TmmApp).container
        val db = container.db
        val clock = container.clock
        val job = db.jobDao().get(jobId) ?: return Result.success()
        if (job.phoneState != "PREPARING" && job.phoneState != "UPLOADING") return Result.success()

        return try {
            setForeground(getForegroundInfo())

            val jobFolderId = job.driveFolderId ?: container.transport.ensureJobFolder(jobId).also { id ->
                db.jobDao().upsert(job.copy(driveFolderId = id, updatedAt = clock.nowMillis()))
            }

            val fileRef = if (job.mode == JobMode.TRANSLATE) uploadFile(job, container) else null

            val current = db.jobDao().get(jobId) ?: return Result.success()
            val request = buildRequest(current, fileRef, db)
            container.transport.writeRequest(jobFolderId, request)

            current.cachePath?.let { File(it).parentFile?.deleteRecursively() }
            db.jobDao().upsert(current.copy(phoneState = "SENT", cachePath = null, updatedAt = clock.nowMillis()))
            Result.success()
        } catch (e: AuthExpiredException) {
            Result.retry()
        } catch (e: IOException) {
            failOrRetry(jobId, db, clock, e)
        }
    }

    private suspend fun failOrRetry(jobId: String, db: TmmDatabase, clock: Clock, e: IOException): Result =
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            db.jobDao().get(jobId)?.let {
                db.jobDao().upsert(
                    it.copy(phoneState = "FAILED", failedStep = "send", errorMessage = e.message, updatedAt = clock.nowMillis()),
                )
            }
            Result.failure()
        } else {
            Result.retry()
        }

    private suspend fun uploadFile(job: JobEntity, container: AppContainer): FileRef {
        val db = container.db
        val clock = container.clock
        val treeUriString = requireNotNull(container.prefs.mihonTreeUri.first()) { "aucun arbre Mihon configure" }
        val fs: MihonFs = MihonTree(applicationContext.contentResolver, Uri.parse(treeUriString))

        val file: File
        val sha: String
        val size: Long
        if (job.cachePath != null && job.sha256 != null && job.sizeBytes != null && File(job.cachePath).exists()) {
            file = File(job.cachePath)
            sha = job.sha256
            size = job.sizeBytes
        } else {
            file = prepareCbz(job, fs, db)
            sha = sha256(file)
            size = file.length()
            db.jobDao().upsert(
                job.copy(
                    cachePath = file.absolutePath,
                    sha256 = sha,
                    sizeBytes = size,
                    phoneState = "UPLOADING",
                    updatedAt = clock.nowMillis(),
                ),
            )
        }

        var lastProgressWrite = 0L
        container.transport.uploadSource(
            job = db.jobDao().get(job.jobId) ?: job,
            file = file,
            onSession = { session ->
                db.jobDao().get(job.jobId)?.let { j ->
                    db.jobDao().upsert(j.copy(uploadSessionUrl = session, updatedAt = clock.nowMillis()))
                }
            },
            onProgress = { sent, total ->
                val now = System.currentTimeMillis()
                if (total <= 0 || sent >= total || now - lastProgressWrite >= 1000) {
                    lastProgressWrite = now
                    db.jobDao().get(job.jobId)?.let { j ->
                        db.jobDao().upsert(j.copy(transferredBytes = sent, transferTotal = total, updatedAt = clock.nowMillis()))
                    }
                }
            },
        )
        return FileRef(name = "source.cbz", size = size, sha256 = sha)
    }

    private suspend fun prepareCbz(job: JobEntity, fs: MihonFs, db: TmmDatabase): File {
        val downloaded = requireNotNull(db.downloadedChapterDao().get(job.mihonPath)) {
            "chapitre telecharge introuvable: ${job.mihonPath}"
        }
        val dir = File(applicationContext.cacheDir, "jobs/${job.jobId}").apply { mkdirs() }
        val dest = File(dir, "source.cbz")
        if (downloaded.isCbz) {
            fs.openInput(downloaded.documentUri).use { input -> dest.outputStream().use { input.copyTo(it) } }
        } else {
            val entries = fs.children(downloaded.documentUri).filter { !it.isDirectory }
                .associate { entry -> entry.name to fs.openInput(entry.docId).use { it.readBytes() } }
            dest.outputStream().use { CbzPacker.pack(entries, it) }
        }
        return dest
    }

    private suspend fun buildRequest(job: JobEntity, fileRef: FileRef?, db: TmmDatabase): RequestJson {
        val series = job.seriesKey?.let { db.seriesDao().get(it) }
        val chapter = job.chapterKey?.let { db.chapterDao().get(it) }
        val downloaded = db.downloadedChapterDao().get(job.mihonPath)
        val targetLang = job.seriesKey?.let { db.seriesSettingsDao().observe(it).first()?.targetLang } ?: "FR"
        return RequestJson(
            jobId = job.jobId,
            createdAt = Instant.ofEpochMilli(job.createdAt).toString(),
            device = Build.MODEL ?: "android",
            series = SeriesRef(
                title = series?.title ?: job.seriesDir,
                sourceName = series?.sourceName ?: job.sourceDir,
                sourceId = series?.sourceId,
                url = series?.url,
            ),
            chapter = ChapterRef(
                name = chapter?.name ?: job.chapterDir,
                number = (chapter?.number ?: downloaded?.number ?: 0f).toDouble(),
                scanlator = chapter?.scanlator,
                url = chapter?.url,
            ),
            mihonPath = ProtoMihonPath(job.sourceDir, job.seriesDir, job.chapterDir),
            file = fileRef,
            options = RequestOptions(
                mode = job.mode,
                pages = job.pages,
                translate = job.translate,
                sourceLang = "auto",
                targetLang = targetLang,
            ),
        )
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
