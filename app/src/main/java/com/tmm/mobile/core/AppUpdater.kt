package com.tmm.mobile.core

import com.tmm.mobile.protocol.ProtocolJson
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import okhttp3.OkHttpClient
import okhttp3.Request

const val LATEST_RELEASE_URL = "https://api.github.com/repos/Minonix/app-translate-my-manga/releases/latest"

/** Derniere release publiee : version sans « v », APK a telecharger et son sha256 (null si GitHub ne le donne pas). */
data class AppRelease(val version: String, val apkUrl: String, val size: Long, val sha256: String?)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val url: String,
    val digest: String? = null,
)

/**
 * Mise a jour depuis les releases GitHub (le depot est public, aucun jeton). L'installation elle-meme est
 * confiee a l'installateur d'Android (SettingsRoute) : il refuse un APK signe par une autre cle.
 */
class AppUpdater(private val client: OkHttpClient, private val dir: File, private val latestUrl: String = LATEST_RELEASE_URL) {

    /** Release plus recente que [current], sinon null. */
    suspend fun newerThan(current: String): AppRelease? {
        val release = latest() ?: return null
        return release.takeIf { isNewerVersion(it.version, current) }
    }

    private suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(latestUrl).header("Accept", "application/vnd.github+json").build()
        val text = client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null // aucune release
            if (!response.isSuccessful) throw IOException("GitHub ${response.code}")
            response.body.string()
        }
        val release = ProtocolJson.decodeFromString(GithubRelease.serializer(), text)
        if (release.draft || release.prerelease) return@withContext null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
        AppRelease(release.tagName.removePrefix("v"), apk.url, apk.size, apk.digest?.removePrefix("sha256:"))
    }

    /** APK telecharge et verifie (taille, sha256) ; un fichier incomplet ou altere n'est jamais rendu. */
    suspend fun download(release: AppRelease, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // anciennes versions
        val dest = File(dir, "TMM-Mobile-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("téléchargement ${response.code}")
            dest.outputStream().use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        onProgress(done, release.size)
                    }
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (dest.length() != release.size || (release.sha256 != null && sha != release.sha256)) {
            dest.delete()
            throw IOException("APK incomplet ou altéré")
        }
        dest
    }
}

/** Comparaison SemVer numerique (« 1.10.0 » > « 1.9.2 ») ; un suffixe (-rc1) est ignore. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
