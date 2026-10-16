package com.tmm.mobile.transport

import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.protocol.CancelJson
import com.tmm.mobile.protocol.DirectPollResponse
import com.tmm.mobile.protocol.HealthResponse
import com.tmm.mobile.protocol.PairRequest
import com.tmm.mobile.protocol.PairResponse
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.PhoneInventory
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ReceivedJson
import com.tmm.mobile.protocol.RemoteErrorBody
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.StatusJson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source

// Le PC a refuse le jeton (401) : il est efface et l'appairage doit etre refait. IOException pour que
// les workers le traitent comme un echec d'envoi (retry borne, puis FAILED) et non comme un crash.
class DirectPairingExpiredException(message: String) : IOException(message)

// Erreur renvoyee par le PC : {"error":{"code","message"}} (bad_request, integrity, other_channel...).
class DirectHttpException(val status: Int, val code: String?, message: String) : IOException(message)

private val JSON_TYPE = "application/json".toMediaType()
private val CBZ_TYPE = "application/vnd.comicbook+zip".toMediaType()
private const val API_PATH = "/api/remote/v1/"

// Saisie libre (avec ou sans schema, avec ou sans chemin) -> racine de l'API, ou null si illisible.
fun normalizeDirectUrl(input: String): HttpUrl? {
    var text = input.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "https://$text"
    return text.toHttpUrlOrNull()?.newBuilder()?.encodedPath(API_PATH)?.query(null)?.fragment(null)?.build()
}

/**
 * Connexion directe au PC (Tailscale) : memes contrats JSON que Drive, sur HTTPS.
 * Un job reste sur le canal choisi a l'envoi ; ce transport ne sert que les jobs `channel == "direct"`.
 * ponytail: l'envoi n'est pas reprenable (un PUT unique) ; sur une coupure, le renvoi repart de zero.
 * A revoir si des chapitres de plusieurs centaines de Mo passent souvent par une 4G instable.
 */
