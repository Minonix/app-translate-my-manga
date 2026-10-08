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
import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_DRIVE
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
import com.tmm.mobile.transport.JobTransport
import com.tmm.mobile.transport.SessionExpiredException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val NOTIFICATION_ID_SEND = 1

// ponytail: limite par processus, suffisant tant qu'un seul processus execute les workers.
private const val SEND_PARALLELISM = 2
private val sendSlots = Semaphore(SEND_PARALLELISM)

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
        val initial = db.jobDao().get(jobId) ?: return Result.success()
        if (initial.phoneState != "PREPARING" && initial.phoneState != "UPLOADING") return Result.success()

        return try {
            setForeground(getForegroundInfo())
            // Envoyer 2000 chapitres d'un coup lancait ~20 workers en parallele (CBZ en memoire, lectures SAF,
            // uploads) et faisait planter l'app : au plus SEND_PARALLELISM a la fois, les autres attendent
            // leur tour deja en foreground (pas de limite de 10 min, pas de tentative consommee).
            sendSlots.withPermit { send(jobId, container) }
        } catch (e: AuthExpiredException) {
            // Sans ce drapeau, l'envoi Drive reessayait en silence a l'infini, sans jamais proposer
            // de renouveler le consentement Google.
            container.prefs.setAuthExpired(true)
            Result.retry()
        } catch (e: SessionExpiredException) {
            // Session resumable Drive perimee (404/410) : on en ouvrira une neuve au prochain essai.
            db.jobDao().get(jobId)?.let { db.jobDao().upsert(it.copy(uploadSessionUrl = null, updatedAt = clock.nowMillis())) }
            failOrRetry(jobId, db, clock, e.message)
        } catch (e: IOException) {
            failOrRetry(jobId, db, clock, e.message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Chapitre supprime de Mihon, arbre SAF revoque... : sans FAILED, le job resterait PREPARING
            // a vie et bloquerait tout nouvel envoi du chapitre (createJobs refuse s'il y a un job actif).
            markFailed(jobId, db, clock, e.message)
            Result.failure()
        }
    }

    private suspend fun send(jobId: String, container: AppContainer): Result {
        val db = container.db
        val clock = container.clock
        // Relu apres l'attente du creneau : le job a pu etre annule entre-temps.
        var job = db.jobDao().get(jobId) ?: return Result.success()
        if (job.phoneState != "PREPARING" && job.phoneState != "UPLOADING") return Result.success()
        // Le canal se decide avant le premier octet envoye (PREPARING) : direct si le PC repond au
        // health-check, sinon Drive. Un envoi Drive encore en cours (UPLOADING, request.json pas ecrit,
        // donc invisible du PC) bascule aussi en direct des que le PC repond. Une fois request.json
        // ecrit, le job reste sur son canal jusqu'au resultat.
        val driveUpload = job.phoneState == "UPLOADING" && job.channel == CHANNEL_DRIVE
        if (job.phoneState == "PREPARING" || driveUpload) {
            val channel = container.router.activeChannel()
            if (channel != job.channel && (!driveUpload || channel == CHANNEL_DIRECT)) {
                // Nouvel essai sur l'autre canal : l'id de dossier/session du precedent n'y vaut rien
                // (le direct stocke le jobId comme "dossier", inconnu de Drive).
                job = job.copy(
                    channel = channel, driveFolderId = null, uploadSessionUrl = null,
                    transferredBytes = 0L, transferTotal = 0L, updatedAt = clock.nowMillis(),
                )
                db.jobDao().upsert(job)
            }
        }
        val transport = container.router.forJob(job)

        val jobFolderId = job.driveFolderId ?: transport.ensureJobFolder(jobId).also { id ->
            db.jobDao().upsert(job.copy(driveFolderId = id, updatedAt = clock.nowMillis()))
        }

        val fileRef = if (job.mode == JobMode.TRANSLATE) uploadFile(job, container, transport) else null

        val current = db.jobDao().get(jobId) ?: return Result.success()
        val request = buildRequest(current, fileRef, db)
        transport.writeRequest(jobFolderId, request)

        current.cachePath?.let { File(it).parentFile?.deleteRecursively() }
        db.jobDao().upsert(current.copy(phoneState = "SENT", cachePath = null, updatedAt = clock.nowMillis()))
        return Result.success()
    }

    private suspend fun failOrRetry(jobId: String, db: TmmDatabase, clock: Clock, message: String?): Result =
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            markFailed(jobId, db, clock, message)
            Result.failure()
        } else {
            Result.retry()
        }

    private suspend fun markFailed(jobId: String, db: TmmDatabase, clock: Clock, message: String?) {
        db.jobDao().get(jobId)?.let {
            db.jobDao().upsert(
                it.copy(phoneState = "FAILED", failedStep = "send", errorMessage = message, updatedAt = clock.nowMillis()),
            )
        }
    }

    private suspend fun uploadFile(job: JobEntity, container: AppContainer, transport: JobTransport): FileRef {
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
        transport.uploadSource(
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
            val docIdByName = fs.children(downloaded.documentUri).filter { !it.isDirectory }.associate { it.name to it.docId }
            dest.outputStream().buffered().use { out ->
                CbzPacker.pack(docIdByName.keys, { name -> fs.openInput(docIdByName.getValue(name)).use { it.readBytes() } }, out)
            }
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
