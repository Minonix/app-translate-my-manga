package com.tmm.mobile.transport

import com.tmm.mobile.core.AppLog
import java.io.IOException
import java.io.OutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Client HTTP de Drive. Les delais par defaut d'OkHttp (10 s) coupaient sur un lien lent (4G faible) la
 * reponse de Google apres un morceau de 8 Mo ou l'ouverture de connexion ; l'upload resumable reprend
 * ensuite au bon octet, donc des delais longs ne coutent rien quand le reseau est bon.
 */
fun driveHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

class AuthExpiredException(message: String) : Exception(message)
class SessionExpiredException(message: String) : Exception(message)

/** R5 : 403 storageQuotaExceeded, Drive plein (code local `drive_quota`). */
class DriveQuotaException(message: String) : IOException(message)

/** R5 : 404 traite explicitement par l'appelant, jamais ecrit dans un fichier telecharge. */
class DriveNotFoundException(message: String) : IOException(message)

data class DriveFile(
    val id: String,
    val name: String,
    val parents: List<String> = emptyList(),
    val modifiedTime: String? = null,
    val size: Long? = null,
)

// serverTime : en-tete `Date` de la reponse Drive (R9 : heure serveur, pas l'horloge du telephone).
data class ChangesPage(val files: List<DriveFile>, val nextPageToken: String?, val serverTime: String? = null)

sealed interface ChunkResult {
    data class Complete(val fileId: String) : ChunkResult
    data class Incomplete(val nextOffset: Long) : ChunkResult
}

private val driveJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class DriveFileEntry(
    val id: String,
    val name: String? = null,
    val parents: List<String>? = null,
    val modifiedTime: String? = null,
    val size: String? = null,
) {
    fun toDriveFile() = DriveFile(id, name ?: "", parents ?: emptyList(), modifiedTime, size?.toLongOrNull())
}

@Serializable
private data class DriveFilesResponse(val files: List<DriveFileEntry> = emptyList(), val nextPageToken: String? = null)

@Serializable
private data class IdResponse(val id: String)

@Serializable
private data class DriveErrorReason(val reason: String? = null)

@Serializable
private data class DriveErrorDetail(val errors: List<DriveErrorReason> = emptyList())

@Serializable
private data class DriveErrorBody(val error: DriveErrorDetail? = null)

private val RATE_LIMIT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded")

@Serializable
private data class AboutResponse(val user: AboutUser? = null)

@Serializable
private data class AboutUser(val emailAddress: String? = null)

/**
 * Requetes REST Drive v3 (plan 5.10). Ce sont les SEULES formes de requetes emises par le
 * client : le faux Drive des tests (E7) ne reconnait qu'elles.
 */
