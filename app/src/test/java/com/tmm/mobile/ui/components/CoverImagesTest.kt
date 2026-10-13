package com.tmm.mobile.ui.components

import coil3.network.HttpException
import coil3.network.NetworkResponse
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Couvertures : normalisation de l'URL avant Coil, en-tetes poses sur les requetes, et journal
// d'echec qui ne contient jamais l'URL complete. Aucun acces reseau.
class CoverImagesTest {
    @Test
    fun normalizeCoverUrl_rejectsEmptyBlankAndNonHttpUrls() {
        assertNull(normalizeCoverUrl(null))
        assertNull(normalizeCoverUrl(""))
        assertNull(normalizeCoverUrl("   "))
        assertNull(normalizeCoverUrl("/manga/123/cover.jpg"))
        assertNull(normalizeCoverUrl("content://media/cover.jpg"))
        assertNull(normalizeCoverUrl("ftp://cdn.example.com/a.jpg"))
        assertNull(normalizeCoverUrl("https://"))
    }

    @Test
    fun normalizeCoverUrl_upgradesHttpAndKeepsHttps() {
        assertEquals("https://cdn.example.com/a.jpg", normalizeCoverUrl("http://cdn.example.com/a.jpg"))
        assertEquals("https://cdn.example.com/a.jpg", normalizeCoverUrl("HTTP://cdn.example.com/a.jpg"))
        assertEquals("https://cdn.example.com/a.jpg?w=300", normalizeCoverUrl("  https://cdn.example.com/a.jpg?w=300 "))
    }

    @Test
    fun coverErrorLogLine_logsHostCodeAndClass_neverTheFullUrl() {
        val url = "https://cdn.example.com/covers/secret-path.jpg?token=abc"
        val http = coverErrorLogLine(url, HttpException(NetworkResponse(code = 403)))
        assertEquals("couverture non chargee hote=cdn.example.com http=403 erreur=HttpException", http)

        val io = coverErrorLogLine(url, IOException("echec vers $url"))
        assertEquals("couverture non chargee hote=cdn.example.com erreur=IOException", io)
        assertFalse(io.contains("secret-path") || io.contains("token"))
    }

    @Test
    fun interceptor_setsMobileUserAgentAndHostReferer() {
        var seen: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(CoverHeadersInterceptor())
            // Intercepteur terminal : capture la requete et repond sans toucher au reseau.
            .addInterceptor { chain ->
                seen = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        client.newCall(Request.Builder().url("https://cdn.example.com/covers/a.jpg").build()).execute().close()

        val request = seen!!
        assertEquals(COVER_USER_AGENT, request.header("User-Agent"))
        assertTrue(request.header("User-Agent")!!.contains("Mobile"))
        assertEquals("https://cdn.example.com/", request.header("Referer"))
    }

    @Test
    fun externalStorageCoverDocId_decodesMihonLocalCover() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AMihon/document/"
        assertEquals(
            "primary:Mihon/local/Return of the War God/cover.webp",
            externalStorageCoverDocId(tree + "primary%3AMihon%2Flocal%2FReturn%20of%20the%20War%20God%2Fcover.webp"),
        )
        assertEquals("primary:Mihon/local/A+B/cover.jpg", externalStorageCoverDocId(tree + "primary%3AMihon%2Flocal%2FA+B%2Fcover.jpg"))
        assertNull(externalStorageCoverDocId("content://media/cover.jpg"))
        assertNull(externalStorageCoverDocId("https://cdn.example.com/a.jpg"))
        assertNull(externalStorageCoverDocId(null))
    }

    @Test
    fun treeContains_matchesFolderAndVolumeRootOnly() {
        assertTrue(treeContains("primary:Mihon", "primary:Mihon/local/X/cover.jpg"))
        assertTrue(treeContains("primary:", "primary:Mihon/local/X/cover.jpg"))
        assertFalse(treeContains("primary:Mihon", "primary:Mihon2/local/X/cover.jpg"))
        assertFalse(treeContains("primary:Mihon/downloads", "primary:Mihon/local/X/cover.jpg"))
    }

    @Test
    fun redirectInterceptor_upgradesHttpHopsToHttps() {
        val seen = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(CoverHttpsRedirectInterceptor())
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                seen += url
                val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).body("".toResponseBody())
                when (url) {
                    "https://cdn.example.com/a.jpg" -> builder.code(301).message("Moved").header("Location", "http://cdn.example.com/b.jpg")
                    "https://cdn.example.com/b.jpg" -> builder.code(302).message("Found").header("Location", "https://img.example.org/c.jpg")
                    else -> builder.code(200).message("OK")
                }.build()
            }
            .build()

        val response = client.newCall(Request.Builder().url("https://cdn.example.com/a.jpg").build()).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(listOf("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg", "https://img.example.org/c.jpg"), seen)
    }
}
