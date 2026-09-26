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
        root = createTempDir()
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
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
        File(seriesDir, "Chapitre 1").mkdirs()

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
        File(seriesDir, "Chapitre 1").mkdirs()

        val fs = FakeMihonFs(root)
        val scanner = DownloadScanner(fs, db)
        scanner.scanDownloads(root.absolutePath)
        val callsAfterFirst = fs.childrenCallCount
        scanner.scanDownloads(root.absolutePath)

        // le second scan ne doit pas redescendre dans le dossier chapitre (cache par mtime)
        assertEquals(callsAfterFirst + 2, fs.childrenCallCount)
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
}