class DriveApi(
    private val client: OkHttpClient,
    private val token: suspend (forceRefresh: Boolean) -> String,
    private val baseUrl: HttpUrl,
    // R13 : jeton refuse (401 apres renouvellement, 403 d'autorisation) invalide aupres de Play Services.
    private val onTokenRejected: suspend () -> Unit = {},
) {
    private fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        path.split("/").forEach { builder.addPathSegment(it) }
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private suspend fun <T> execute(
        attempt: Int = 0,
        build: () -> Request,
        onResponse: suspend (Response) -> T,
    ): T {
        val accessToken = token(false)
        val request = build().newBuilder().header("Authorization", "Bearer $accessToken").build()
        AppLog.d("DriveApi", "${request.method} ${request.url.encodedPath}")
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        response.use {
            AppLog.d("DriveApi", "${request.method} ${request.url.encodedPath} -> ${it.code}")
            when {
                it.code == 401 -> {
                    if (attempt > 0) {
                        onTokenRejected()
                        throw AuthExpiredException("401 apres reauthentification")
                    }
                }
                // R5 : un 403 n'est jamais un succes ; sa raison decide entre backoff, Drive plein et auth.
                it.code == 403 -> {
                    val reason = errorReason(it)
                    when (reason) {
                        in RATE_LIMIT_REASONS -> if (attempt >= 5) throw IOException("echec Drive apres 5 essais : 403 $reason")
                        "storageQuotaExceeded" -> throw DriveQuotaException("Drive plein (403 storageQuotaExceeded)")
                        else -> {
                            onTokenRejected()
                            throw AuthExpiredException("403 ${reason ?: "sans raison"}")
                        }
                    }
                }
                it.code == 429 || it.code in 500..599 -> {
                    if (attempt >= 5) throw IOException("echec Drive apres 5 essais : ${it.code}")
                }
                else -> return onResponse(it)
            }
        }
        if (response.code == 401) {
            token(true)
        } else {
            delay(retryAfterMillis(response.header("Retry-After"), attempt))
        }
        return execute(attempt + 1, build, onResponse)
    }

    private fun errorReason(response: Response): String? = runCatching {
        driveJson.decodeFromString(DriveErrorBody.serializer(), response.body!!.string()).error?.errors?.firstOrNull()?.reason
    }.getOrNull()

    // R5 : Retry-After en secondes ou en date HTTP ; borne a 5 min pour ne pas figer un worker.
    private fun retryAfterMillis(header: String?, attempt: Int): Long {
        val value = header?.trim()
        value?.toLongOrNull()?.let { return (it * 1000).coerceIn(0, MAX_RETRY_AFTER_MS) }
        if (value != null) {
            runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
                .getOrNull()
                ?.let { return (it - System.currentTimeMillis()).coerceIn(0, MAX_RETRY_AFTER_MS) }
        }
        return (1L shl attempt) * 1000
    }

    /** 404 -> [DriveNotFoundException], autre statut hors 2xx -> IOException : jamais decode comme un succes. */
    private fun requireOk(response: Response, what: String) {
        if (response.code == 404) throw DriveNotFoundException("$what introuvable sur Drive (404)")
        if (!response.isSuccessful) throw IOException("$what : reponse Drive ${response.code}")
    }

    // Le plus ancien d'abord, comme le PC : en cas de doublon, les deux retombent sur le meme dossier.
    // Le nom est recompare ici car "name =" de Drive ne distingue pas forcement "App" de "APP".
    // R3 : newest = true pour un fichier reecrit en place (settings, remote...) : modifiedTime le plus recent.
    suspend fun findChild(parentId: String, name: String, folder: Boolean, newest: Boolean = false): DriveFile? {
        val escapedName = name.replace("'", "\\'")
        var q = "name = '$escapedName' and '$parentId' in parents and trashed = false"
        if (folder) q += " and mimeType = 'application/vnd.google-apps.folder'"
        return execute(build = {
            Request.Builder().url(
                url(
                    "drive/v3/files",
                    mapOf("q" to q, "fields" to "files(id,name,modifiedTime,size)", "spaces" to "drive", "orderBy" to "createdTime"),
                ),
            ).get().build()
        }) { response ->
            requireOk(response, "recherche de $name")
            val body = driveJson.decodeFromString(DriveFilesResponse.serializer(), response.body!!.string())
            val matches = body.files.filter { it.name == name }
            (if (newest) matches.maxByOrNull { it.modifiedTime.orEmpty() } else matches.firstOrNull())?.toDriveFile()
        }
    }

    /** Ids des sous-dossiers directs de `parentId` (toutes les pages). */
    suspend fun listChildFolderIds(parentId: String): Set<String> = listChildFolders(parentId).mapTo(HashSet()) { it.id }

    /** Sous-dossiers directs de `parentId` (toutes les pages), avec leur nom. */
    suspend fun listChildFolders(parentId: String): List<DriveFile> {
        val q = "'$parentId' in parents and trashed = false and mimeType = 'application/vnd.google-apps.folder'"
        val folders = ArrayList<DriveFile>()
        var pageToken: String? = null
        do {
            val query = buildMap {
                put("q", q)
                put("fields", "nextPageToken,files(id,name)")
                put("pageSize", "1000")
                put("spaces", "drive")
                pageToken?.let { put("pageToken", it) }
            }
            pageToken = execute(build = { Request.Builder().url(url("drive/v3/files", query)).get().build() }) { response ->
                requireOk(response, "liste des dossiers")
                val decoded = driveJson.decodeFromString(DriveFilesResponse.serializer(), response.body!!.string())
                decoded.files.mapTo(folders) { it.toDriveFile() }
                decoded.nextPageToken
            }
        } while (pageToken != null)
        return folders
    }

    suspend fun createFolder(parentId: String, name: String): String {
        val metadata = """{"name":${jsonString(name)},"mimeType":"application/vnd.google-apps.folder","parents":[${jsonString(parentId)}]}"""
        return execute(build = {
            Request.Builder().url(url("drive/v3/files", mapOf("fields" to "id")))
                .post(metadata.toRequestBody("application/json".toMediaType()))
                .build()
        }) { response ->
            requireOk(response, "creation du dossier $name")
            driveJson.decodeFromString(IdResponse.serializer(), response.body!!.string()).id
        }
    }

    suspend fun createJson(parentId: String, name: String, bytes: ByteArray): DriveFile {
        val metadata = """{"name":${jsonString(name)},"parents":[${jsonString(parentId)}],"mimeType":"application/json"}"""
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/json".toMediaType()))
            .build()
        return execute(build = {
            Request.Builder().url(
                url("upload/drive/v3/files", mapOf("uploadType" to "multipart", "fields" to "id,modifiedTime")),
            ).post(body).build()
        }) { response ->
            requireOk(response, "ecriture de $name")
            val decoded = driveJson.decodeFromString(DriveFileEntry.serializer(), response.body!!.string())
            DriveFile(decoded.id, name, listOf(parentId), decoded.modifiedTime)
        }
    }

    /** R1 : remplace le contenu d'un fichier existant (meme fileId). 404 -> [DriveNotFoundException]. */
    suspend fun updateJson(fileId: String, bytes: ByteArray): DriveFile {
        return execute(build = {
            Request.Builder().url(
                url("upload/drive/v3/files/$fileId", mapOf("uploadType" to "media", "fields" to "id,name,modifiedTime")),
            ).patch(bytes.toRequestBody("application/json".toMediaType())).build()
        }) { response ->
            requireOk(response, "mise a jour du fichier $fileId")
            driveJson.decodeFromString(DriveFileEntry.serializer(), response.body!!.string()).toDriveFile()
        }
    }

    suspend fun startResumable(parentId: String, name: String, size: Long, mimeType: String): String {
        val metadata = """{"name":${jsonString(name)},"parents":[${jsonString(parentId)}]}"""
        return execute(build = {
            Request.Builder().url(url("upload/drive/v3/files", mapOf("uploadType" to "resumable")))
                .header("X-Upload-Content-Type", mimeType)
                .header("X-Upload-Content-Length", size.toString())
                .post(metadata.toRequestBody("application/json".toMediaType()))
                .build()
        }) { response ->
            requireOk(response, "ouverture de l'envoi de $name")
            response.header("Location") ?: throw IOException("session resumable sans en-tete Location")
        }
    }

    suspend fun putChunk(session: String, bytes: ByteArray, offset: Long, total: Long): ChunkResult {
        val end = offset + bytes.size - 1
        return execute(build = {
            Request.Builder().url(session)
                .header("Content-Range", "bytes $offset-$end/$total")
                .put(bytes.toRequestBody(null))
                .build()
        }) { response -> parseChunkResponse(response) }
    }

    suspend fun queryResumable(session: String, total: Long): ChunkResult {
        return execute(build = {
            Request.Builder().url(session)
                .header("Content-Range", "bytes */$total")
                .put(ByteArray(0).toRequestBody(null))
                .build()
        }) { response ->
            if (response.code == 404 || response.code == 410) {
                throw SessionExpiredException("session resumable expiree (${response.code})")
            }
            parseChunkResponse(response)
        }
    }

    private fun parseChunkResponse(response: Response): ChunkResult {
        return when {
            response.code == 200 || response.code == 201 -> {
                val id = driveJson.decodeFromString(IdResponse.serializer(), response.body!!.string()).id
                ChunkResult.Complete(id)
            }
            response.code == 308 -> {
                val range = response.header("Range")
                val nextOffset = range?.substringAfter('-')?.toLongOrNull()?.plus(1) ?: 0L
                ChunkResult.Incomplete(nextOffset)
            }
            else -> throw IOException("reponse inattendue lors de l'envoi resumable : ${response.code}")
        }
    }

    // Avec l'acces Drive complet, sans filtre de nom on listerait tout fichier modifie du compte.
    suspend fun listChanged(sinceIso: String, pageToken: String?, names: List<String>): ChangesPage {
        val nameClause = names.joinToString(" or ") { "name = '$it'" }
        val q = "modifiedTime > '$sinceIso' and trashed = false and ($nameClause)"
        val query = buildMap {
            put("q", q)
            put("fields", "nextPageToken,files(id,name,parents,modifiedTime,size)")
            put("pageSize", "1000")
            put("spaces", "drive")
            if (pageToken != null) put("pageToken", pageToken)
        }
        return execute(build = { Request.Builder().url(url("drive/v3/files", query)).get().build() }) { response ->
            requireOk(response, "liste des changements")
            val decoded = driveJson.decodeFromString(DriveFilesResponse.serializer(), response.body!!.string())
            ChangesPage(decoded.files.map { it.toDriveFile() }, decoded.nextPageToken, response.header("Date"))
        }
    }

    suspend fun download(fileId: String, sink: OutputStream, onProgress: suspend (Long, Long) -> Unit) {
        execute(build = {
            Request.Builder().url(url("drive/v3/files/$fileId", mapOf("alt" to "media"))).get().build()
        }) { response ->
            // R5 : un corps d'erreur n'est jamais ecrit dans le fichier de destination.
            requireOk(response, "fichier $fileId")
            val body = response.body!!
            val total = body.contentLength()
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    sent += read
                    // Progression par bloc (l'appelant limite les ecritures en base) : un seul appel en
                    // fin de flux faisait sauter la barre de 0 a 100 %.
                    onProgress(sent, total)
                }
            }
        }
    }

    suspend fun readText(fileId: String): String {
        return execute(build = {
            Request.Builder().url(url("drive/v3/files/$fileId", mapOf("alt" to "media"))).get().build()
        }) { response ->
            requireOk(response, "fichier $fileId")
            response.body!!.string()
        }
    }

    // Deja supprime (404) = objectif atteint ; tout autre echec remonte (l'appelant sait que rien n'a ete supprime).
    suspend fun delete(fileId: String) {
        execute(build = { Request.Builder().url(url("drive/v3/files/$fileId")).delete().build() }) { response ->
            if (response.code != 404) requireOk(response, "suppression de $fileId")
        }
    }

    suspend fun about(): String? = try {
        execute(build = {
            Request.Builder().url(url("drive/v3/about", mapOf("fields" to "user(emailAddress)"))).get().build()
        }) { response ->
            requireOk(response, "compte")
            driveJson.decodeFromString(AboutResponse.serializer(), response.body!!.string()).user?.emailAddress
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_RETRY_AFTER_MS = 5 * 60 * 1000L
    }

    private fun jsonString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
