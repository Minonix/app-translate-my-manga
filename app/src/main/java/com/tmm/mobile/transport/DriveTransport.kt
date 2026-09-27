package com.tmm.mobile.transport

import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.protocol.CancelJson
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.PcIndex
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ProtocolMarker
import com.tmm.mobile.protocol.ReceivedJson
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.StatusJson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import kotlinx.coroutines.flow.first

private const val CHUNK_SIZE = 8 * 1024 * 1024

// CdC 7.1/7.3. Seule implementation V1 de JobTransport (DirectTransport V2 n'existe pas).
class DriveTransport(
    private val driveApi: DriveApi,
    private val prefs: AppPrefs,
) : JobTransport {

    override suspend fun ensureLayout(): LayoutInfo {
        var created = false
        var rootId = prefs.driveRootId.first()
        if (rootId == null) {
            rootId = driveApi.findChild("root", "TranslateMyManga", folder = true)?.id
                ?: driveApi.createFolder("root", "TranslateMyManga").also { created = true }
            prefs.setDriveRootId(rootId)
        }
        var jobsId = prefs.driveJobsId.first()
        if (jobsId == null) {
            jobsId = driveApi.findChild(rootId, "jobs", folder = true)?.id
                ?: driveApi.createFolder(rootId, "jobs").also { created = true }
            prefs.setDriveJobsId(jobsId)
        }
        var pcId = prefs.drivePcId.first()
        if (pcId == null) {
            pcId = driveApi.findChild(rootId, "pc", folder = true)?.id
                ?: driveApi.createFolder(rootId, "pc").also { created = true }
            prefs.setDrivePcId(pcId)
        }
        var commandsId = prefs.driveCommandsId.first()
        if (commandsId == null) {
            commandsId = driveApi.findChild(pcId, "commands", folder = true)?.id
                ?: driveApi.createFolder(pcId, "commands").also { created = true }
            prefs.setDriveCommandsId(commandsId)
        }
        if (driveApi.findChild(rootId, "protocol.json", folder = false) == null) {
            val bytes = ProtocolJson.encodeToString(ProtocolMarker.serializer(), ProtocolMarker())
                .toByteArray(Charsets.UTF_8)
            driveApi.createJson(rootId, "protocol.json", bytes)
            created = true
        }
        return LayoutInfo(created)
    }

    override suspend fun accountEmail(): String? = driveApi.about()

    override suspend fun ensureJobFolder(jobId: String): String {
        val jobsId = requireNotNull(prefs.driveJobsId.first()) { "ensureLayout() non appele" }
        return driveApi.findChild(jobsId, jobId, folder = true)?.id
            ?: driveApi.createFolder(jobsId, jobId)
    }

    override suspend fun uploadSource(
        job: JobEntity,
        file: File,
        onSession: suspend (String) -> Unit,
        onProgress: suspend (sent: Long, total: Long) -> Unit,
    ): String {
        val jobsId = requireNotNull(prefs.driveJobsId.first()) { "ensureLayout() non appele" }
        val jobFolderId = job.driveFolderId
            ?: driveApi.createFolder(jobsId, job.jobId)
        val total = file.length()
        var session = job.uploadSessionUrl
        if (session == null) {
            session = driveApi.startResumable(jobFolderId, "source.cbz", total, "application/vnd.comicbook+zip")
            onSession(session)
        }
        var offset = 0L
        val existing = driveApi.queryResumable(session, total)
        if (existing is ChunkResult.Incomplete) offset = existing.nextOffset
        if (existing is ChunkResult.Complete) {
            onProgress(total, total)
            return existing.fileId
        }
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteArray(CHUNK_SIZE)
            while (offset < total) {
                raf.seek(offset)
                val read = raf.read(buffer, 0, minOf(CHUNK_SIZE.toLong(), total - offset).toInt())
                if (read <= 0) break
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                // ponytail: une coupure reseau en plein envoi ne relance pas tout l'upload -
                // on interroge la session pour reprendre exactement ou le serveur s'est arrete.
                val result = try {
                    driveApi.putChunk(session, chunk, offset, total)
                } catch (e: IOException) {
                    driveApi.queryResumable(session, total)
                }
                when (result) {
                    is ChunkResult.Complete -> {
                        onProgress(total, total)
                        return result.fileId
                    }
                    is ChunkResult.Incomplete -> {
                        offset = result.nextOffset
                        onProgress(offset, total)
                    }
                }
            }
        }
        error("upload resumable termine sans reponse finale du serveur")
    }

    override suspend fun writeRequest(jobFolderId: String?, request: RequestJson): String {
        val jobsId = requireNotNull(prefs.driveJobsId.first()) { "ensureLayout() non appele" }
        val folderId = jobFolderId ?: driveApi.createFolder(jobsId, request.jobId)
        val bytes = ProtocolJson.encodeToString(RequestJson.serializer(), request).toByteArray(Charsets.UTF_8)
        return driveApi.createJson(folderId, "request.json", bytes).id
    }

    override suspend fun writeCancel(job: JobEntity, cancel: CancelJson) {
        val folderId = requireNotNull(job.driveFolderId) { "job sans dossier Drive" }
        val bytes = ProtocolJson.encodeToString(CancelJson.serializer(), cancel).toByteArray(Charsets.UTF_8)
        driveApi.createJson(folderId, "cancel.json", bytes)
    }

    override suspend fun writeReceived(job: JobEntity, received: ReceivedJson) {
        val folderId = requireNotNull(job.driveFolderId) { "job sans dossier Drive" }
        val bytes = ProtocolJson.encodeToString(ReceivedJson.serializer(), received).toByteArray(Charsets.UTF_8)
        driveApi.createJson(folderId, "received.json", bytes)
    }

    override suspend fun writeCommand(command: SettingsCommand) {
        val commandsId = requireNotNull(prefs.driveCommandsId.first()) { "ensureLayout() non appele" }
        val bytes = ProtocolJson.encodeToString(SettingsCommand.serializer(), command).toByteArray(Charsets.UTF_8)
        driveApi.createJson(commandsId, "${command.cmdId}.json", bytes)
    }

    // CdC 5.9 : liste les fichiers modifies depuis `since`, classe chacun par nom, decode son
    // contenu et en tire jobId directement plutot que de maintenir une table dossier->jobId.
    override suspend fun poll(since: String?): PollResult {
        val sinceIso = since ?: Instant.EPOCH.toString()
        val statuses = mutableListOf<StatusJson>()
        val results = mutableListOf<Pair<ResultJson, String?>>()
        var heartbeat: Heartbeat? = null
        var index: PcIndex? = null
        var settings: PcSettings? = null
        var protocolTooNew = false
        var maxModified: String? = null
        var pageToken: String? = null
        do {
            val page = driveApi.listChanged(sinceIso, pageToken)
            for (entry in page.files) {
                val modified = entry.modifiedTime
                if (modified != null && (maxModified == null || modified > maxModified!!)) maxModified = modified
                try {
                    when (entry.name) {
                        "status.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(StatusJson.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else statuses += decoded
                        }
                        "result.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(ResultJson.serializer(), text)
                            if (decoded.protocol > 1) {
                                protocolTooNew = true
                            } else {
                                val resultCbz = entry.parents.firstOrNull()
                                    ?.let { driveApi.findChild(it, "result.cbz", folder = false)?.id }
                                results += decoded to resultCbz
                            }
                        }
                        "heartbeat.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(Heartbeat.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else heartbeat = decoded
                        }
                        "index.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(PcIndex.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else index = decoded
                        }
                        "settings.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(PcSettings.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else settings = decoded
                        }
                    }
                } catch (e: Exception) {
                    // ponytail: un fichier partiellement ecrit ou corrompu ne doit pas bloquer
                    // tout le poll ; il sera relu au prochain cycle si son modifiedTime avance encore.
                    AppLog.d("DriveTransport", "poll: entree ignoree (${entry.name}) : ${e.message}")
                }
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        // Marge de recouvrement CdC 7.3 : on retranche 120s pour couvrir les ecritures encore en vol.
        val cursor = maxModified?.let { Instant.parse(it).minusSeconds(120).toString() } ?: since
        return PollResult(cursor, statuses, results, heartbeat, index, settings, protocolTooNew)
    }

    override suspend fun readSettings(): PcSettings? {
        val pcId = prefs.drivePcId.first() ?: return null
        val file = driveApi.findChild(pcId, "settings.json", folder = false) ?: return null
        val text = driveApi.readText(file.id)
        return ProtocolJson.decodeFromString(PcSettings.serializer(), text)
    }

    override suspend fun downloadResult(job: JobEntity, fileId: String, dest: File, onProgress: suspend (Long, Long) -> Unit) {
        FileOutputStream(dest).use { out ->
            driveApi.download(fileId, out, onProgress)
        }
    }

    override suspend fun deleteJobFolder(job: JobEntity) {
        val folderId = job.driveFolderId ?: return
        driveApi.delete(folderId)
    }
}
