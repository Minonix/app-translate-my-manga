package com.tmm.mobile.ui.screens.catalog

import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.mihon.MihonBackup
import com.tmm.mobile.mihon.MihonBackupCategory
import com.tmm.mobile.mihon.MihonBackupChapter
import com.tmm.mobile.mihon.MihonBackupManga
import com.tmm.mobile.mihon.MihonBackupSource
import com.tmm.mobile.workers.TestClock
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.6 (E10) : CatalogViewModel derive son etat de Room (backup_import, series,
// category, series_category, downloaded_chapter) et "Relire la derniere sauvegarde" rejoue un
// import complet (importReimportSequence) sans bouton "Importer" manuel (5.21).
@RunWith(RobolectricTestRunner::class)
class CatalogViewModelTest {
    private val env = WorkerTestEnvironment()

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun series(key: String, title: String) = SeriesEntity(
        seriesKey = key, sourceId = "src", url = "url/$key", title = title, author = null,
        status = 0, thumbnailUrl = null, sourceName = "Mihon", dateAdded = 0L, mangaType = null,
    )

    @Test
    fun state_derivesCategoriesAndAbsentSeriesFromRoom() = runTest {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren"), series("one-piece", "One Piece")))
        env.db.categoryDao().upsertAll(listOf(CategoryEntity(order = 0L, name = "En cours")))
        env.db.seriesCategoryDao().insertAll(listOf(SeriesCategoryEntity(seriesKey = "frieren", categoryOrder = 0L)))
        // Seul Frieren a un chapitre telecharge -> One Piece compte comme "absente".
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = "Mihon/frieren/1", sourceDir = "Mihon", seriesDir = "frieren", chapterDir = "1",
                documentUri = "doc://1", isCbz = true, sizeBytes = 1L, lastModified = 0L, pageCount = 10,
                seriesKey = "frieren", chapterKey = null, number = 1f,
            ),
        )

        val vm = CatalogViewModel(env.container)
        val state = vm.state.first { it.totalSeries == 2 && it.absentSeries == 1 && it.categories.firstOrNull()?.count == 1 }

        assertEquals(2, state.totalSeries)
        assertEquals(1, state.absentSeries)
        assertEquals(1, state.totalCategories)
        assertEquals(listOf("En cours", "Sans catégorie"), state.categories.map { it.name })
        assertEquals(1, state.categories.first { it.name == "En cours" }.count)
        assertEquals(CatalogImportStep.Idle, state.importStep)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun tachibkBytes(): ByteArray {
        val backup = MihonBackup(
            backupManga = listOf(
                MihonBackupManga(
                    source = 1L, url = "/manga/1", title = "Kagurabachi", categories = listOf(0L),
                    chapters = listOf(MihonBackupChapter(url = "/manga/1/ch1", name = "Chapitre 1", chapterNumber = 1f)),
                ),
            ),
            backupCategories = listOf(MihonBackupCategory(name = "En cours", order = 0)),
            backupSources = listOf(MihonBackupSource(name = "MangaSource", sourceId = 1L)),
        )
        val bytes = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup)
        return ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(bytes) } }.toByteArray()
    }

    @Test
    fun importReimportSequence_readsNewestTachibkAndUpdatesBackupImport() = runTest {
        val root = tmp.newFolder("mihon-tree")
        val autobackup = File(root, "autobackup").apply { mkdirs() }
        File(autobackup, "com.mihon_older.tachibk").apply {
            writeBytes(tachibkBytes())
            setLastModified(1_000L)
        }
        val newest = File(autobackup, "com.mihon_newest.tachibk").apply {
            writeBytes(tachibkBytes())
            setLastModified(2_000L)
        }

        val fs = FakeMihonFs(root)
        val steps = mutableListOf<CatalogImportStep>()
        importReimportSequence(fs, root.absolutePath, env.db, TestClock()) { steps.add(it) }

        assertEquals(
            listOf(
                CatalogImportStep.ReadingFile,
                CatalogImportStep.Decompressing,
                CatalogImportStep.ReadingSeries,
                CatalogImportStep.ReadingCategories,
                CatalogImportStep.Comparing,
            ),
            steps,
        )
        val imported = env.db.backupImportDao().observe().first()
        assertEquals(newest.name, imported?.name)
        assertEquals(1, env.db.seriesDao().observeAll().first().size)
        assertTrue(env.db.seriesDao().observeAll().first().any { it.title == "Kagurabachi" })
    }
}
