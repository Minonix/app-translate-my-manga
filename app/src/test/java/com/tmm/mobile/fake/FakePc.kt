package com.tmm.mobile.fake

import com.tmm.mobile.protocol.CommandResult
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.JobError
import com.tmm.mobile.protocol.PcIndex
import com.tmm.mobile.protocol.PcIndexChapter
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.Quality
import com.tmm.mobile.protocol.QueueCounts
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.SettingsValues
import com.tmm.mobile.protocol.StatusJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Simule le cote TMM-Win du protocole (CdC 7.1-7.5) en agissant directement sur [FakeDrive],
 * comme le ferait la vraie application Windows en face du telephone.
 */
class FakePc(private val drive: FakeDrive, private val nowIso: () -> String) {

    private fun appRootId(): String {
        val appId = drive.childOf("root", "App")!!.id
        return drive.childOf(appId, "TranslateMyManga")!!.id
    }
    private fun jobsId() = drive.childOf(appRootId(), "jobs")!!.id
    private fun pcId() = drive.childOf(appRootId(), "pc")!!.id

    private fun jobFolderId(jobId: String) = drive.childOf(jobsId(), jobId)?.id
        ?: error("dossier de job introuvable : $jobId")

    fun readRequest(jobId: String): RequestJson {
        val file = drive.childOf(jobFolderId(jobId), "request.json") ?: error("request.json absent pour $jobId")
        return ProtocolJson.decodeFromString(RequestJson.serializer(), String(file.bytes))
    }

    private fun writeJson(parentId: String, name: String, bytes: ByteArray) {
        val file = drive.childOf(parentId, name) ?: FakeFile(name = name, parents = mutableListOf(parentId), mimeType = "application/json", modifiedTime = nowIso())
        file.bytes = bytes
        file.modifiedTime = nowIso()
        drive.files[file.id] = file
    }

    fun writeStatus(jobId: String, status: StatusJson) {
        writeJson(jobFolderId(jobId), "status.json", ProtocolJson.encodeToString(StatusJson.serializer(), status).toByteArray())
    }

    fun accept(jobId: String, engine: String = "gemini") {
        val request = readRequest(jobId)
        writeStatus(jobId, StatusJson(jobId = jobId, updatedAt = nowIso(), mode = request.options.mode, state = "processing", stage = "ocr", engine = engine))
    }

    // fileRef non nul simule un resultat "avec fichier" (translate) : ecrit alors result.cbz avec
    // le nom Drive attendu par InstallJobWorker.downloadResult (recherche par fileId, pas par nom).
    fun finish(jobId: String, resultBytes: ByteArray, pages: Int = 1, fileRef: FileRef? = null, quality: Quality? = null): String? {
        val request = readRequest(jobId)
        val folderId = jobFolderId(jobId)
        var resultFileId: String? = null
        if (fileRef != null) {
            val cbz = FakeFile(name = "result.cbz", parents = mutableListOf(folderId), mimeType = "application/vnd.comicbook+zip", modifiedTime = nowIso(), bytes = resultBytes)
            drive.files[cbz.id] = cbz
            resultFileId = cbz.id
        }
        val result = ResultJson(
            jobId = jobId,
            finishedAt = nowIso(),
            mode = request.options.mode,
            file = fileRef,
            localFolderName = request.mihonPath.series,
            chapterFileName = request.mihonPath.chapter,
            pages = pages,
            quality = quality,
        )
        writeJson(folderId, "result.json", ProtocolJson.encodeToString(ResultJson.serializer(), result).toByteArray())
        return resultFileId
    }

    fun fail(jobId: String, code: String, message: String) {
        val request = readRequest(jobId)
        writeStatus(jobId, StatusJson(jobId = jobId, updatedAt = nowIso(), mode = request.options.mode, state = "failed", error = JobError(code, message)))
    }

    fun writeHeartbeat(
        slots: Int = 1,
        active: List<String> = emptyList(),
        queueActive: Int = 0,
        queueWaiting: Int = 0,
        queueFailed: Int = 0,
        driveOk: Boolean? = null,
        lastErrorCode: String? = null,
    ) {
        val heartbeat = Heartbeat(
            pcName = "PC-TEST",
            appVersion = "1.0.0",
            updatedAt = nowIso(),
            slots = slots,
            activeJobIds = active,
            queue = QueueCounts(queueActive, queueWaiting, queueFailed),
            driveOk = driveOk,
            lastErrorCode = lastErrorCode,
        )
        writeJson(pcId(), "heartbeat.json", ProtocolJson.encodeToString(Heartbeat.serializer(), heartbeat).toByteArray())
    }

