package com.tmm.mobile.ui.screens.newtranslation

import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.7 (E10) : la feuille "Nouvelle traduction" liste les series ayant au moins un
// chapitre TO_SEND (triees par nombre decroissant), permet de choisir les chapitres puis appelle
// JobRepository.createJobs a l'envoi.
@RunWith(RobolectricTestRunner::class)
class NewTranslationViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun series(key: String, title: String) = SeriesEntity(
        seriesKey = key, sourceId = "src", url = "url/$key", title = title, author = null,
        status = 0, thumbnailUrl = null, sourceName = "Mihon", dateAdded = 0L, mangaType = null,
    )

    private fun downloaded(seriesKey: String, chapterDir: String, number: Float, pages: Int) = DownloadedChapterEntity(
        mihonPath = "Mihon/$seriesKey/$chapterDir", sourceDir = "Mihon", seriesDir = seriesKey, chapterDir = chapterDir,
        documentUri = "doc://$seriesKey/$chapterDir", isCbz = true, sizeBytes = 1L, lastModified = 0L, pageCount = pages,
        seriesKey = seriesKey, chapterKey = null, number = number,
    )

    private suspend fun seedTwoSeriesWithToSendChapters() {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren"), series("one-piece", "One Piece")))
        env.db.categoryDao().upsertAll(listOf(CategoryEntity(order = 0L, name = "En cours")))
        env.db.seriesCategoryDao().insertAll(listOf(SeriesCategoryEntity(seriesKey = "frieren", categoryOrder = 0L)))
        // Frieren : 1 chapitre TO_SEND. One Piece : 2 chapitres TO_SEND -> doit passer en tete (tri decroissant).
        env.db.downloadedChapterDao().upsert(downloaded("frieren", "140", 140f, 20))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1120", 1120f, 18))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1121", 1121f, 19))
    }

    @Test
    fun state_listsOnlySeriesWithToSendChapters_sortedByCountDescending() = runTest {
        seedTwoSeriesWithToSendChapters()

        val vm = NewTranslationViewModel(env.container)
        val state = vm.state.first { it.seriesList.size == 2 }

        assertEquals(listOf("One Piece", "Frieren"), state.seriesList.map { it.title })
        assertEquals(2, state.seriesList.first { it.title == "One Piece" }.chapters.size)
        assertEquals("En cours", state.seriesList.first { it.title == "Frieren" }.subtitle)
    }

    @Test
    fun selectSeries_preselectsAllChapters_thenToggleAllUnchecksEverything() = runTest {
        seedTwoSeriesWithToSendChapters()
        val vm = NewTranslationViewModel(env.container)
        vm.state.first { it.seriesList.size == 2 }

        vm.onEvent(NewTranslationEvent.SelectSeries("one-piece"))
        val selected = vm.state.first { it.isStep2 }
        assertEquals(2, selected.selectedCount)
        assertTrue(selected.isAllChaptersSelected)

        vm.onEvent(NewTranslationEvent.ToggleAllChapters)
        val unselected = vm.state.first { it.selectedCount == 0 }
        assertEquals(0, unselected.selectedCount)
    }

    @Test
    fun send_createsJobsForSelectedChaptersOnly_andReachesDonePhase() = runTest {
        seedTwoSeriesWithToSendChapters()
        val vm = NewTranslationViewModel(env.container)
        vm.state.first { it.seriesList.size == 2 }

        vm.onEvent(NewTranslationEvent.SelectSeries("one-piece"))
        val step2 = vm.state.first { it.isStep2 }
        // On ne garde qu'un seul chapitre coche avant l'envoi.
        val toUncheck = step2.selectedSeries!!.chapters.first().mihonPath
        vm.onEvent(NewTranslationEvent.ToggleChapter(toUncheck))

        vm.onEvent(NewTranslationEvent.Send)
        val done = vm.state.first { it.phase == NewTranslationPhase.Done }

        assertEquals(1, done.selectedCount)
        val jobs = env.db.jobDao().observeAll().first()
        assertEquals(1, jobs.size)
        assertEquals("one-piece", jobs.first().seriesKey)
    }
}
