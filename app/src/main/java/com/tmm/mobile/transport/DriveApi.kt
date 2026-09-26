package com.tmm.mobile.transport

import com.tmm.mobile.core.AppLog
import java.io.IOException
import java.io.OutputStream
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

class AuthExpiredException(message: String) : Exception(message)
class SessionExpiredException(message: String) : Exception(message)

data class DriveFile(
    val id: String,
    val name: String,
    val parents: List<String> = emptyList(),
    val modifiedTime: String? = null,
    val size: Long? = null,
)

data class ChangesPage(val files: List<DriveFile>, val nextPageToken: String?)

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
        onResponse: (Response) -> T,
    ): T {
        val accessToken = token(false)
        val request = build().newBuilder().header("Authorization", "Bearer $accessToken").build()
        AppLog.d("DriveApi", "${request.method} ${request.url.encodedPath}")
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        response.use {
            AppLog.d("DriveApi", "${request.method} ${request.url.encodedPath} -> ${it.code}")
            when {
                it.code == 401 -> {
                    if (attempt > 0) throw AuthExpiredException("401 apres reauthentification")
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
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: (1L shl attempt)
            delay(retryAfterSeconds * 1000)
        }
        return execute(attempt + 1, build, onResponse)
    }

    suspend fun findChild(parentId: String, name: String, folder: Boolean): DriveFile? {
        val escapedName = name.replace("'", "\\'")
        var q = "name = '$escapedName' and '$parentId' in parents and trashed = false"
        if (folder) q += " and mimeType = 'application/vnd.google-apps.folder'"
        return execute(build = {
            Request.Builder().url(
                url("drive/v3/files", mapOf("q" to q, "fields" to "files(id,name,modifiedTime,size)", "spaces" to "drive")),
            ).get().build()
        }) { response ->
            val body = driveJson.decodeFromString(DriveFilesResponse.serializer(), response.body!!.string())
            body.files.firstOrNull()?.toDriveFile()
        }
    }

    suspend fun createFolder(parentId: String, name: String): String {
        val metadata = """{"name":${jsonString(name)},"mimeType":"application/vnd.google-apps.folder","parents":[${jsonString(parentId)}]}"""
        return execute(build = {
            Request.Builder().url(url("drive/v3/files", mapOf("fields" to "id")))
                .post(metadata.toRequestBody("application/json".toMediaType()))
                .build()
        }) { response ->
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
            val decoded = driveJson.decodeFromString(DriveFileEntry.serializer(), response.body!!.string())
            DriveFile(decoded.id, name, listOf(parentId), decoded.modifiedTime)
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

    suspend fun listChanged(sinceIso: String, pageToken: String?): ChangesPage {
        val q = "modifiedTime > '$sinceIso' and trashed = false"
        val query = buildMap {
            put("q", q)
            put("fields", "nextPageToken,files(id,name,parents,modifiedTime,size)")
            put("pageSize", "1000")
            put("spaces", "drive")
            if (pageToken != null) put("pageToken", pageToken)
        }
        return execute(build = { Request.Builder().url(url("drive/v3/files", query)).get().build() }) { response ->
            val decoded = driveJson.decodeFromString(DriveFilesResponse.serializer(), response.body!!.string())
            ChangesPage(decoded.files.map { it.toDriveFile() }, decoded.nextPageToken)
        }
    }

    suspend fun download(fileId: String, sink: OutputStream, onProgress: suspend (Long, Long) -> Unit) {
        execute(build = {
            Request.Builder().url(url("drive/v3/files/$fileId", mapOf("alt" to "media"))).get().build()
        }) { response ->
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
                }
                sent to total
            }
        }.also { (sent, total) -> onProgress(sent, total) }
    }

    suspend fun readText(fileId: String): String {
        return execute(build = {
            Request.Builder().url(url("drive/v3/files/$fileId", mapOf("alt" to "media"))).get().build()
        }) { response -> response.body!!.string() }
    }

    suspend fun delete(fileId: String) {
        execute(build = { Request.Builder().url(url("drive/v3/files/$fileId")).delete().build() }) { }
    }

    suspend fun about(): String? = try {
        execute(build = {
            Request.Builder().url(url("drive/v3/about", mapOf("fields" to "user(emailAddress)"))).get().build()
        }) { response ->
            driveJson.decodeFromString(AboutResponse.serializer(), response.body!!.string()).user?.emailAddress
        }
    } catch (e: Exception) {
        null
    }

    private fun jsonString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