class DirectTransport(
    private val client: OkHttpClient,
    private val prefs: AppPrefs,
    private val tokens: DirectTokenStore,
    healthTimeoutSeconds: Long = 6,
) : JobTransport {

    // Client dedie : le health-check repond en 6 s au plus (2 s ratait un Tailscale qui se reveille), jamais
    // les delais d'un transfert.
    private val healthClient = client.newBuilder()
        .connectTimeout(healthTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(healthTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(healthTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    suspend fun baseUrl(): HttpUrl? = prefs.directUrl.first()?.let(::normalizeDirectUrl)

    suspend fun isPaired(): Boolean = baseUrl() != null && tokens.get() != null

    // Joignable en moins de 2 s : null sinon (URL/jeton absents, reseau coupe, reponse illisible).
    suspend fun health(): HealthResponse? {
        val base = baseUrl() ?: return null
        val request = Request.Builder().url(endpoint(base, "health")).get().build()
        return try {
            withContext(Dispatchers.IO) { healthClient.newCall(request).execute() }.use {
                if (!it.isSuccessful) null else ProtocolJson.decodeFromString(HealthResponse.serializer(), it.body.string())
            }
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        }
    }

    // Echange le code a 6 chiffres contre le jeton, puis memorise URL, PC et jeton (chiffre).
    // Le 401 d'un mauvais code n'efface rien : ce n'est pas un jeton refuse.
    suspend fun pair(urlInput: String, code: String, deviceName: String): PairResponse {
        val base = requireNotNull(normalizeDirectUrl(urlInput)) { "adresse du PC illisible" }
        val deviceId = prefs.directDeviceId.first() ?: UUID.randomUUID().toString().also { prefs.setDirectDeviceId(it) }
        val body = ProtocolJson.encodeToString(PairRequest.serializer(), PairRequest(code = code, deviceId = deviceId, deviceName = deviceName))
        val response = execute(
            Request.Builder().url(endpoint(base, "pair")).post(body.toRequestBody(JSON_TYPE)).build(),
            authed = false,
        ) { ProtocolJson.decodeFromString(PairResponse.serializer(), it.body.string()) }
        tokens.set(response.token)
        prefs.setDirectUrl(base.toString())
        prefs.setDirectPcName(response.pcName)
        return response
    }

    suspend fun forget() {
        tokens.clear()
        prefs.setDirectUrl(null)
        prefs.setDirectPcName(null)
        prefs.setDirectPairingExpired(false)
    }

    suspend fun putInventory(inventory: PhoneInventory) {
        val body = ProtocolJson.encodeToString(PhoneInventory.serializer(), inventory).toRequestBody(JSON_TYPE)
        call("inventory") { put(body) }
    }

    override suspend fun ensureLayout(): LayoutInfo = LayoutInfo(created = false)

    override suspend fun accountEmail(): String? = null

    override suspend fun ensureJobFolder(jobId: String): String = jobId

    override suspend fun uploadSource(
        job: JobEntity,
        file: File,
        onSession: suspend (String) -> Unit,
        onProgress: suspend (sent: Long, total: Long) -> Unit,
    ): String {
        val sha = requireNotNull(job.sha256) { "job sans sha256 : envoi direct impossible" }
        val body = ProgressFileBody(file, onProgress)
        call("jobs/${job.jobId}/source") { put(body).header("X-Sha256", sha) }
        onProgress(file.length(), file.length())
        return job.jobId
    }

    override suspend fun writeRequest(jobFolderId: String?, request: RequestJson): String {
        val body = ProtocolJson.encodeToString(RequestJson.serializer(), request).toRequestBody(JSON_TYPE)
        val response = call("jobs/${request.jobId}") { post(body) }
        // Refus de job renvoye en HTTP 200 : corps = status.json "failed" avec error.code (meme contrat
        // que sur Drive). Leve avec le statut 200 pour que SendJobWorker le classe comme un refus PC.
        val status = runCatching { ProtocolJson.decodeFromString(StatusJson.serializer(), response) }.getOrNull()
        val error = status?.error
        if (status?.state == "failed" && error != null) throw DirectHttpException(200, error.code, error.message)
        return request.jobId
    }

    // 404 : le PC ne connait pas (ou plus) ce job, il n'y a rien a annuler.
    override suspend fun writeCancel(job: JobEntity, cancel: CancelJson) {
        val body = ProtocolJson.encodeToString(CancelJson.serializer(), cancel).toRequestBody(JSON_TYPE)
        try {
            call("jobs/${job.jobId}/cancel") { post(body) }
        } catch (e: DirectHttpException) {
            if (e.status != 404) throw e
        }
    }

    // 404 : le PC a deja clos ce job ; sans cette tolerance, OutboxWorker echouerait sur lui a chaque
    // passage et n'atteindrait plus les jobs suivants.
    override suspend fun writeReceived(job: JobEntity, received: ReceivedJson) {
        val body = ProtocolJson.encodeToString(ReceivedJson.serializer(), received).toRequestBody(JSON_TYPE)
        try {
            call("jobs/${job.jobId}/received") { post(body) }
        } catch (e: DirectHttpException) {
            if (e.status != 404) throw e
        }
    }

    override suspend fun writeCommand(command: SettingsCommand) {
        val body = ProtocolJson.encodeToString(SettingsCommand.serializer(), command).toRequestBody(JSON_TYPE)
        call("commands") { post(body) }
    }

    // `since` est ignore par le PC (il renvoie tout) et le curseur direct n'est pas celui de Drive :
    // RefreshCoordinator ne le persiste pas. Pour un resultat, resultFileId prend la valeur du jobId
    // (le PC retrouve le fichier a partir du job, jamais d'un chemin fourni par le client).
    override suspend fun poll(since: String?): PollResult {
        val text = call("poll") { get() }
        val doc = ProtocolJson.decodeFromString(DirectPollResponse.serializer(), text)
        var tooNew = false
        fun <T> keep(protocol: Int, value: T): T? = if (protocol > 1) { tooNew = true; null } else value
        val statuses = doc.statuses.mapNotNull { keep(it.protocol, it) }
        val results = doc.results.mapNotNull { r -> keep(r.protocol, r)?.let { it to it.jobId } }
        val heartbeat = doc.heartbeat?.let { keep(it.protocol, it) }
        val index = doc.index?.let { keep(it.protocol, it) }
        val settings = doc.settings?.let { keep(it.protocol, it) }
        return PollResult(doc.cursor, statuses, results, heartbeat, index, settings, tooNew, protocolSeen = heartbeat != null && !tooNew)
    }

    // ponytail: un poll complet pour relire les reglages (index inclus). Suffisant : le PC applique une
    // commande de facon synchrone, la premiere lecture confirme. Route dediee si l'index devient lourd.
    override suspend fun readSettings(): PcSettings? = poll(null).settings

    override suspend fun downloadResult(job: JobEntity, fileId: String, dest: File, onProgress: suspend (Long, Long) -> Unit) {
        val request = authorized(Request.Builder().url(endpoint(requireBase(), "jobs/${job.jobId}/result.cbz")).get().build())
        withContext(Dispatchers.IO) { client.newCall(request).execute() }.use { response ->
            ensureOk(request, response)
            val total = response.body.contentLength()
            var done = 0L
            FileOutputStream(dest).use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
        }
    }

    // Le PC clot le job a la reception de `received` : rien a supprimer cote canal.
    override suspend fun deleteJobFolder(job: JobEntity) = Unit

    private suspend fun requireBase(): HttpUrl =
        baseUrl() ?: throw DirectPairingExpiredException("aucun PC associe pour la connexion directe")

    private fun endpoint(base: HttpUrl, path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()

    private suspend fun authorized(request: Request): Request {
        val token = tokens.get() ?: throw DirectPairingExpiredException("PC non associe")
        return request.newBuilder().header("Authorization", "Bearer $token").build()
    }

    private suspend fun call(path: String, configure: Request.Builder.() -> Request.Builder): String {
        // Ne pas nommer ce parametre "build" : dans un bloc a receveur Request.Builder, "build()"
        // appelait Request.Builder.build() et ignorait put/post, d'ou des GET sans corps (405 cote PC).
        val request = authorized(Request.Builder().url(endpoint(requireBase(), path)).configure().build())
        return execute(request, authed = true) { it.body.string() }
    }

    private suspend fun <T> execute(request: Request, authed: Boolean, onResponse: (Response) -> T): T {
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        return response.use {
            ensureOk(request, it, authed)
            onResponse(it)
        }
    }

    // Journalise methode + chemin + code seulement : ni en-tete, ni corps, ni jeton.
    private suspend fun ensureOk(request: Request, response: Response, authed: Boolean = true) {
        AppLog.d("DirectTransport", "${request.method} ${request.url.encodedPath} -> ${response.code}")
        if (response.isSuccessful) return
        if (response.code == 401 && authed) {
            tokens.clear()
            prefs.setDirectPairingExpired(true)
            throw DirectPairingExpiredException("Appairage expire : associer a nouveau le PC")
        }
        val detail = try {
            ProtocolJson.decodeFromString(RemoteErrorBody.serializer(), response.body.string()).error
        } catch (e: Exception) {
            null
        }
        throw DirectHttpException(response.code, detail?.code, detail?.message ?: "erreur ${response.code}")
    }

    // Corps du PUT source : flux du fichier avec progression. Content-Length vient de contentLength().
    private class ProgressFileBody(
        private val file: File,
        private val onProgress: suspend (Long, Long) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType = CBZ_TYPE
        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            val total = file.length()
            var sent = 0L
            var lastReport = 0L
            file.source().use { source ->
                while (true) {
                    val read = source.read(sink.buffer, 64L * 1024)
                    if (read < 0) break
                    sink.emit()
                    sent += read
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 250) {
                        lastReport = now
                        runBlocking { onProgress(sent, total) }
                    }
                }
            }
        }
    }
}
