package com.tmm.mobile.fake

import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.JobError
import com.tmm.mobile.protocol.PcIndex
import com.tmm.mobile.protocol.PcIndexChapter
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.QueueCounts
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.protocol.StatusJson

/**
 * Simule le cote TMM-Win du protocole (CdC 7.1-7.5) en agissant directement sur [FakeDrive],
 * comme le ferait la vraie application Windows en face du telephone.
 */
class FakePc(private val drive: FakeDrive, private val nowIso: () -> String) {

    private fun jobsId() = drive.childOf("root", "jobs")!!.id
    private fun pcId() = drive.childOf("root", "pc")!!.id

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

    fun finish(jobId: String, resultBytes: ByteArray, pages: Int = 1) {
        val request = readRequest(jobId)
        val folderId = jobFolderId(jobId)
        val cbz = FakeFile(name = "result.cbz", parents = mutableListOf(folderId), mimeType = "application/vnd.comicbook+zip", modifiedTime = nowIso(), bytes = resultBytes)
        drive.files[cbz.id] = cbz
        val result = ResultJson(
            jobId = jobId,
            finishedAt = nowIso(),
            mode = request.options.mode,
            file = null,
            localFolderName = request.mihonPath.series,
            chapterFileName = request.mihonPath.chapter,
            pages = pages,
        )
        writeJson(folderId, "result.json", ProtocolJson.encodeToString(ResultJson.serializer(), result).toByteArray())
    }

    fun fail(jobId: String, code: String, message: String) {
        val request = readRequest(jobId)
        writeStatus(jobId, StatusJson(jobId = jobId, updatedAt = nowIso(), mode = request.options.mode, state = "failed", error = JobError(code, message)))
    }

    fun writeHeartbeat(slots: Int = 1, active: List<String> = emptyList(), queueActive: Int = 0, queueWaiting: Int = 0, queueFailed: Int = 0) {
        val heartbeat = Heartbeat(
            pcName = "PC-TEST",
            appVersion = "1.0.0",
            updatedAt = nowIso(),
            slots = slots,
            activeJobIds = active,
            queue = QueueCounts(queueActive, queueWaiting, queueFailed),
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
}
