package com.tmm.mobile.mihon

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.TmmDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadScannerTest {
    private lateinit var db: TmmDatabase
    private lateinit var root: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TmmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = kotlin.io.path.createTempDirectory().toFile()
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    // Chapitre telecharge avec une page : un dossier vide n'est plus compte comme chapitre.
    private fun chapter(seriesDir: File, name: String) = File(seriesDir, name).apply {
        mkdirs()
        File(this, "1.jpg").writeBytes(byteArrayOf(1))
    }

    private suspend fun seedSeries() {
        db.seriesDao().upsert(
            SeriesEntity("sk1", "42", "/manga/1", "One Piece", null, 0, null, "MangaSource", 0L, null),
        )
    }

    @Test fun scanDownloads_matchesSeriesAndUpsertsDownloadedChapter() = runTest {
        seedSeries()
        val sourceDir = File(root, "MangaSource").apply { mkdirs() }
        val seriesDir = File(sourceDir, "One Piece").apply { mkdirs() }
        File(seriesDir, "Chapitre 1").apply { mkdirs() }
        File(seriesDir, "Chapitre 1/1.jpg").writeBytes(byteArrayOf(1))

        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)

        val all = db.downloadedChapterDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("sk1", all[0].seriesKey)
        assertEquals(1, all[0].pageCount)
    }

    @Test fun scanDownloads_incompleteTmpDirIsIgnored() = runTest {
        seedSeries()
        val sourceDir = File(root, "MangaSource").apply { mkdirs() }
        val seriesDir = File(sourceDir, "One Piece").apply { mkdirs() }
        File(seriesDir, "Chapitre 1_abc123_tmp").mkdirs()

        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)

        assertEquals(0, db.downloadedChapterDao().observeAll().first().size)
    }

    @Test fun scanDownloads_unmatchedSeries_keepsChapterWithNullSeriesKey() = runTest {
        val sourceDir = File(root, "MangaSource").apply { mkdirs() }
        val seriesDir = File(sourceDir, "Bleach").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")

        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)

        val all = db.downloadedChapterDao().observeAll().first()
        assertEquals(1, all.size)
        assertNull(all[0].seriesKey)
    }

    @Test fun scanDownloads_secondScan_skipsUnchangedSeriesDirViaCache() = runTest {
        seedSeries()
        val sourceDir = File(root, "MangaSource").apply { mkdirs() }
        val seriesDir = File(sourceDir, "One Piece").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")

        val fs = FakeMihonFs(root)
        val scanner = DownloadScanner(fs, db)
        scanner.scanDownloads(root.absolutePath)
        val callsAfterFirst = fs.childrenCallCount
        scanner.scanDownloads(root.absolutePath)

        // le second scan ne doit pas redescendre dans le dossier chapitre (cache par mtime)
        assertEquals(callsAfterFirst + 2, fs.childrenCallCount)
    }

    @Test fun scanDownloads_ignoresLooseFilesAndChaptersWithoutPages() = runTest {
        seedSeries()
        val seriesDir = File(File(root, "MangaSource"), "One Piece").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")
        File(seriesDir, "Chapitre 2").mkdirs()
        File(seriesDir, "cover.jpg").writeBytes(byteArrayOf(1))

        DownloadScanner(FakeMihonFs(root), db).scanDownloads(root.absolutePath)

        assertEquals(listOf("MangaSource/One Piece/Chapitre 1"), db.downloadedChapterDao().observeAll().first().map { it.mihonPath })
    }

    @Test fun scanLocal_preservesExistingJobIdAndMihonPath() = runTest {
        val localRoot = File(root, "local").apply { mkdirs() }
        val folder = File(localRoot, "OnePiece").apply { mkdirs() }
        File(folder, "ch1.cbz").writeBytes(byteArrayOf(1))

        db.localFrChapterDao().upsert(
            com.tmm.mobile.data.db.LocalFrChapterEntity(
                localPath = "OnePiece/ch1.cbz",
                folderName = "OnePiece",
                fileName = "ch1.cbz",
                documentUri = "old-uri",
                lastModified = 0L,
                jobId = "job1",
                mihonPath = "MangaSource/One Piece/Chapitre 1",
            ),
        )

        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanLocal(localRoot.absolutePath)

        val updated = db.localFrChapterDao().get("OnePiece/ch1.cbz")
        assertNotNull(updated)
        assertEquals("job1", updated?.jobId)
        assertEquals("MangaSource/One Piece/Chapitre 1", updated?.mihonPath)
    }

    @Test fun scanDownloads_removesDeletedChapterAndDeletedSeriesDir() = runTest {
        seedSeries()
        val sourceDir = File(root, "MangaSource").apply { mkdirs() }
        val seriesDir = File(sourceDir, "One Piece").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")
        chapter(seriesDir, "Chapitre 2")
        val bleach = File(sourceDir, "Bleach").apply { mkdirs() }
        chapter(bleach, "Chapitre 1")
        val scanner = DownloadScanner(FakeMihonFs(root), db)
        scanner.scanDownloads(root.absolutePath)
        assertEquals(3, db.downloadedChapterDao().observeAll().first().size)

        File(seriesDir, "Chapitre 2").deleteRecursively()
        seriesDir.setLastModified(seriesDir.lastModified() + 10_000)
        bleach.deleteRecursively()
        scanner.scanDownloads(root.absolutePath)

        assertEquals(listOf("MangaSource/One Piece/Chapitre 1"), db.downloadedChapterDao().observeAll().first().map { it.mihonPath })
        assertEquals(1, db.scannedSeriesDirDao().observeAll().first().size)
    }

    @Test fun rematch_attachesDownloadsToSeriesImportedLater() = runTest {
        val seriesDir = File(File(root, "MangaSource"), "One Piece").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")
        val scanner = DownloadScanner(FakeMihonFs(root), db)
        scanner.scanDownloads(root.absolutePath)
        assertNull(db.downloadedChapterDao().observeAll().first().single().seriesKey)

        seedSeries()
        scanner.rematch()

        assertEquals("sk1", db.downloadedChapterDao().observeAll().first().single().seriesKey)
    }

    // Dossier source reel de Mihon : "Nom (LANGUE)", la sauvegarde ne porte que "Nom".
    @Test fun scanDownloads_matchesSourceDirWithMihonLangSuffix() = runTest {
        seedSeries()
        chapter(File(File(root, "MangaSource (EN)"), "One Piece").apply { mkdirs() }, "Chapitre 1")

        DownloadScanner(FakeMihonFs(root), db).scanDownloads(root.absolutePath)

        assertEquals("sk1", db.downloadedChapterDao().observeAll().first().single().seriesKey)
    }

    @Test fun scanLocal_removesDeletedFiles_keepsUnchangedFolderViaCache() = runTest {
        val localRoot = File(root, "local").apply { mkdirs() }
        val folder = File(localRoot, "OnePiece").apply { mkdirs() }
        File(folder, "ch1.cbz").writeBytes(byteArrayOf(1))
        val ch2 = File(folder, "ch2.cbz").apply { writeBytes(byteArrayOf(1)) }
        val fs = FakeMihonFs(root)
        val scanner = DownloadScanner(fs, db)
        scanner.scanLocal(localRoot.absolutePath)
        assertEquals(2, db.localFrChapterDao().observeAll().first().size)

        val calls = fs.childrenCallCount
        scanner.scanLocal(localRoot.absolutePath)
        assertEquals(calls + 1, fs.childrenCallCount)

        ch2.delete()
        folder.setLastModified(folder.lastModified() + 10_000)
        scanner.scanLocal(localRoot.absolutePath)
        assertEquals(listOf("OnePiece/ch1.cbz"), db.localFrChapterDao().observeAll().first().map { it.localPath })
    }
}
