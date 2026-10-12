package com.tmm.mobile.fake

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl
import okio.Buffer
import org.junit.Assert.fail

class FakeFile(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var parents: MutableList<String>,
    var mimeType: String,
    var modifiedTime: String,
    var bytes: ByteArray = ByteArray(0),
)

private class ResumableSession(val parentId: String, val name: String, val total: Long) {
    var received: Long = 0L
    var bytes: ByteArray = ByteArray(0)
}

/**
 * Faux Drive (E7, plan 5.10) : ne reconnait QUE les formes de requetes emises par [DriveApi].
 * Toute autre forme fait echouer le test explicitement plutot que de repondre au hasard.
 * ponytail: pas de vraie pagination Drive (une seule page toujours) ; suffisant pour les tests
 * de ce jalon, a etendre si un test exige plusieurs pages de listChanged.
 */
class FakeDrive(private val nowIso: () -> String) {
    val server = MockWebServer()
    val files = ConcurrentHashMap<String, FakeFile>()
    private val sessions = ConcurrentHashMap<String, ResumableSession>()

    var accountEmail: String? = "compte@example.com"
    var force401Once: Boolean = false
    var force429ForNextAttempts: Int = 0
    var cutoffUploadAfterBytes: Long? = null
    // Simule un "name =" Drive insensible a la casse (App et APP renvoyes ensemble).
    var caseInsensitiveNames: Boolean = false
    // Appele avant de servir le contenu d'un fichier : simule un changement d'etat pendant un telechargement.
    var beforeMediaGet: ((String) -> Unit)? = null
    // R5 : les N prochaines requetes repondent 403 avec cette raison (rateLimitExceeded, storageQuotaExceeded...).
    var force403Reason: String? = null
    var force403ForNextAttempts: Int = 0
    // Limite le 403 aux requetes dont le chemin commence par ce prefixe (null = toutes).
    var force403PathPrefix: String? = null
    // Statut renvoye par toute suppression (null = suppression normale, 404 si deja absent).
    var deleteFailsWith: Int? = null
    // Compteurs par forme de requete, pour verifier qu'aucune creation en double n'a lieu.
    val createdFolderNames = java.util.Collections.synchronizedList(mutableListOf<String>())
    // Fichiers JSON crees (parentId to nom) : survit a la suppression en cascade du dossier parent.
    val createdJsonFiles = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    init {
        files["root"] = FakeFile(id = "root", name = "root", parents = mutableListOf(), mimeType = FOLDER, modifiedTime = nowIso())
        // Dossier APP cree par l'utilisateur : l'app ne le cree jamais, elle s'y range.
        files[APP_ID] = FakeFile(id = APP_ID, name = "App", parents = mutableListOf("root"), mimeType = FOLDER, modifiedTime = nowIso())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
    }

    fun start() = server.start()
    fun shutdown() = server.close()
    fun baseUrl(): HttpUrl = server.url("/")

    fun childOf(parentId: String, name: String): FakeFile? = files.values.firstOrNull { it.name == name && parentId in it.parents }

    private fun handle(request: RecordedRequest): MockResponse {
        if (force401Once) {
            force401Once = false
            return MockResponse(code = 401)
        }
        if (force429ForNextAttempts > 0) {
            force429ForNextAttempts--
            return MockResponse.Builder().code(429).addHeader("Retry-After", "0").build()
        }
        val url = request.url
        val path = url.encodedPath
        if (force403ForNextAttempts > 0 && (force403PathPrefix == null || path.startsWith(force403PathPrefix!!))) {
            force403ForNextAttempts--
            val reason = force403Reason
            val body = if (reason != null) """{"error":{"code":403,"errors":[{"reason":"$reason"}]}}""" else "{}"
            return MockResponse(code = 403, body = body)
        }
        return when {
            path == "/drive/v3/files" && request.method == "GET" -> handleQuery(url)
            path == "/drive/v3/files" && request.method == "POST" -> handleCreateFolder(request)
            path == "/upload/drive/v3/files" && request.method == "POST" -> handleUpload(request, url)
            path.startsWith("/upload/drive/v3/files/") && request.method == "PATCH" && url.queryParameter("uploadType") == "media" ->
                handlePatch(request, path.removePrefix("/upload/drive/v3/files/"))
            path.startsWith("/upload/session/") && request.method == "PUT" -> handleChunk(request, path.removePrefix("/upload/session/"))
            path.startsWith("/drive/v3/files/") && request.method == "GET" -> handleGet(path.removePrefix("/drive/v3/files/"))
            path.startsWith("/drive/v3/files/") && request.method == "DELETE" -> handleDelete(path.removePrefix("/drive/v3/files/"))
            path == "/drive/v3/about" && request.method == "GET" -> MockResponse(
                code = 200,
                body = if (accountEmail != null) """{"user":{"emailAddress":"$accountEmail"}}""" else "{}",
            )
            else -> failRequest("forme de requete inconnue : ${request.method} $path?${url.query}")
        }
    }

