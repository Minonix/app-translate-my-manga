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
import com.tmm.mobile.protocol.RemoteInfo
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.StatusJson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val CHUNK_SIZE = 8 * 1024 * 1024

// Dossier racine de l'utilisateur, casse exacte (un ancien "APP" peut exister a cote).
const val APP_FOLDER = "App"

private val PC_FILES = listOf("heartbeat.json", "index.json", "settings.json", "remote.json")
private val JOB_FILES = listOf("status.json", "result.json")
private const val PROTOCOL_FILE = "protocol.json"

// CdC 7.1/7.3. Canal Drive de JobTransport (le canal direct est DirectTransport).
class DriveTransport(
    private val driveApi: DriveApi,
    private val prefs: AppPrefs,
) : JobTransport {

    override suspend fun ensureLayout(): LayoutInfo {
        var created = false
        var appId = prefs.driveAppId.first()
        if (appId == null) {
            // "App" appartient a l'utilisateur (un sous-dossier par application) : jamais cree ici, un
            // second App a la racine serait un clone. Introuvable = portee insuffisante ou dossier absent.
            appId = driveApi.findChild("root", APP_FOLDER, folder = true)?.id
                ?: throw IOException("Dossier $APP_FOLDER introuvable a la racine du Drive")
            prefs.setDriveAppId(appId)
        }
        var rootId = prefs.driveRootId.first()
        if (rootId == null) {
            rootId = driveApi.findChild(appId, "TranslateMyManga", folder = true)?.id
                ?: driveApi.createFolder(appId, "TranslateMyManga").also { created = true }
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
        val marker = driveApi.findChild(rootId, PROTOCOL_FILE, folder = false)
        if (marker == null) {
            val bytes = ProtocolJson.encodeToString(ProtocolMarker.serializer(), ProtocolMarker())
                .toByteArray(Charsets.UTF_8)
            driveApi.createJson(rootId, PROTOCOL_FILE, bytes)
            created = true
        } else {
            // protocol.json du PC : un protocole > 1 leve le bandeau « mettre a jour l'app », 1 le fait retomber.
            readProtocolMarker(marker.id)?.let { prefs.setProtocolTooNew(it > 1) }
        }
        return LayoutInfo(created)
    }

    // R3 : un fichier vide ou tronque est « pas encore pret », pas une erreur.
    private suspend fun readProtocolMarker(fileId: String): Int? = try {
        ProtocolJson.decodeFromString(ProtocolMarker.serializer(), driveApi.readText(fileId)).protocol
    } catch (e: AuthExpiredException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.d("DriveTransport", "protocol.json illisible : ${e.message}")
        null
    }

    private suspend fun findJobFolder(jobId: String): String? {
        val jobsId = prefs.driveJobsId.first() ?: return null
        return driveApi.findChild(jobsId, jobId, folder = true)?.id
    }

    // R1 : fichier a nom fixe ecrit en « chercher puis mettre a jour par fileId, creer seulement s'il
    // n'existe pas ». ponytail: pas de cache des fileId (une recherche par ecriture) : un cache par job
    // grossirait sans fin dans DataStore. A revoir si le quota de requetes Drive devient un sujet.
    private suspend fun writeFixedJson(parentId: String, name: String, bytes: ByteArray): DriveFile {
        val existing = driveApi.findChild(parentId, name, folder = false)
        if (existing != null) {
            try {
                return driveApi.updateJson(existing.id, bytes)
            } catch (e: DriveNotFoundException) {
                // Supprime entre la recherche et l'ecriture : on le recree.
            }
        }
        return driveApi.createJson(parentId, name, bytes)
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
        // R2 : jamais de createFolder aveugle ; le dossier vient de la base ou d'un chercher-puis-creer.
        val jobFolderId = job.driveFolderId ?: ensureJobFolder(job.jobId)
        val total = file.length()
        var session = job.uploadSessionUrl
        var offset = 0L
        if (session == null) {
            session = driveApi.startResumable(jobFolderId, "source.cbz", total, "application/vnd.comicbook+zip")
            onSession(session)
        } else {
            // Reprise seulement : une session neuve est vide, l'interroger coutait un aller-retour de plus
            // par chapitre (sensible quand une serie entiere part sur une connexion lente).
            val existing = driveApi.queryResumable(session, total)
            if (existing is ChunkResult.Incomplete) offset = existing.nextOffset
            if (existing is ChunkResult.Complete) {
                onProgress(total, total)
                return existing.fileId
            }
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
        val folderId = jobFolderId ?: ensureJobFolder(request.jobId)
        val bytes = ProtocolJson.encodeToString(RequestJson.serializer(), request).toByteArray(Charsets.UTF_8)
        return writeFixedJson(folderId, "request.json", bytes).id
    }

    // Sans dossier sur Drive, le PC n'a jamais vu le job : rien a annuler.
    override suspend fun writeCancel(job: JobEntity, cancel: CancelJson) {
        val folderId = job.driveFolderId ?: findJobFolder(job.jobId) ?: return
        val bytes = ProtocolJson.encodeToString(CancelJson.serializer(), cancel).toByteArray(Charsets.UTF_8)
        writeFixedJson(folderId, "cancel.json", bytes)
    }

    // Dossier deja purge cote Drive : l'accuse n'a plus de destinataire.
    override suspend fun writeReceived(job: JobEntity, received: ReceivedJson) {
        val folderId = job.driveFolderId ?: findJobFolder(job.jobId) ?: return
        val bytes = ProtocolJson.encodeToString(ReceivedJson.serializer(), received).toByteArray(Charsets.UTF_8)
        writeFixedJson(folderId, "received.json", bytes)
    }

    override suspend fun writeCommand(command: SettingsCommand) {
        val commandsId = requireNotNull(prefs.driveCommandsId.first()) { "ensureLayout() non appele" }
        val bytes = ProtocolJson.encodeToString(SettingsCommand.serializer(), command).toByteArray(Charsets.UTF_8)
        writeFixedJson(commandsId, "${command.cmdId}.json", bytes)
    }

    // CdC 5.9 : liste les fichiers modifies depuis `since`, classe chacun par nom, decode son
    // contenu et en tire jobId directement plutot que de maintenir une table dossier->jobId.
    override suspend fun poll(since: String?): PollResult {
        val sinceIso = since ?: Instant.EPOCH.toString()
        // R3 : status le plus recent par job, result le plus ancien par job (doublons de nom).
        val statusByJob = HashMap<String, Pair<String, StatusJson>>()
        val resultByJob = HashMap<String, Triple<String, ResultJson, String?>>()
        // R3 : fichiers PC reecrits en place, le modifiedTime le plus recent l'emporte.
        var heartbeat: Pair<String, Heartbeat>? = null
        var index: Pair<String, PcIndex>? = null
        var settings: Pair<String, PcSettings>? = null
        var remote: Pair<String, RemoteInfo>? = null
        var protocolTooNew = false
        var protocolSeen = false
        var maxModified: String? = null
        // R4 : le curseur n'avance pas au-dela d'une entree qui n'a pas pu etre lue.
        var minUnreadable: String? = null
        var serverTime: String? = null
        var pageToken: String? = null
        val rootId = prefs.driveRootId.first()
        fun newer(candidate: String, known: Pair<String, *>?) = known == null || candidate >= known.first
        // Acces Drive complet : un settings.json ou status.json d'une autre application peut porter le
        // meme nom. Seuls comptent les fichiers de pc/ et des dossiers de jobs/ de la boite aux lettres.
        val pcId = prefs.drivePcId.first()
        val jobsId = prefs.driveJobsId.first()
        var jobFolderIds: Set<String>? = null
        do {
            val page = driveApi.listChanged(sinceIso, pageToken, PC_FILES + JOB_FILES + PROTOCOL_FILE)
            if (serverTime == null) serverTime = page.serverTime
            for (entry in page.files) {
                val modified = entry.modifiedTime ?: ""
                if (modified.isNotEmpty() && (maxModified == null || modified > maxModified!!)) maxModified = modified
                try {
                    val parent = entry.parents.firstOrNull()
                    val inMailbox = when (entry.name) {
                        in PC_FILES -> parent != null && parent == pcId
                        in JOB_FILES -> parent != null && jobsId != null &&
                            parent in (jobFolderIds ?: driveApi.listChildFolderIds(jobsId).also { jobFolderIds = it })
                        PROTOCOL_FILE -> parent != null && parent == rootId
                        else -> false
                    }
                    if (!inMailbox) continue
                    when (entry.name) {
                        "status.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(StatusJson.serializer(), text)
                            if (decoded.protocol > 1) {
                                protocolTooNew = true
                            } else if (newer(modified, statusByJob[decoded.jobId])) {
                                statusByJob[decoded.jobId] = modified to decoded
                            }
                        }
                        "result.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(ResultJson.serializer(), text)
                            val known = resultByJob[decoded.jobId]
                            if (decoded.protocol > 1) {
                                protocolTooNew = true
                            } else if (known == null || modified < known.first) {
                                val resultCbz = entry.parents.firstOrNull()
                                    ?.let { driveApi.findChild(it, "result.cbz", folder = false)?.id }
                                resultByJob[decoded.jobId] = Triple(modified, decoded, resultCbz)
                            }
                        }
                        "heartbeat.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(Heartbeat.serializer(), text)
                            if (decoded.protocol > 1) {
                                protocolTooNew = true
                            } else {
                                protocolSeen = true
                                if (newer(modified, heartbeat)) heartbeat = modified to decoded
                            }
                        }
                        "index.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(PcIndex.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else if (newer(modified, index)) index = modified to decoded
                        }
                        "settings.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(PcSettings.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else if (newer(modified, settings)) settings = modified to decoded
                        }
                        "remote.json" -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(RemoteInfo.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else if (newer(modified, remote)) remote = modified to decoded
                        }
                        PROTOCOL_FILE -> {
                            val text = driveApi.readText(entry.id)
                            val decoded = ProtocolJson.decodeFromString(ProtocolMarker.serializer(), text)
                            if (decoded.protocol > 1) protocolTooNew = true else protocolSeen = true
                        }
                    }
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // R3/R4 : un fichier vide, tronque ou une erreur reseau sur cette entree ne bloque pas le
                    // poll ; le curseur reste avant elle pour la relire au prochain cycle.
                    AppLog.d("DriveTransport", "poll: entree relue au prochain cycle (${entry.name}) : ${e.message}")
                    if (modified.isNotEmpty() && (minUnreadable == null || modified < minUnreadable!!)) minUnreadable = modified
                }
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        // Marge de recouvrement CdC 7.3 : on retranche 120s pour couvrir les ecritures encore en vol.
        // R4 : borne a juste avant la premiere entree illisible (la requete est « modifiedTime > curseur »).
        val cursor = when {
            minUnreadable != null -> Instant.parse(minUnreadable).minusSeconds(120).minusMillis(1).toString()
            maxModified != null -> Instant.parse(maxModified).minusSeconds(120).toString()
            else -> since
        }
        return PollResult(
            cursor = cursor,
            statuses = statusByJob.values.map { it.second },
            results = resultByJob.values.sortedBy { it.first }.map { it.second to it.third },
            heartbeat = heartbeat?.second,
            index = index?.second,
            settings = settings?.second,
            protocolTooNew = protocolTooNew,
            remote = remote?.second,
            heartbeatModifiedTime = heartbeat?.first,
            serverTime = serverTime,
            protocolSeen = protocolSeen && !protocolTooNew,
        )
    }

    // R4 : lecture sur demande ; un fichier absent, vide ou illisible rend null (relu plus tard) au lieu de lever.
    override suspend fun readSettings(): PcSettings? = readPcFile("settings.json", PcSettings.serializer())

    // Lecture directe de pc/remote.json (ecran d'appairage) : le poll ne le voit que s'il a change
    // depuis le curseur, alors qu'ici on veut la valeur courante.
    suspend fun readRemoteInfo(): RemoteInfo? {
        if (prefs.drivePcId.first() == null) {
            try {
                ensureLayout()
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: IOException) {
                AppLog.d("DriveTransport", "remote.json : boite aux lettres introuvable : ${e.message}")
                return null
            }
        }
        return readPcFile("remote.json", RemoteInfo.serializer())
    }

    private suspend fun <T> readPcFile(name: String, serializer: kotlinx.serialization.KSerializer<T>): T? {
        val pcId = prefs.drivePcId.first() ?: return null
        return try {
            val file = driveApi.findChild(pcId, name, folder = false, newest = true) ?: return null
            ProtocolJson.decodeFromString(serializer, driveApi.readText(file.id))
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("DriveTransport", "$name illisible : ${e.message}")
            null
        }
    }

    override suspend fun downloadResult(job: JobEntity, fileId: String, dest: File, onProgress: suspend (Long, Long) -> Unit) {
        FileOutputStream(dest).use { out ->
            driveApi.download(fileId, out, onProgress)
        }
    }

    override suspend fun deleteJobFolder(job: JobEntity) {
        val folderId = job.driveFolderId ?: findJobFolder(job.jobId) ?: return
        driveApi.delete(folderId)
    }
}
