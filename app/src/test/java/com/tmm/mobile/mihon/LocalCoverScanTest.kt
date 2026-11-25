package com.tmm.mobile.mihon

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.SeriesLinkEntity
import com.tmm.mobile.data.db.TmmDatabase
import java.io.File
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Couvertures de local/ au scan : vignette reseau de la serie plutot qu'une page de credits
// (Off to an Invincible Start : cover.webp = page 001 BRSMANHUA de c0033, sur le vrai telephone).
@RunWith(RobolectricTestRunner::class)
class LocalCoverScanTest {
    private lateinit var db: TmmDatabase
    private lateinit var root: File
    private val server = MockWebServer()
    private var thumbnailCode = 200
    private var requests = 0

    // Petites images fabriquees (aucune image reelle) : en-tetes JPEG / WebP suffisent au test.
    private val credits = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), 0, 0, 0, 0, 'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(), 7)
    private val thumbnail = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TmmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = kotlin.io.path.createTempDirectory().toFile()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests++
                return if (thumbnailCode == 200) {
                    MockResponse.Builder().code(200).addHeader("Content-Type", "image/jpeg").body(Buffer().write(thumbnail)).build()
                } else {
                    MockResponse(code = thumbnailCode)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
        root.deleteRecursively()
    }

    // MockWebServer sert en http : on saute normalizeCoverUrl (qui force https) avec un client brut.
    private fun scanner() = DownloadScanner(FakeMihonFs(root), db) { url -> fetchCoverImage(url!!, OkHttpClient()) }

    private suspend fun seedSeries(title: String) {
        db.seriesDao().upsert(
            SeriesEntity("sk1", "42", "/m/1", title, null, 0, server.url("/thumb.jpg").toString(), "BRSManhua", 0L, null),
        )
    }

    private fun localFolder(name: String, cover: ByteArray?): File {
        val folder = File(File(root, "local"), name).apply { mkdirs() }
        File(folder, "c0033 - Ch. 33.cbz").outputStream().use {
            CbzPacker.pack(mapOf("001.webp" to credits, "002.webp" to byteArrayOf(9, 9, 9)), it)
        }
        if (cover != null) File(folder, "cover.webp").writeBytes(cover)
        return folder
    }

    private val localRoot get() = File(root, "local").absolutePath

    @Test fun pageCover_replacedByThumbnail_keepsName() = runTest {
        seedSeries("Off to an Invincible Start")
        val folder = localFolder("Off to an Invincible Start", credits)

        scanner().scanLocal(localRoot)

        assertEquals(listOf("c0033 - Ch. 33.cbz", "cover.webp"), folder.list()!!.sorted())
        assertEquals(thumbnail.toList(), File(folder, "cover.webp").readBytes().toList())
        assertEquals(1, requests)
    }

    @Test fun pageCover_keptWhenThumbnailUnavailable_andNotRetriedWhileFolderUnchanged() = runTest {
        seedSeries("Off to an Invincible Start")
        thumbnailCode = 404
        val folder = localFolder("Off to an Invincible Start", credits)
        val scanner = scanner()

        scanner.scanLocal(localRoot)
        scanner.scanLocal(localRoot)

        assertEquals(credits.toList(), File(folder, "cover.webp").readBytes().toList())
        assertEquals(1, requests)
    }

    @Test fun realCover_neverReplacedNorDownloaded() = runTest {
        seedSeries("Off to an Invincible Start")
        val folder = localFolder("Off to an Invincible Start", byteArrayOf(4, 4, 4, 4))

        scanner().scanLocal(localRoot, force = true)

        assertEquals(listOf(4, 4, 4, 4), File(folder, "cover.webp").readBytes().map { it.toInt() })
        assertEquals(0, requests)
    }

    @Test fun missingCover_takesThumbnailBeforePage() = runTest {
        seedSeries("Off to an Invincible Start")
        val folder = localFolder("Off to an Invincible Start", null)

        scanner().scanLocal(localRoot)

        assertEquals(listOf("c0033 - Ch. 33.cbz", "cover.jpg"), folder.list()!!.sorted())
        assertEquals(thumbnail.toList(), File(folder, "cover.jpg").readBytes().toList())
    }

    // Dossier lie a la main (autre titre) : il prend la vignette de la serie qui y pointe.
    @Test fun linkedFolder_takesThumbnailOfLinkedSeries() = runTest {
        seedSeries("See You at the Pepper Field!")
        db.seriesLinkDao().upsert(SeriesLinkEntity("sk1", "See You in the Chili Pepper Field!"))
        val folder = localFolder("See You in the Chili Pepper Field!", credits)

        scanner().scanLocal(localRoot)

        assertEquals(thumbnail.toList(), File(folder, "cover.webp").readBytes().toList())
    }

    // Plafond de telechargements par scan : les dossiers restants sont repris au scan suivant.
    @Test fun downloadsCappedPerScan_restDeferredToNextScan() = runTest {
        val names = (1..10).map { "Serie $it" }
        names.forEachIndexed { i, name ->
            db.seriesDao().upsert(SeriesEntity("sk$i", "42", "/m/$i", name, null, 0, server.url("/t$i.jpg").toString(), "S", 0L, null))
            localFolder(name, credits)
        }
        val scanner = scanner()

        scanner.scanLocal(localRoot)
        assertEquals(8, requests)
        scanner.scanLocal(localRoot)
        assertEquals(10, requests)
        val replaced = names.count { File(File(localRoot, it), "cover.webp").readBytes().contentEquals(thumbnail) }
        assertEquals(10, replaced)
    }
}
