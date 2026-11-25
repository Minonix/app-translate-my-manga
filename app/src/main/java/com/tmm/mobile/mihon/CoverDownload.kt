package com.tmm.mobile.mihon

import com.tmm.mobile.ui.components.newCoverHttpClient
import com.tmm.mobile.ui.components.normalizeCoverUrl
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

// Vignette reseau d'une serie (thumbnailUrl de la sauvegarde), posee comme cover.* dans local/<serie>/ :
// une page tiree d'un chapitre (credits sur fond noir, page de garde) faisait une mauvaise couverture.
// Partage par InstallJobWorker (installation d'un job) et DownloadScanner (scan de local/).

internal const val MAX_COVER_BYTES = 10L * 1024 * 1024

// Client du scan de demarrage : delai total court, une vignette injoignable ne bloque pas le scan.
private val scanCoverClient: OkHttpClient by lazy {
    newCoverHttpClient().newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
}

/** Couverture de la sauvegarde (extension, octets), ou null si absente, refusee ou trop lourde. */
fun downloadCover(thumbnailUrl: String?, client: OkHttpClient = scanCoverClient): Pair<String, ByteArray>? {
    val url = normalizeCoverUrl(thumbnailUrl) ?: return null
    return fetchCoverImage(url, client)
}

/** Telecharge [url] telle quelle (sans normalisation) : null si la reponse n'est pas une image lisible. */
internal fun fetchCoverImage(url: String, client: OkHttpClient): Pair<String, ByteArray>? = runCatching {
    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        val body = response.body
        val type = body.contentType()
        if (!response.isSuccessful || type?.type != "image" || body.contentLength() > MAX_COVER_BYTES) return@use null
        val bytes = body.bytes()
        if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return@use null
        imageExtension(bytes, type.subtype) to bytes
    }
}.getOrNull()

/**
 * Extension d'une image d'apres ses premiers octets, sinon d'apres [hint] (sous-type MIME ou extension).
 * Les CDN annoncent souvent un Content-Type faux : le contenu prime.
 */
internal fun imageExtension(bytes: ByteArray, hint: String?): String {
    fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
    fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
    return when {
        at(0) == 0xFF && at(1) == 0xD8 -> "jpg"
        at(0) == 0x89 && ascii(1, "PNG") -> "png"
        ascii(0, "RIFF") && ascii(8, "WEBP") -> "webp"
        ascii(0, "GIF8") -> "gif"
        ascii(4, "ftypavif") -> "avif"
        else -> when (hint?.lowercase()?.removePrefix(".")) {
            "png" -> "png"
            "webp" -> "webp"
            "gif" -> "gif"
            "avif" -> "avif"
            "jpeg", "jpg" -> "jpg"
            else -> hint?.lowercase()?.takeIf { CbzPacker.isImage("x.$it") } ?: "jpg"
        }
    }
}
