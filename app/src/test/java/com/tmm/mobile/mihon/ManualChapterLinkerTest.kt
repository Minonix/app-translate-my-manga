package com.tmm.mobile.mihon

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.TmmDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManualChapterLinkerTest {
    private lateinit var db: TmmDatabase
    private lateinit var root: File
    private lateinit var seriesDir: File
    private lateinit var fs: FakeMihonFs
    private lateinit var linker: ManualChapterLinker

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TmmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = kotlin.io.path.createTempDirectory().toFile()
        seriesDir = File(root, "downloads/MangaSource/One Piece").apply { mkdirs() }
        fs = FakeMihonFs(root)
        linker = ManualChapterLinker(fs, root.absolutePath, db)
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private suspend fun seed() {
        db.seriesDao().upsert(SeriesEntity("sk1", "42", "/manga/1", "One Piece", null, 0, null, "MangaSource", 0L, null))
        db.chapterDao().upsertAll(
            listOf(
                ChapterEntity("sk1|5", "sk1", "/ch/5", "Chapitre 5", 5f, null, false, 0L),
                ChapterEntity("sk1|32", "sk1", "/ch/32", "Chapitre 32", 32f, null, false, 0L),
            ),
        )
    }

    private fun cbz(name: String, vararg entries: String) = File(seriesDir, name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach {
                zip.putNextEntry(ZipEntry(it))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }
    }

    private val target5 = LinkTarget("sk1", "sk1|5", 5f)

    @Test fun startDir_isSeriesFolder() = runTest {
        seed()
        assertEquals(seriesDir.absolutePath, linker.startDirDocId("sk1"))
    }

    @Test fun check_fileNeverScanned_linksAndSurvivesRescan() = runTest {
        seed()
        val file = cbz("Bizarre_nom.cbz", "001.jpg", "002.jpg")

        val check = linker.check(target5, file.absolutePath) as LinkCheck.Ok
        assertNull(check.replacing)
        assertEquals(2, check.file.row.pageCount)
        linker.link(check.file)

        val row = db.downloadedChapterDao().get("MangaSource/One Piece/Bizarre_nom.cbz")!!
        assertEquals("sk1|5", row.chapterKey)

        // Rescan force puis rematch : le lien manuel l'emporte sur le Matcher (qui ne lit aucun numero ici).
        val scanner = DownloadScanner(fs, db)
        scanner.scanDownloads(File(root, "downloads").absolutePath, force = true)
        assertEquals("sk1|5", db.downloadedChapterDao().get(row.mihonPath)!!.chapterKey)
        scanner.rematch()
        assertEquals("sk1|5", db.downloadedChapterDao().get(row.mihonPath)!!.chapterKey)
        assertEquals(5f, db.downloadedChapterDao().get(row.mihonPath)!!.number)
    }

    @Test fun check_sameChapterTwice_isRefused() = runTest {
        seed()
        val file = cbz("Ch5.cbz", "1.png")
        linker.link((linker.check(target5, file.absolutePath) as LinkCheck.Ok).file)

        assertEquals(LinkCheck.AlreadyLinked, linker.check(target5, file.absolutePath))
    }

    @Test fun check_fileLinkedToOtherChapter_reportsIt() = runTest {
        seed()
        val file = cbz("Ch32.cbz", "1.png")
        db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                "MangaSource/One Piece/Ch32.cbz", "MangaSource", "One Piece", "Ch32.cbz", file.absolutePath,
                true, file.length(), file.lastModified(), 1, "sk1", "sk1|32", 32f,
            ),
        )

        val check = linker.check(target5, file.absolutePath) as LinkCheck.Ok
        assertEquals(LinkedElsewhere(32f, null), check.replacing)
    }

    @Test fun check_refusesOutsideDownloads_nonCbz_andEmptyArchive() = runTest {
        seed()
        val outside = File(root, "local/x.cbz").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        assertEquals(LinkCheck.OutsideDownloads, linker.check(target5, outside.absolutePath))

        val pdf = File(seriesDir, "x.pdf").apply { writeBytes(byteArrayOf(1)) }
        assertEquals(LinkCheck.NotCbz, linker.check(target5, pdf.absolutePath))

        val empty = cbz("vide.cbz", "ComicInfo.xml")
        assertEquals(LinkCheck.NoPages, linker.check(target5, empty.absolutePath))
        assertTrue(db.manualChapterLinkDao().getAll().isEmpty())
    }

    // Ligne « manquante » : numero seul, sans chapitre du catalogue.
    @Test fun link_missingNumber_keepsNumberWithoutChapterKey() = runTest {
        seed()
        val file = cbz("Ch7.cbz", "1.webp")
        linker.link((linker.check(LinkTarget("sk1", null, 7f), file.absolutePath) as LinkCheck.Ok).file)

        val row = db.downloadedChapterDao().get("MangaSource/One Piece/Ch7.cbz")!!
        assertEquals("sk1", row.seriesKey)
        assertNull(row.chapterKey)
        assertEquals(7f, row.number)
    }
}