    private val findRegex = Regex("""name = '(.*)' and '(.*)' in parents and trashed = false( and mimeType = 'application/vnd\.google-apps\.folder')?""")
    private val listRegex = Regex("""modifiedTime > '([^']*)' and trashed = false and \((name = '[^']*'(?: or name = '[^']*')*)\)""")
    private val childFoldersRegex = Regex("""'([^']*)' in parents and trashed = false and mimeType = 'application/vnd\.google-apps\.folder'""")

    private fun handleQuery(url: HttpUrl): MockResponse {
        val q = url.queryParameter("q") ?: return failRequest("q manquant")
        findRegex.matchEntire(q)?.let { m ->
            val name = m.groupValues[1].replace("\\'", "'")
            val parentId = m.groupValues[2]
            val folderOnly = m.groupValues[3].isNotEmpty()
            // Comme Drive, "name =" peut renvoyer plusieurs dossiers (doublons) : DriveApi trie lui-meme.
            val matches = files.values.filter {
                it.name.equals(name, ignoreCase = caseInsensitiveNames) && parentId in it.parents && (!folderOnly || it.mimeType == FOLDER)
            }.sortedBy { it.modifiedTime }
            return MockResponse(code = 200, body = filesJson(matches, null))
        }
        listRegex.matchEntire(q)?.let { m ->
            val since = m.groupValues[1]
            val names = Regex("""name = '([^']*)'""").findAll(m.groupValues[2]).map { it.groupValues[1] }.toSet()
            val entries = files.values.filter { it.id != "root" && it.name in names && it.modifiedTime > since }.sortedBy { it.modifiedTime }
            // R9 : en-tete Date = heure serveur (celle du faux Drive, pas l'horloge du telephone).
            val date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.parse(nowIso()).atZone(java.time.ZoneOffset.UTC))
            return MockResponse.Builder().code(200).addHeader("Date", date).body(filesJson(entries, null)).build()
        }
        childFoldersRegex.matchEntire(q)?.let { m ->
            val parentId = m.groupValues[1]
            val entries = files.values.filter { parentId in it.parents && it.mimeType == FOLDER }
            return MockResponse(code = 200, body = filesJson(entries, null))
        }
        return failRequest("forme de q inconnue : $q")
    }

    private fun handleCreateFolder(request: RecordedRequest): MockResponse {
        val body = request.body?.utf8() ?: ""
        val name = extractJsonString(body, "name") ?: return failRequest("name manquant : $body")
        val parentId = extractJsonArrayFirst(body, "parents") ?: return failRequest("parents manquant : $body")
        val file = FakeFile(name = name, parents = mutableListOf(parentId), mimeType = FOLDER, modifiedTime = nowIso())
        files[file.id] = file
        createdFolderNames += name
        return MockResponse(code = 200, body = """{"id":"${file.id}"}""")
    }

    private fun handlePatch(request: RecordedRequest, fileId: String): MockResponse {
        val file = files[fileId] ?: return MockResponse(code = 404)
        file.bytes = request.body?.toByteArray() ?: ByteArray(0)
        file.modifiedTime = nowIso()
        return MockResponse(code = 200, body = """{"id":"${file.id}","name":"${file.name}","modifiedTime":"${file.modifiedTime}"}""")
    }

    private fun handleUpload(request: RecordedRequest, url: HttpUrl): MockResponse {
        return when (url.queryParameter("uploadType")) {
            "multipart" -> handleMultipart(request)
            "resumable" -> handleStartResumable(request)
            else -> failRequest("uploadType inconnu : ${url.queryParameter("uploadType")}")
        }
    }

    private fun handleMultipart(request: RecordedRequest): MockResponse {
        val contentType = request.headers["Content-Type"] ?: return failRequest("Content-Type manquant")
        val boundary = Regex("boundary=(.*)").find(contentType)?.groupValues?.get(1)?.trim('"')
            ?: return failRequest("boundary manquant : $contentType")
        val raw = request.body?.utf8() ?: ""
        val parts = raw.split("--$boundary").filter { it.isNotBlank() && it.trim() != "--" }
        if (parts.size < 2) return failRequest("multipart/related attendu avec 2 parties, recu ${parts.size}")
        val metadata = parts[0].substringAfter("\r\n\r\n").trim()
        val content = parts[1].substringAfter("\r\n\r\n").removeSuffix("\r\n").trim()
        val name = extractJsonString(metadata, "name") ?: return failRequest("name manquant : $metadata")
        val parentId = extractJsonArrayFirst(metadata, "parents") ?: return failRequest("parents manquant : $metadata")
        val file = FakeFile(name = name, parents = mutableListOf(parentId), mimeType = "application/json", modifiedTime = nowIso(), bytes = content.toByteArray())
        files[file.id] = file
        createdJsonFiles += parentId to name
        return MockResponse(code = 200, body = """{"id":"${file.id}","modifiedTime":"${file.modifiedTime}"}""")
    }

    private fun handleStartResumable(request: RecordedRequest): MockResponse {
        val body = request.body?.utf8() ?: ""
        val name = extractJsonString(body, "name") ?: return failRequest("name manquant : $body")
        val parentId = extractJsonArrayFirst(body, "parents") ?: return failRequest("parents manquant : $body")
        val total = request.headers["X-Upload-Content-Length"]?.toLongOrNull()
            ?: return failRequest("X-Upload-Content-Length manquant")
        val token = UUID.randomUUID().toString()
        sessions[token] = ResumableSession(parentId, name, total)
        return MockResponse.Builder().code(200).addHeader("Location", server.url("/upload/session/$token").toString()).build()
    }

    private fun handleChunk(request: RecordedRequest, token: String): MockResponse {
        val session = sessions[token] ?: return MockResponse(code = 404)
        val range = request.headers["Content-Range"] ?: return failRequest("Content-Range manquant")
        if (range == "bytes */${session.total}") {
            return if (session.received >= session.total) {
                completeUpload(token, session)
            } else {
                MockResponse.Builder().code(308).addHeader("Range", "bytes=0-${session.received - 1}").build()
            }
        }
        val m = Regex("""bytes (\d+)-(\d+)/(\d+)""").matchEntire(range) ?: return failRequest("Content-Range invalide : $range")
        val offset = m.groupValues[1].toLong()
        val chunkBytes = request.body?.toByteArray() ?: ByteArray(0)
        if (offset != session.received) return failRequest("offset inattendu : recu $offset, attendu ${session.received}")
        val cutoff = cutoffUploadAfterBytes
        if (cutoff != null && session.received + chunkBytes.size > cutoff) {
            val allowed = (cutoff - session.received).toInt().coerceAtLeast(0)
            session.bytes += chunkBytes.copyOfRange(0, allowed)
            session.received += allowed
            cutoffUploadAfterBytes = null
            return MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
        }
        session.bytes += chunkBytes
        session.received += chunkBytes.size
        return if (session.received >= session.total) {
            completeUpload(token, session)
        } else {
            MockResponse.Builder().code(308).addHeader("Range", "bytes=0-${session.received - 1}").build()
        }
    }

    private fun completeUpload(token: String, session: ResumableSession): MockResponse {
        val file = FakeFile(name = session.name, parents = mutableListOf(session.parentId), mimeType = "application/vnd.comicbook+zip", modifiedTime = nowIso(), bytes = session.bytes)
        files[file.id] = file
        sessions.remove(token)
        return MockResponse(code = 200, body = """{"id":"${file.id}"}""")
    }

    private fun handleGet(fileId: String): MockResponse {
        val file = files[fileId] ?: return MockResponse(code = 404)
        beforeMediaGet?.invoke(fileId)
        return MockResponse.Builder().code(200).body(Buffer().write(file.bytes)).build()
    }

    private fun handleDelete(fileId: String): MockResponse {
        deleteFailsWith?.let { return MockResponse(code = it) }
        // Comme Drive : supprimer un dossier supprime son contenu ; un id inconnu repond 404.
        files.remove(fileId) ?: return MockResponse(code = 404)
        files.values.filter { fileId in it.parents }.forEach { files.remove(it.id) }
        return MockResponse(code = 204)
    }

    private fun filesJson(entries: List<FakeFile>, nextPageToken: String?): String {
        val items = entries.joinToString(",") {
            """{"id":"${it.id}","name":"${it.name}","parents":[${it.parents.joinToString(",") { p -> "\"$p\"" }}],"modifiedTime":"${it.modifiedTime}","size":"${it.bytes.size}"}"""
        }
        val next = if (nextPageToken != null) ""","nextPageToken":"$nextPageToken"""" else ""
        return """{"files":[$items]$next}"""
    }

    private fun extractJsonString(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")

    private fun extractJsonArrayFirst(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*\["((?:\\.|[^"\\])*)"""").find(json)?.groupValues?.get(1)

    companion object {
        const val FOLDER = "application/vnd.google-apps.folder"
        const val APP_ID = "app-folder"
    }

    private fun failRequest(message: String): MockResponse {
        fail(message)
        throw AssertionError(message)
    }
}
