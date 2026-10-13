package com.tmm.mobile.workers

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.tmm.mobile.R
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.ErrorCatalog
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.mihon.CbzPacker
import com.tmm.mobile.mihon.DownloadOnlySeries
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.protocol.ChapterRef
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.MihonPath as ProtoMihonPath
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.RequestOptions
import com.tmm.mobile.protocol.SeriesRef
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.DirectHttpException
import com.tmm.mobile.transport.DriveQuotaException
import com.tmm.mobile.transport.JobTransport
import com.tmm.mobile.transport.SessionExpiredException
import java.io.File
import java.io.FileNotFoundException
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
        transferForegroundInfo(applicationContext, NOTIFICATION_ID_SEND, R.string.notification_sending)

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
            // R13 : jamais FAILED. Sans ce drapeau, l'envoi Drive reessayait en silence a l'infini, sans
            // jamais proposer de renouveler le consentement Google.
            container.prefs.setAuthExpired(true)
            Result.retry()
        } catch (e: SessionExpiredException) {
            // Session resumable Drive perimee (404/410) : on en ouvrira une neuve au prochain essai.
            db.jobDao().get(jobId)?.let { db.jobDao().upsert(it.copy(uploadSessionUrl = null, updatedAt = clock.nowMillis())) }
            failOrRetry(jobId, db, clock, e.message, LocalErrorCode.NETWORK)
        } catch (e: DriveQuotaException) {
            // Drive plein : reessayer 5 fois ne liberera pas de place, l'utilisateur relance apres menage.
            markFailed(jobId, db, clock, e.message, LocalErrorCode.DRIVE_QUOTA)
            container.prefs.setDriveFull(true)
            Result.failure()
        } catch (e: DirectHttpException) {
            // Refus du PC en direct (HTTP 200 + status failed, ou 400 bad_request/protocol/integrity) : meme
            // code et meme affichage que le status.json "failed" du canal Drive. 404/405 sans Drive configure
            // (sinon send() s'est replie sur Drive) : PC trop ancien pour le direct, libelle « version du PC
            // trop ancienne » (code PC `protocol`, action update_pc), reessayer ne servirait a rien. Le reste
            // (500 internal...) est reessaye : le PC est idempotent par jobId, le meme POST repart.
            when {
                (e.status == 200 || e.status in 400..499) && e.code != null && e.code in ErrorCatalog.pcJob -> {
                    markFailed(jobId, db, clock, e.message, errorCode = null, pcErrorCode = e.code)
                    Result.failure()
                }
                e.status == 404 || e.status == 405 -> {
                    markFailed(jobId, db, clock, e.message, errorCode = null, pcErrorCode = "protocol")
                    Result.failure()
                }
                else -> failOrRetry(jobId, db, clock, e.message, LocalErrorCode.NETWORK)
            }
        } catch (e: IOException) {
            val code = localErrorCode(e)
            if (code == LocalErrorCode.PHONE_DISK_FULL) {
                markFailed(jobId, db, clock, e.message, code)
                Result.failure()
            } else {
                failOrRetry(jobId, db, clock, e.message, code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Chapitre supprime de Mihon, arbre SAF revoque... : sans FAILED, le job resterait PREPARING
            // a vie et bloquerait tout nouvel envoi du chapitre (createJobs refuse s'il y a un job actif).
            val code = if (e is SecurityException || e is SourceUnreadableException) localErrorCode(e) else null
            markFailed(jobId, db, clock, e.message, code)
            Result.failure()
        }
    }

    // forceDrive : repli sur Drive apres un 404/405 du direct, sans refaire le choix du canal.
    private suspend fun send(jobId: String, container: AppContainer, forceDrive: Boolean = false): Result {
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
        if (!forceDrive && (job.phoneState == "PREPARING" || driveUpload)) {
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
        return try {
            deliver(jobId, job, container, transport)
        } catch (e: DirectHttpException) {
            if (job.channel != CHANNEL_DIRECT) throw e
            when {
                // 409 other_channel : le PC a deja ce job par Drive. Ni echec ni « Relancer » (doublon) :
                // le job passe SENT sur le canal Drive et se suit par le poll Drive.
                e.status == 409 -> {
                    switchToDrive(jobId, container, phoneState = "SENT")
                    Result.success()
                }
                // 404/405 not_found : PC sans route directe (ancienne version). Repli immediat sur Drive
                // s'il est configure, au lieu de 5 essais inutiles ; sinon doWork() marque l'echec update_pc.
                // ponytail: le repli n'est pas memorise ; un nouvel essai (erreur reseau Drive) repasse par
                // le direct (PC joignable) et reprend l'envoi Drive de zero. A revoir si les vieux PC durent.
                (e.status == 404 || e.status == 405) && !forceDrive && container.prefs.googleEmail.first() != null -> {
                    switchToDrive(jobId, container, phoneState = null)
                    send(jobId, container, forceDrive = true)
                }
                else -> throw e
            }
        }
    }

    // Bascule le job sur le canal Drive : l'id stocke par le direct (le jobId) ne vaut rien sur Drive,
    // le dossier sera retrouve ou cree par son nom (R2). phoneState non nul : nouvel etat (SENT pour 409).
    private suspend fun switchToDrive(jobId: String, container: AppContainer, phoneState: String?) {
        val db = container.db
        val current = db.jobDao().get(jobId) ?: return
        var updated = current.copy(
            channel = CHANNEL_DRIVE, driveFolderId = null, uploadSessionUrl = null,
            transferredBytes = 0L, transferTotal = 0L, updatedAt = container.clock.nowMillis(),
        )
        if (phoneState != null) {
            current.cachePath?.let { File(it).parentFile?.deleteRecursively() }
            updated = updated.copy(phoneState = phoneState, cachePath = null)
        }
        db.jobDao().upsert(updated)
    }

    private suspend fun deliver(jobId: String, initial: JobEntity, container: AppContainer, transport: JobTransport): Result {
        val db = container.db
        val clock = container.clock
        var job = initial

        // R2 : l'id du dossier est enregistre en base avant tout envoi, et `job` est mis a jour avec lui :
        // l'ancienne copie (driveFolderId = null) ecrasait l'id en base, et uploadSource creait un second
        // dossier jobs/<jobId> (source.cbz d'un cote, request.json de l'autre).
        val jobFolderId = job.driveFolderId ?: transport.ensureJobFolder(jobId).also { id ->
            job = job.copy(driveFolderId = id, updatedAt = clock.nowMillis())
            db.jobDao().upsert(job)
        }

        // Redemande (redeliver) : request.json seul, file = null, le PC renvoie sa traduction existante.
        val fileRef = if (job.mode == JobMode.TRANSLATE && !job.redeliver) uploadFile(job, container, transport) else null

        val current = db.jobDao().get(jobId) ?: return Result.success()
        val request = buildRequest(current, fileRef, db)
        transport.writeRequest(jobFolderId, request)

        current.cachePath?.let { File(it).parentFile?.deleteRecursively() }
        db.jobDao().upsert(current.copy(phoneState = "SENT", cachePath = null, updatedAt = clock.nowMillis()))
        // Un envoi a abouti : Drive n'est plus plein (sur Drive ; en direct, le drapeau est sans objet).
        if (current.channel == CHANNEL_DRIVE) container.prefs.setDriveFull(false)
        return Result.success()
    }

    private suspend fun failOrRetry(jobId: String, db: TmmDatabase, clock: Clock, message: String?, errorCode: String): Result =
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            markFailed(jobId, db, clock, message, errorCode)
            Result.failure()
        } else {
            Result.retry()
        }

    private suspend fun markFailed(
        jobId: String,
        db: TmmDatabase,
        clock: Clock,
        message: String?,
        errorCode: String?,
        pcErrorCode: String? = null,
    ) {
        db.jobDao().get(jobId)?.let {
            db.jobDao().upsert(
                it.copy(
                    phoneState = "FAILED", failedStep = "send", errorMessage = message, errorCode = errorCode,
                    pcErrorCode = pcErrorCode ?: it.pcErrorCode, updatedAt = clock.nowMillis(),
                ),
            )
        }
    }

    private suspend fun uploadFile(job: JobEntity, container: AppContainer, transport: JobTransport): FileRef {
        val db = container.db
        val clock = container.clock

        val file: File
        val sha: String
        val size: Long
        if (job.cachePath != null && job.sha256 != null && job.sizeBytes != null && File(job.cachePath).exists()) {
            file = File(job.cachePath)
            sha = job.sha256
            size = job.sizeBytes
        } else {
            // Arbre SAF absent ou revoque : SecurityException -> code `permission` (ecran d'autorisation).
            val fs: MihonFs = container.mihonRoot()?.fs ?: throw SecurityException("aucun arbre Mihon accessible")
            file = prepareCbz(job, fs, db)
            sha = sha256(file)
            size = file.length()
            // R2 : relu en base (et non `job`, copie d'avant ensureJobFolder) pour ne pas effacer driveFolderId.
            val current = db.jobDao().get(job.jobId) ?: job
            db.jobDao().upsert(
                current.copy(
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
        val downloaded = db.downloadedChapterDao().get(job.mihonPath)
            ?: throw SourceUnreadableException("chapitre telecharge introuvable: ${job.mihonPath}")
        val dir = File(applicationContext.cacheDir, "jobs/${job.jobId}").apply { mkdirs() }
        val dest = File(dir, "source.cbz")
        try {
            if (downloaded.isCbz) {
                fs.openInput(downloaded.documentUri).use { input -> dest.outputStream().use { input.copyTo(it) } }
            } else {
                val docIdByName = fs.children(downloaded.documentUri).filter { !it.isDirectory }.associate { it.name to it.docId }
                dest.outputStream().buffered().use { out ->
                    CbzPacker.pack(docIdByName.keys, { name -> fs.openInput(docIdByName.getValue(name)).use { it.readBytes() } }, out)
                }
            }
        } catch (e: FileNotFoundException) {
            // Document SAF disparu (chapitre supprime de Mihon) ; un disque plein garde son propre code.
            if (isDiskFull(e)) throw e
            throw SourceUnreadableException("chapitre illisible dans Mihon : ${e.message}")
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
                sourceId = series?.takeUnless { DownloadOnlySeries.isDownloadOnly(it.seriesKey) }?.sourceId,
                url = series?.takeUnless { DownloadOnlySeries.isDownloadOnly(it.seriesKey) }?.url,
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
                redeliver = if (job.redeliver) true else null,
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
