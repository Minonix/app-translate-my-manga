package com.tmm.mobile.mihon

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.data.db.ChapterEntity
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

    // Ch. 103 « Pas telecharge » : scan pendant le telechargement ("_tmp"), puis Mihon renomme le
    // dossier sans que la date du dossier serie change (meme seconde). Le cache masquait le chapitre.
    @Test fun scanDownloads_seriesScannedDuringDownload_findsChapterOnceRenamed() = runTest {
        seedSeries()
        val seriesDir = File(root, "MangaSource/One Piece").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")
        val tmp = chapter(seriesDir, "Chapitre 2_abc123_tmp")
        val stamp = seriesDir.lastModified()

        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)
        assertEquals(1, db.downloadedChapterDao().observeAll().first().size)

        tmp.renameTo(File(seriesDir, "Chapitre 2"))
        seriesDir.setLastModified(stamp)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)

        assertEquals(setOf("Chapitre 1", "Chapitre 2"), db.downloadedChapterDao().observeAll().first().map { it.chapterDir }.toSet())
    }

    // Chapitres « manquants » apres un Actualiser pendant le telechargement : Mihon ecrit "X.cbz.tmp" puis
    // le renomme en ".cbz" dans la meme seconde. Le dossier serie ne doit pas etre mis en cache entre-temps.
    @Test fun scanDownloads_cbzTmpDuringDownload_findsCbzOnceRenamed() = runTest {
        seedSeries()
        val seriesDir = File(root, "MangaSource/One Piece").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")
        val tmp = File(seriesDir, "Chapitre 2.cbz.tmp").apply { writeBytes(cbzWithOnePage()) }
        val stamp = seriesDir.lastModified()

        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)
        tmp.renameTo(File(seriesDir, "Chapitre 2.cbz"))
        seriesDir.setLastModified(stamp)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)

        assertEquals(setOf("Chapitre 1", "Chapitre 2.cbz"), db.downloadedChapterDao().observeAll().first().map { it.chapterDir }.toSet())
    }

    // Un CBZ illisible dans une serie ne doit pas empecher de lire les series suivantes.
    @Test fun scanDownloads_unreadableSeries_doesNotStopScan() = runTest {
        seedSeries()
        val broken = File(root, "AAA/Broken").apply { mkdirs() }
        File(broken, "Chapter 1.cbz").writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 1, 2, 3))
        chapter(File(root, "MangaSource/One Piece").apply { mkdirs() }, "Chapitre 1")

        DownloadScanner(FakeMihonFs(root), db).scanDownloads(root.absolutePath)

        assertEquals(listOf("Chapitre 1"), db.downloadedChapterDao().observeAll().first().map { it.chapterDir })
    }

    private fun cbzWithOnePage(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("1.jpg"))
            zos.write(byteArrayOf(1))
            zos.closeEntry()
        }
        return out.toByteArray()
    }

    // Tirer pour recharger : relit un dossier meme si sa date n'a pas bouge.
    @Test fun scanDownloads_force_ignoresDirCache() = runTest {
        seedSeries()
        val seriesDir = File(root, "MangaSource/One Piece").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")
        val stamp = seriesDir.lastModified()
        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)

        chapter(seriesDir, "Chapitre 2")
        seriesDir.setLastModified(stamp)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)
        assertEquals(1, db.downloadedChapterDao().observeAll().first().size)

        DownloadScanner(fs, db).scanDownloads(root.absolutePath, force = true)
        assertEquals(2, db.downloadedChapterDao().observeAll().first().size)
    }

    @Test fun scanDownloads_unmatchedSeries_becomesItsOwnSeries() = runTest {
        val sourceDir = File(root, "MangaSource").apply { mkdirs() }
        val seriesDir = File(sourceDir, "Bleach").apply { mkdirs() }
        chapter(seriesDir, "Chapitre 1")

        val fs = FakeMihonFs(root)
        DownloadScanner(fs, db).scanDownloads(root.absolutePath)

        val all = db.downloadedChapterDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals(DownloadOnlySeries.key("MangaSource", "Bleach"), all[0].seriesKey)
        assertNull(all[0].chapterKey)
        assertEquals(1f, all[0].number)
        assertEquals("Bleach", db.seriesDao().get(DownloadOnlySeries.key("MangaSource", "Bleach"))?.title)
    }

    // Cas reel : la sauvegarde ne connait la serie que sur une autre source (MangaFire), les
    // chapitres sont telecharges depuis MangaDot (EN). Ils forment leur propre serie, numerotee, sans
    // rien rattacher a la copie MangaFire, et le restent apres un nouvel import (cache par date).
    @Test fun scanDownloads_seriesOnlyOnOtherSource_staysSeparateAcrossReimport() = runTest {
        db.seriesDao().upsert(SeriesEntity("fire", "1", "/title/x", "The Garden of Secrets", null, 0, null, "MangaFire", 0L, null))
        db.chapterDao().upsertAll(listOf(ChapterEntity("fire|29", "fire", "/ch/29", "Ch. 29", 29f, "official", false, 0L)))
        val seriesDir = File(File(root, "MangaDot (EN)"), "The Garden of Secrets").apply { mkdirs() }
        chapter(seriesDir, "Nyx Scans_Chapter 29_816b9c")
        chapter(seriesDir, "Rinko Comics_Chapter 29_aaaaaa")
        chapter(seriesDir, "Rinko Comics_Chapter 50.5_bbbbbb")
        val scanner = DownloadScanner(FakeMihonFs(root), db)
        val ownKey = DownloadOnlySeries.key("MangaDot (EN)", "The Garden of Secrets")

        scanner.scanDownloads(root.absolutePath)
        val rows = db.downloadedChapterDao().observeAll().first()
        assertEquals(setOf(ownKey), rows.map { it.seriesKey }.toSet())
        assertEquals(listOf(29f, 29f, 50.5f), rows.mapNotNull { it.number }.sorted())

        // Import d'une sauvegarde (vide la table series), dossier inchange : la serie revient.
        db.seriesDao().clear()
        db.seriesDao().upsert(SeriesEntity("fire", "1", "/title/x", "The Garden of Secrets", null, 0, null, "MangaFire", 0L, null))
        scanner.scanDownloads(root.absolutePath)
        scanner.rematch()
        assertEquals("MangaDot (EN)", db.seriesDao().get(ownKey)?.sourceName)
        assertEquals(setOf(ownKey), db.downloadedChapterDao().observeAll().first().map { it.seriesKey }.toSet())
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
        val ownKey = DownloadOnlySeries.key("MangaSource", "One Piece")
        assertEquals(ownKey, db.downloadedChapterDao().observeAll().first().single().seriesKey)

        seedSeries()
        scanner.rematch()
        assertNull(db.seriesDao().get(ownKey))

        assertEquals("sk1", db.downloadedChapterDao().observeAll().first().single().seriesKey)
    }

    // Dossier source reel de Mihon : "Nom (LANGUE)", la sauvegarde ne porte que "Nom".
    @Test fun scanDownloads_matchesSourceDirWithMihonLangSuffix() = runTest {
        seedSeries()
        chapter(File(File(root, "MangaSource (EN)"), "One Piece").apply { mkdirs() }, "Chapitre 1")

        DownloadScanner(FakeMihonFs(root), db).scanDownloads(root.absolutePath)

        assertEquals("sk1", db.downloadedChapterDao().observeAll().first().single().seriesKey)
    }

    // Meme serie deux fois dans la bibliotheque (FR et EN d'une meme source, ou ajout en double) :
    // un seul dossier Mihon, chaque chapitre va a la copie dont l'URL donne son suffixe de hachage.
    @Test fun scanDownloads_duplicateSeries_chaptersGoToSeriesOwningTheirUrl() = runTest {
        db.seriesDao().upsert(SeriesEntity("fr", "42", "/manga/fr", "One Piece", null, 0, null, "MangaSource", 0L, null))
        db.seriesDao().upsert(SeriesEntity("en", "43", "/manga/en", "One Piece", null, 0, null, "MangaSource", 0L, null))
        db.chapterDao().upsertAll(
            listOf(
                ChapterEntity("fr|1", "fr", "/ch/fr-1", "Chapitre 1", 1f, null, false, 0L),
                ChapterEntity("en|1", "en", "/ch/en-1", "Chapitre 1", 1f, null, false, 0L),
                ChapterEntity("en|2", "en", "/ch/en-2", "Chapitre 2", 2f, null, false, 0L),
            ),
        )
        val seriesDir = File(File(root, "MangaSource (EN)"), "One Piece").apply { mkdirs() }
        chapter(seriesDir, MihonNaming.getChapterDirName("Chapitre 1", null, "/ch/en-1"))
        chapter(seriesDir, MihonNaming.getChapterDirName("Chapitre 2", null, "/ch/en-2"))
        chapter(seriesDir, "Chapitre 3 inconnu")
        val scanner = DownloadScanner(FakeMihonFs(root), db)

        scanner.scanDownloads(root.absolutePath)
        val rows = db.downloadedChapterDao().observeAll().first()
        assertEquals(listOf("en", "en", "en"), rows.map { it.seriesKey })
        assertEquals(setOf("en|1", "en|2", null), rows.map { it.chapterKey }.toSet())

        // Telechargements deja rattaches a la mauvaise copie par une version precedente.
        db.downloadedChapterDao().upsertAll(rows.map { it.copy(seriesKey = "fr", chapterKey = null) })
        scanner.rematch()
        assertEquals(setOf("en|1", "en|2", null), db.downloadedChapterDao().observeAll().first().map { it.chapterKey }.toSet())
        assertEquals(setOf("en"), db.downloadedChapterDao().observeAll().first().map { it.seriesKey }.toSet())
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

    private fun downloaded(source: String, series: String, chapterDir: String, number: Float) =
        com.tmm.mobile.data.db.DownloadedChapterEntity(
            mihonPath = "$source/$series/$chapterDir", sourceDir = source, seriesDir = series, chapterDir = chapterDir,
            documentUri = "uri", isCbz = true, sizeBytes = 1, lastModified = 0, pageCount = 1,
            seriesKey = null, chapterKey = null, number = number,
        )

    // Bug « Pret a installer jusqu'au 65 » : les traductions deja dans local/ sans mihonPath n'etaient
    // pas reconnues, le statut retombait sur l'index du PC (fr) au lieu de « A jour ». Le dossier etait
    // deja en cache : le rattachement doit se faire meme sans rescanner le dossier.
    @Test fun scanLocal_linksLegacyFrFilesToDownloadedChapter_evenWhenFolderCached() = runTest {
        val localRoot = File(root, "local").apply { mkdirs() }
        val folder = File(localRoot, "A Gatherer's Adventure in Isekai").apply { mkdirs() }
        listOf("c0033 - Ch. 33.cbz", "c0014.1 - Ch. 14.1.cbz", "c0040 - Ch. 40.cbz", "notes.cbz")
            .forEach { File(folder, it).writeBytes(byteArrayOf(1)) }
        val scanner = DownloadScanner(FakeMihonFs(root), db)
        scanner.scanLocal(localRoot.absolutePath)

        val series = "A Gatherer's Adventure in Isekai"
        db.downloadedChapterDao().upsertAll(
            listOf(
                downloaded("MangaFire (EN)", series, "unofficial_Ch. 33_a1.cbz", 33f),
                downloaded("MangaFire (EN)", series, "unofficial_Ch. 14.1_b2.cbz", 14.1f),
                // Deux editions du 40 : ambigu, on ne devine pas.
                downloaded("MangaFire (EN)", series, "unofficial_Ch. 40_c3.cbz", 40f),
                downloaded("Other (EN)", series, "Ch. 40_d4.cbz", 40f),
            ),
        )
        scanner.scanLocal(localRoot.absolutePath)

        val byFile = db.localFrChapterDao().observeAll().first().associate { it.fileName to it.mihonPath }
        assertEquals("MangaFire (EN)/$series/unofficial_Ch. 33_a1.cbz", byFile["c0033 - Ch. 33.cbz"])
        assertEquals("MangaFire (EN)/$series/unofficial_Ch. 14.1_b2.cbz", byFile["c0014.1 - Ch. 14.1.cbz"])
        assertNull(byFile["c0040 - Ch. 40.cbz"])
        assertNull(byFile["notes.cbz"])
    }

    @Test
    fun guessScanlator_readsPrefixBeforeChapterKeyword() {
        assertEquals("Nyx Scans", DownloadOnlySeries.guessScanlator("Nyx Scans_Chapter 29_816b9c.cbz"))
        assertEquals("Rinko Comics", DownloadOnlySeries.guessScanlator("Rinko Comics_Chapter 47_1a91b8"))
        assertEquals(null, DownloadOnlySeries.guessScanlator("Chapter 5_ Titre_123abc"))
        assertEquals(null, DownloadOnlySeries.guessScanlator("Extra_ffffff.cbz"))
    }
}
