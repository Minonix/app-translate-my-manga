package com.tmm.mobile.transport

import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.protocol.CancelJson
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.PcIndex
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.RemoteInfo
import com.tmm.mobile.protocol.ReceivedJson
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.StatusJson
import java.io.File

// CdC 4.3. Deux implementations : DriveTransport (boite aux lettres Drive) et DirectTransport (HTTPS via Tailscale).
interface JobTransport {
    suspend fun ensureLayout(): LayoutInfo
    suspend fun accountEmail(): String?

    // uploadSource() et writeRequest() creent chacun le dossier du job s'ils recoivent un
    // jobFolderId nul (createFolder n'est pas idempotent, contrairement a ensureLayout) : pour un
    // job qui appelle les deux (mode translate), ensureJobFolder() doit etre appele une seule fois
    // en amont pour eviter deux dossiers Drive distincts portant le meme nom.
    suspend fun ensureJobFolder(jobId: String): String

    suspend fun uploadSource(
        job: JobEntity,
        file: File,
        onSession: suspend (String) -> Unit,
        onProgress: suspend (sent: Long, total: Long) -> Unit,
    ): String

    suspend fun writeRequest(jobFolderId: String?, request: RequestJson): String
    suspend fun writeCancel(job: JobEntity, cancel: CancelJson)
    suspend fun writeReceived(job: JobEntity, received: ReceivedJson)
    suspend fun writeCommand(command: SettingsCommand)
    suspend fun poll(since: String?): PollResult
    suspend fun readSettings(): PcSettings?
    suspend fun downloadResult(
        job: JobEntity,
        fileId: String,
        dest: File,
        onProgress: suspend (Long, Long) -> Unit,
    )

    suspend fun deleteJobFolder(job: JobEntity)
}

data class LayoutInfo(val created: Boolean)

data class PollResult(
    val cursor: String?,
    val statuses: List<StatusJson>,
    val results: List<Pair<ResultJson, String?>>,
    val heartbeat: Heartbeat?,
    val index: PcIndex?,
    val settings: PcSettings?,
    val protocolTooNew: Boolean,
    // pc/remote.json (Drive uniquement) : URL de la connexion directe publiee par le PC.
    val remote: RemoteInfo? = null,
)
