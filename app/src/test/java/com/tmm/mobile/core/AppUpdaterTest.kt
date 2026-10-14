package com.tmm.mobile.core

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

// Mise a jour depuis les releases GitHub : faux GitHub local, aucun reseau.
class AppUpdaterTest {
    private val server = MockWebServer()
    private val dir = File(kotlin.io.path.createTempDirectory().toFile(), "updates")
    private val apk = ByteArray(1000) { it.toByte() }
    private var release = ""

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/latest" -> MockResponse(code = 200, body = release)
                "/app.apk" -> MockResponse.Builder().code(200).body(Buffer().write(apk)).build()
                else -> MockResponse(code = 404)
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun updater() = AppUpdater(OkHttpClient(), dir, server.url("/latest").toString())

    private fun releaseJson(tag: String, sha: String = sha256(apk), size: Int = apk.size, prerelease: Boolean = false) =
        """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"notes","assets":[
           {"name":"TMM-Mobile-$tag.apk","size":$size,"digest":"sha256:$sha",
            "browser_download_url":"${server.url("/app.apk")}","content_type":"application/vnd.android.package-archive"}]}"""

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun versionsAreComparedNumerically() {
        assertTrue(isNewerVersion("1.10.0", "1.9.2"))
        assertTrue(isNewerVersion("v2.0.0", "1.10.1"))
        assertFalse(isNewerVersion("1.10.0", "1.10.0"))
        assertFalse(isNewerVersion("1.9.9", "1.10.0"))
    }

    @Test fun newerRelease_isOffered_sameOrPrereleaseIsNot() = runTest {
        release = releaseJson("v1.11.0")
        val offered = updater().newerThan("1.10.0")
        assertEquals("1.11.0", offered?.version)
        assertEquals(sha256(apk), offered?.sha256)

        assertNull(updater().newerThan("1.11.0"))
        release = releaseJson("v1.12.0", prerelease = true)
        assertNull(updater().newerThan("1.10.0"))
    }

    @Test fun download_returnsVerifiedApk() = runTest {
        release = releaseJson("v1.11.0")
        val updater = updater()
        val file = updater.download(updater.newerThan("1.10.0")!!) { _, _ -> }
        assertArrayEquals(apk, file.readBytes())
    }

    // Un APK altere (ou tronque) ne doit jamais etre confie a l'installateur.
    @Test fun download_rejectsApkWithWrongDigest() = runTest {
        release = releaseJson("v1.11.0", sha = "0".repeat(64))
        val updater = updater()
        try {
            updater.download(updater.newerThan("1.10.0")!!) { _, _ -> }
            fail("APK altere accepte")
        } catch (e: IOException) {
            assertTrue(dir.listFiles().isNullOrEmpty())
        }
    }
}
