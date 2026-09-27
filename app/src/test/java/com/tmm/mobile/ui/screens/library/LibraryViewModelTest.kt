package com.tmm.mobile.ui.screens.library

import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.flatten
import com.tmm.mobile.mihon.MihonNaming
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.2 (E10) : verifie que LibraryViewModel agrege series/categories/chapitres reels
// (Room) en LibraryState, sur un jeu de donnees representatif (pas la maquette entiere - un
// "Envoyer" et un "A jour" suffisent pour couvrir SeriesSummary.badge()/deriveStatus()).
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private suspend fun seedSeriesWithOneChapterToSend(seriesKey: String, title: String, categoryOrder: Long? = null) {
        env.db.seriesDao().upsert(
            SeriesEntity(
                seriesKey = seriesKey, sourceId = "1", url = "https://x/$seriesKey", title = title, author = null,
                status = 0, thumbnailUrl = null, sourceName = "MangaSource", dateAdded = 0L, mangaType = "manga",
            ),
        )
        val chapterKey = "$seriesKey-1"
        env.db.chapterDao().upsertAll(
            listOf(ChapterEntity(chapterKey = chapterKey, seriesKey = seriesKey, url = "https://x/$chapterKey", name = "Chapitre 1", number = 1f, scanlator = null, read = false, dateUpload = 0L)),
        )
        val mihonPath = MihonPath(MihonNaming.getSourceDirName("MangaSource"), MihonNaming.getMangaDirName(title), MihonNaming.getChapterDirName("Chapitre 1", null, "https://x/$chapterKey"))
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = mihonPath.flatten(), sourceDir = mihonPath.source, seriesDir = mihonPath.series, chapterDir = mihonPath.chapter,
                documentUri = "content://x", isCbz = false, sizeBytes = 10L, lastModified = 0L, pageCount = 5,
                seriesKey = seriesKey, chapterKey = chapterKey, number = 1f,
            ),
        )
        if (categoryOrder != null) env.db.seriesCategoryDao().insertAll(listOf(SeriesCategoryEntity(seriesKey, categoryOrder)))
    }

    @Test fun state_reflectsRealSeriesAndBadge_toSend() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)

        val state = vm.state.first { it.allSeries.isNotEmpty() }
        val series = state.allSeries.single()
        assertEquals("Blue Lock", series.title)
        assertEquals("blue-lock", series.seriesKey)
        assertEquals(1, series.toSend)
        assertEquals(StatusTone.Send, series.tone)
        assertEquals("1 à envoyer", series.badge)
    }

    @Test fun state_upToDateSeries_hasOkBadgeAndFrCount() = runTest {
        val seriesKey = "dandadan"
        env.db.seriesDao().upsert(
            SeriesEntity(seriesKey = seriesKey, sourceId = "1", url = "https://x/$seriesKey", title = "Dandadan", author = null, status = 0, thumbnailUrl = null, sourceName = "MangaSource", dateAdded = 0L, mangaType = "manga"),
        )
        val chapterKey = "$seriesKey-1"
        env.db.chapterDao().upsertAll(
            listOf(ChapterEntity(chapterKey = chapterKey, seriesKey = seriesKey, url = "https://x/$chapterKey", name = "Chapitre 1", number = 1f, scanlator = null, read = false, dateUpload = 0L)),
        )
        val mihonPath = MihonPath(MihonNaming.getSourceDirName("MangaSource"), MihonNaming.getMangaDirName("Dandadan"), MihonNaming.getChapterDirName("Chapitre 1", null, "https://x/$chapterKey"))
        env.db.localFrChapterDao().upsert(
            LocalFrChapterEntity(localPath = "content://fr/1", folderName = "Dandadan", fileName = "1.cbz", documentUri = "content://fr/1", lastModified = 0L, jobId = null, mihonPath = mihonPath.flatten()),
        )

        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        val state = vm.state.first { it.allSeries.isNotEmpty() }
        val series = state.allSeries.single()
        assertEquals(1, series.frChapters)
        assertEquals(0, series.toSend)
        assertEquals(StatusTone.Ok, series.tone)
    }

    @Test fun categoryFilter_countsAndFiltersBySeriesCategoryLink() = runTest {
        env.db.categoryDao().upsertAll(listOf(CategoryEntity(order = 1L, name = "En cours")))
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock", categoryOrder = 1L)
        seedSeriesWithOneChapterToSend("chainsaw-man", "Chainsaw Man")

        val vm = LibraryViewModel(env.container, initialCategoryOrder = 1L)
        val state = vm.state.first { it.allSeries.size == 2 && it.category == "En cours" }
        assertEquals(listOf("Tous" to 2, "En cours" to 1), state.categories)
    }

    @Test fun sendToPc_createsJobsForSelectedToSendChapters() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        vm.state.first { it.allSeries.isNotEmpty() }
        vm.onEvent(LibraryEvent.ToggleSelected("Blue Lock"))
        vm.state.first { "Blue Lock" in it.selected }

        vm.onEvent(LibraryEvent.SendToPc)
        val state = vm.state.first { it.toast != null }

        assertEquals(LibraryToast.Sent(1), state.toast)
        assertEquals(1, env.db.jobDao().observeAll().first().size)
    }
}
