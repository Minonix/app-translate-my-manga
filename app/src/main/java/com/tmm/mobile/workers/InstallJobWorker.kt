package com.tmm.mobile.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.tmm.mobile.R
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.mihon.CbzPacker
import com.tmm.mobile.mihon.LocalInstaller
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.DriveNotFoundException
import com.tmm.mobile.ui.components.newCoverHttpClient
import com.tmm.mobile.ui.components.normalizeCoverUrl
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val NOTIFICATION_ID_INSTALL = 2

// Chaque chapitre a son propre travail unique : apres une nuit de traductions, des dizaines de
// resultats arrivent ensemble et WorkManager les lancait tous en parallele (telechargement, client
// OkHttp de couverture, couverture de 10 Mo en memoire, liste SAF par job) -> OutOfMemoryError.
// Les installations passent donc par ce verrou de processus, quelques-unes a la fois ; les autres
// attendent suspendues (sans thread bloque) et la file se vide par lots.
private const val MAX_CONCURRENT_INSTALLS = 2
private val installSlots = Semaphore(MAX_CONCURRENT_INSTALLS)

// PLAN_GEMINI.md 5.12/5.13/8.4 : RESULT_READY -> DOWNLOADING (telechargement + verif sha256/taille
// contre ResultJson.file, pas contre JobEntity.sha256/sizeBytes qui decrivent l'envoi) -> installation
// locale -> INSTALLED, puis enqueue de l'OutboxWorker (c'est lui qui ecrit received.json).
// R10 : le CBZ telecharge reste en cache jusqu'au succes de l'installation ; un nouvel essai reprend
// au cache verifie (pas de retelechargement). Chaque echec porte un code local (§1.2).
class InstallJobWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        transferForegroundInfo(applicationContext, NOTIFICATION_ID_INSTALL, R.string.notification_receiving)

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val container = (applicationContext as TmmApp).container
        val job = container.db.jobDao().get(jobId) ?: return Result.success()
        if (job.phoneState != "RESULT_READY" && job.phoneState != "DOWNLOADING") return Result.success()
        return installSlots.withPermit { install(jobId, container) }
    }

    private suspend fun install(jobId: String, container: AppContainer): Result {
        val db = container.db
        val clock = container.clock
        // Relu apres l'attente du verrou : le job a pu etre oublie, remis a plat ou installe entre-temps.
        val job = db.jobDao().get(jobId) ?: return Result.success()
        if (job.phoneState != "RESULT_READY" && job.phoneState != "DOWNLOADING") return Result.success()
        // Etape en cours, pour le failedStep et le code d'un echec d'E/S : "download" ou "install".
        var step = "download"

        return try {
            setForegroundIfAllowed()
            val result = job.resultJson
                ?.let { runCatching { ProtocolJson.decodeFromString(ResultJson.serializer(), it) }.getOrNull() }
                ?: return fail(jobId, db, clock, step, LocalErrorCode.DOWNLOAD, "resultJson absent ou illisible")
            // R10 : rien a installer (file = null) -> le job est clos sans installation ni exception.
            val fileRef = result.file ?: return closeWithoutInstall(job, container)
            val fileId = job.resultFileId
                ?: return fail(jobId, db, clock, step, LocalErrorCode.RESULT_MISSING, "result.cbz introuvable")

            // PLAN_GEMINI.md 5.4 : RESULT_READY -> DOWNLOADING avant le reseau, ecrit avant l'appel (CdC 11.2).
            db.jobDao().upsert(job.copy(phoneState = "DOWNLOADING", transferredBytes = 0,
                transferTotal = fileRef.size, updatedAt = clock.nowMillis()))

            val cacheDir = File(applicationContext.cacheDir, "jobs/${job.jobId}").apply { mkdirs() }
            val dest = File(cacheDir, "result.cbz")
            val cached = dest.exists() && dest.length() == fileRef.size && sha256(dest) == fileRef.sha256
            if (!cached) {
                var lastProgressWrite = 0L
                try {
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
                } catch (e: DriveNotFoundException) {
                    // result.json present, result.cbz absent : on redemande au PC (redeliver), sans reessai.
                    dest.delete()
                    return fail(jobId, db, clock, step, LocalErrorCode.RESULT_MISSING, e.message)
                }

                if (dest.length() != fileRef.size || sha256(dest) != fileRef.sha256) {
                    dest.delete()
                    return failOrRetry(jobId, db, clock, step, LocalErrorCode.DOWNLOAD, "sha256/taille du resultat different de resultJson.file")
                }
            }

            // Le job a pu etre oublie ou change d'etat pendant le telechargement (remise a plat, suppression) :
            // on n'installe pas un resultat que plus rien n'attend.
            if (db.jobDao().get(jobId)?.phoneState != "DOWNLOADING") {
                dest.parentFile?.deleteRecursively()
                return Result.success()
            }

            step = "install"
            // Arbre SAF absent ou revoque : SecurityException -> `permission` (ecran d'autorisation).
            val root = container.mihonRoot() ?: throw SecurityException("aucun arbre Mihon accessible")
            val localRootDocId = root.fs.findChild(root.rootDocId, "local")?.docId
                ?: return failOrRetry(jobId, db, clock, step, LocalErrorCode.INSTALL, "dossier local/ introuvable")

            val installResult = LocalInstaller(root.fs).install(
                localRootDocId = localRootDocId,
                localFolderName = result.localFolderName,
                chapterFileName = result.chapterFileName,
                jobId = job.jobId,
                openContent = { dest.inputStream() },
                expectedSize = fileRef.size,
                expectedSha256 = fileRef.sha256,
            )
            if (!installResult.success) {
                // Cache garde : le prochain essai reinstalle sans retelecharger.
                return failOrRetry(jobId, db, clock, step, LocalErrorCode.INSTALL, installResult.error ?: "installation locale echouee")
            }
            // Couverture de la serie dans local/ : jamais bloquant, le chapitre est deja installe.
            try {
                val thumbnailUrl = job.seriesKey?.let { db.seriesDao().get(it)?.thumbnailUrl }
                val written = withContext(Dispatchers.IO) {
                    LocalInstaller(root.fs).ensureCover(localRootDocId, result.localFolderName) {
                        downloadCover(thumbnailUrl) ?: dest.inputStream().use { CbzPacker.firstPage(it) }
                        ?.let { (name, bytes) -> name.substringAfterLast('.', "jpg") to bytes }
                    }
                }
                if (written) AppLog.d("Install", "couverture posee dans local/${result.localFolderName}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("Install", "couverture non posee : ${e.javaClass.simpleName} ${e.message}")
            }
            dest.parentFile?.deleteRecursively()

            val localPath = "${result.localFolderName}/${result.chapterFileName}"
            // R2 : le nom du fichier porte desormais l'edition. Le meme chapitre deja installe sous un
            // autre nom (ancien nom sans edition) est remplace, sinon Mihon l'afficherait deux fois.
            for (old in db.localFrChapterDao().getForFolder(result.localFolderName)) {
                if (old.mihonPath != job.mihonPath || old.localPath == localPath) continue
                if (runCatching { root.fs.delete(old.documentUri) }.isSuccess) db.localFrChapterDao().delete(old)
            }
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
            saveQuality(job, result, db)
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
                    failedStep = null,
                    errorCode = null,
                    errorMessage = null,
                    updatedAt = clock.nowMillis(),
                ),
            )
            container.workManager.enqueueOutbox(container.prefs.wifiOnly.first())
            Result.success()
        } catch (e: AuthExpiredException) {
            // R13 : meme drapeau que les autres workers, jamais FAILED.
            container.prefs.setAuthExpired(true)
            Result.retry()
        } catch (e: SecurityException) {
            fail(jobId, db, clock, step, LocalErrorCode.PERMISSION, e.message)
        } catch (e: IOException) {
            val code = when {
                isDiskFull(e) -> LocalErrorCode.PHONE_DISK_FULL
                step == "install" -> LocalErrorCode.INSTALL
                else -> localErrorCode(e)
            }
            if (code == LocalErrorCode.PHONE_DISK_FULL) {
                fail(jobId, db, clock, step, code, e.message)
            } else {
                failOrRetry(jobId, db, clock, step, code, e.message ?: "erreur reseau")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Reponse illisible, erreur SAF inattendue : sans FAILED, le job resterait DOWNLOADING a vie
            // (plus jamais relance, puisque refresh ne relance que RESULT_READY).
            fail(
                jobId, db, clock, step,
                if (step == "install") LocalErrorCode.INSTALL else LocalErrorCode.DOWNLOAD,
                e.message ?: e.javaClass.simpleName,
            )
        }
    }

    // R10 : result.json sans fichier (rien a installer) : clos sans exception ni installation.
    private suspend fun closeWithoutInstall(job: JobEntity, container: AppContainer): Result {
        val current = container.db.jobDao().get(job.jobId) ?: return Result.success()
        container.db.jobDao().upsert(
            current.copy(phoneState = "INSTALLED", installedAt = container.clock.nowMillis(), updatedAt = container.clock.nowMillis()),
        )
        container.workManager.enqueueOutbox(container.prefs.wifiOnly.first())
        return Result.success()
    }

    private suspend fun saveQuality(job: JobEntity, result: ResultJson, db: TmmDatabase) {
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
    }

    private suspend fun failOrRetry(jobId: String, db: TmmDatabase, clock: Clock, step: String, code: String, message: String): Result =
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            fail(jobId, db, clock, step, code, message)
        } else {
            Result.retry()
        }

    private suspend fun fail(jobId: String, db: TmmDatabase, clock: Clock, step: String, code: String, message: String?): Result {
        db.jobDao().get(jobId)?.let {
            db.jobDao().upsert(
                it.copy(
                    phoneState = "FAILED", failedStep = step, errorCode = code, errorMessage = message,
                    updatedAt = clock.nowMillis(),
                ),
            )
        }
        return Result.failure()
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

/** Couverture de la sauvegarde (extension, octets), ou null si absente, refusee ou trop lourde. */
private fun downloadCover(thumbnailUrl: String?): Pair<String, ByteArray>? {
    val url = normalizeCoverUrl(thumbnailUrl) ?: return null
    return runCatching {
        newCoverHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            val type = body.contentType()
            if (!response.isSuccessful || type?.type != "image" || body.contentLength() > MAX_COVER_BYTES) return@use null
            val ext = when (type.subtype.lowercase()) {
                "png" -> "png"
                "webp" -> "webp"
                "gif" -> "gif"
                "avif" -> "avif"
                else -> "jpg"
            }
            ext to body.bytes()
        }
    }.getOrNull()
}

private const val MAX_COVER_BYTES = 10L * 1024 * 1024
