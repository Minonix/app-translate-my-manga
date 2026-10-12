package com.tmm.mobile.ui.screens.translate

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Maquette v2, écran 6 : la feuille « Traduire » liste les séries ayant au moins un chapitre TO_SEND,
// précoche tout depuis l'Accueil, rien depuis Activité, et envoie translate = true ou false (point A).
@RunWith(RobolectricTestRunner::class)
class TranslateViewModelTest {
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

    private suspend fun seed() {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren"), series("one-piece", "One Piece")))
        // Frieren : 1 chapitre TO_SEND. One Piece : 2, donc en tête (tri décroissant).
        env.db.downloadedChapterDao().upsert(downloaded("frieren", "140", 140f, 20))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1121", 1121f, 19))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1120", 1120f, 18))
    }

    private suspend fun open(request: TranslateRequest): TranslateViewModel {
        seed()
        val vm = TranslateViewModel(env.container)
        vm.onEvent(TranslateEvent.Open(request))
        vm.state.first { it.series.size == 2 }
        return vm
    }

    @Test
    fun state_listsSeriesWithToSendChapters_sortedByCountThenNumber() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        val state = vm.state.value

        assertEquals(listOf("One Piece", "Frieren"), state.series.map { it.title })
        assertEquals(listOf("1120", "1121"), state.series[0].chapters.map { it.numberLabel })
        assertEquals(3, state.totalCount)
        assertEquals(3, state.selectedCount)
    }

    @Test
    fun openFromActivity_checksNothing_thenToggleSeriesChecksIt() = runTest {
        val vm = open(TranslateRequest(preselectAll = false))
        assertEquals(0, vm.state.value.selectedCount)

        vm.onEvent(TranslateEvent.ToggleSeries("one-piece"))
        val state = vm.state.first { it.selectedCount == 2 }
        assertEquals(SeriesCheck.All, state.checkOf(state.series[0]))
        assertEquals(SeriesCheck.None, state.checkOf(state.series[1]))
    }

    @Test
    fun toggleChapter_makesSeriesPartial_andToggleSeriesCompletesIt() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))

        vm.onEvent(TranslateEvent.ToggleChapter("Mihon/one-piece/1120"))
        val partial = vm.state.first { it.selectedCount == 2 }
        assertEquals(SeriesCheck.Partial, partial.checkOf(partial.series[0]))

        vm.onEvent(TranslateEvent.ToggleSeries("one-piece"))
        val full = vm.state.first { it.selectedCount == 3 }
        assertEquals(SeriesCheck.All, full.checkOf(full.series[0]))
    }

    @Test
    fun seriesKeys_restrictsListToChosenSeries() = runTest {
        seed()
        val vm = TranslateViewModel(env.container)
        vm.onEvent(TranslateEvent.Open(TranslateRequest(preselectAll = true, seriesKeys = setOf("frieren"))))
        val state = vm.state.first { it.series.isNotEmpty() }
        assertEquals(listOf("Frieren"), state.series.map { it.title })
    }

    @Test
    fun sendTranslate_createsJobsForSelectedChaptersOnly_withTranslateTrue() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        vm.onEvent(TranslateEvent.ToggleSeries("frieren"))
        vm.state.first { it.selectedCount == 2 }

        vm.onEvent(TranslateEvent.Send(translate = true))
        val done = vm.state.first { it.phase is TranslatePhase.Done }.phase as TranslatePhase.Done

        assertEquals(TranslatePhase.Done(2, translate = true), done)
        val jobs = env.db.jobDao().observeAll().first()
        assertEquals(2, jobs.size)
        assertTrue(jobs.all { it.seriesKey == "one-piece" && it.translate })
    }

    @Test
    fun sendCopy_createsJobsWithTranslateFalse() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))

        vm.onEvent(TranslateEvent.Send(translate = false))
        vm.state.first { it.phase is TranslatePhase.Done }

        val jobs = env.db.jobDao().observeAll().first()
        assertEquals(3, jobs.size)
        assertTrue(jobs.none { it.translate })
    }

    // Garde Busy : rouvrir la feuille pendant un envoi ne doit pas repasser a Idle, sinon un second
    // « Traduire » partirait avant la fin du premier. La base est bloquee par une transaction tenue sur
    // un autre fil, pour que l'envoi reste en cours pendant la reouverture.
    @Test
    fun reopenWhileSending_keepsBusyPhase() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            env.db.runInTransaction {
                locked.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }.apply { start() }
        try {
            assertTrue(locked.await(10, TimeUnit.SECONDS))
            vm.onEvent(TranslateEvent.Send(translate = true))
            vm.state.first { it.phase == TranslatePhase.Busy }

            // Reouverture depuis Activite : rien de coche, mais la phase reste Busy.
            vm.onEvent(TranslateEvent.Open(TranslateRequest(preselectAll = false)))
            val reopened = vm.state.first { it.selectedCount == 0 }
            assertEquals(TranslatePhase.Busy, reopened.phase)
        } finally {
            release.countDown()
            holder.join(10_000)
        }
        assertEquals(TranslatePhase.Done(3, translate = true), vm.state.first { it.phase is TranslatePhase.Done }.phase)
    }
}