    fun writeIndex(chapters: List<PcIndexChapter>) {
        val index = PcIndex(updatedAt = nowIso(), chapters = chapters)
        writeJson(pcId(), "index.json", ProtocolJson.encodeToString(PcIndex.serializer(), index).toByteArray())
    }

    fun writeSettings(settings: PcSettings) {
        writeJson(pcId(), "settings.json", ProtocolJson.encodeToString(PcSettings.serializer(), settings).toByteArray())
    }

    // R12 : un cycle PC sur pc/commands comme bridge.py (_apply_commands/_run_command) : chaque commande
    // du dossier est traitee puis supprimee ; baseVersion != version -> rejected/stale_version, sinon
    // appliquee et version + 1. Seul le dernier accuse reste visible dans settings.json.
    fun processCommands(current: PcSettings): PcSettings {
        val commandsId = drive.childOf(pcId(), "commands")?.id ?: return current
        var settings = current
        val files = drive.files.values.filter { commandsId in it.parents }.sortedBy { it.modifiedTime }
        for (file in files) {
            val command = ProtocolJson.decodeFromString(SettingsCommand.serializer(), String(file.bytes))
            drive.files.remove(file.id)
            settings = if (command.baseVersion != settings.version) {
                settings.copy(lastCommandId = command.cmdId, lastCommandResult = CommandResult(command.cmdId, "rejected", "stale_version"))
            } else {
                val values = ProtocolJson.encodeToJsonElement(SettingsValues.serializer(), settings.values).jsonObject
                val merged = JsonObject(values + command.changes.mapValues { (k, v) -> coerce(values.getValue(k), v) })
                settings.copy(
                    version = settings.version + 1,
                    values = ProtocolJson.decodeFromJsonElement(SettingsValues.serializer(), merged),
                    lastCommandId = command.cmdId,
                    lastCommandResult = CommandResult(command.cmdId, "applied"),
                )
            }
        }
        writeSettings(settings.copy(updatedAt = nowIso()))
        return settings
    }

    // Le telephone envoie des chaines ("2", "true") ; le PC les convertit au type du reglage.
    private fun coerce(old: kotlinx.serialization.json.JsonElement, value: kotlinx.serialization.json.JsonElement) =
        if (old is kotlinx.serialization.json.JsonPrimitive && !old.isString) {
            kotlinx.serialization.json.Json.parseToJsonElement((value as kotlinx.serialization.json.JsonPrimitive).content)
        } else {
            value
        }

    // R12 : accuse d'une commande de reglages (lastCommandId + lastCommandResult), ecrit comme le PC
    // avant de supprimer la commande. status "applied" | "rejected", code de rejet sinon null.
    fun acknowledgeCommand(base: PcSettings, cmdId: String, status: String, code: String? = null, version: Int = base.version) {
        writeSettings(
            base.copy(
                updatedAt = nowIso(),
                version = version,
                lastCommandId = cmdId,
                lastCommandResult = CommandResult(cmdId = cmdId, status = status, code = code),
            ),
        )
    }

    // R3 : second fichier de meme nom dans le dossier du job (ecriture concurrente cote PC), avec son
    // propre modifiedTime ; contenu brut pour pouvoir aussi simuler un JSON vide ou tronque.
    fun writeDuplicateJobFile(jobId: String, name: String, content: String, modifiedTime: String): FakeFile {
        val file = FakeFile(name = name, parents = mutableListOf(jobFolderId(jobId)), mimeType = "application/json", modifiedTime = modifiedTime, bytes = content.toByteArray())
        drive.files[file.id] = file
        return file
    }

    fun statusJson(jobId: String, state: String, updatedAt: String = nowIso(), error: JobError? = null): String =
        ProtocolJson.encodeToString(StatusJson.serializer(), StatusJson(jobId = jobId, updatedAt = updatedAt, mode = "translate", state = state, error = error))
}
