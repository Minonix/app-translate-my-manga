package com.tmm.mobile.ui.screens.transfers

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.4 (E10) : SyncViewModel derive "a envoyer"/"a recuperer"/manquants de Room
// (downloaded_chapter, job, chapter) comme GlobalCounts.kt, et SendUp/GetDown appellent le vrai
// JobRepository (WorkManager n'est pas attendu ici, seul l'etat DB/StateFlow est verifie).
@RunWith(RobolectricTestRunner::class)
class SyncViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun series(key: String, title: String) = SeriesEntity(
        seriesKey = key, sourceId = "src", url = "url/$key", title = title, author = null,
        status = 0, thumbnailUrl = null, sourceName = "Mihon", dateAdded = 0L, mangaType = null,
    )

    private fun downloaded(seriesKey: String, chapterDir: String, number: Float, sizeBytes: Long = 1_000_000L) = DownloadedChapterEntity(
        mihonPath = "Mihon/$seriesKey/$chapterDir", sourceDir = "Mihon", seriesDir = seriesKey, chapterDir = chapterDir,
        documentUri = "doc://$seriesKey/$chapterDir", isCbz = true, sizeBytes = sizeBytes, lastModified = 0L, pageCount = 20,
        seriesKey = seriesKey, chapterKey = "$seriesKey/$chapterDir", number = number,
    )

    private fun resultReadyJob(seriesKey: String, chapterDir: String) = JobEntity(
        jobId = "job-$seriesKey-$chapterDir", mode = "translate", pages = null, translate = true,
        mihonPath = "Mihon/$seriesKey/$chapterDir", sourceDir = "Mihon", seriesDir = seriesKey, chapterDir = chapterDir,
        seriesKey = seriesKey, chapterKey = null, phoneState = "RESULT_READY",
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    @Test
    fun state_countsToSendAndGroupsBySeries() = runTest {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren"), series("one-piece", "One Piece")))
        env.db.downloadedChapterDao().upsert(downloaded("frieren", "140", 140f))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1120", 1120f))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1121", 1121f))

        val vm = SyncViewModel(env.container)
        val state = vm.state.first { it.upCount == 3 }

        assertEquals(3, state.upCount)
        assertEquals("One Piece", state.upSeries.first().title)
    }

    @Test
    fun state_countsResultReadyJobsAsToFetch() = runTest {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren")))
        env.db.jobDao().upsert(resultReadyJob("frieren", "140"))

        val vm = SyncViewModel(env.container)
        val state = vm.state.first { it.downCount == 1 && it.downSeries.firstOrNull()?.title == "Frieren" }

        assertEquals(1, state.downCount)
        assertEquals("Frieren", state.downSeries.first().title)
    }

    @Test
    fun sendUp_createsJobsForToSendChapters() = runTest {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren")))
        env.db.downloadedChapterDao().upsert(downloaded("frieren", "140", 140f))

        val vm = SyncViewModel(env.container)
        vm.state.first { it.upCount == 1 }

        vm.onEvent(SyncEvent.SendUp)
        val state = vm.state.first { it.sendState is SendState.Done }

        assertEquals(1, (state.sendState as SendState.Done).chapterCount)
        assertEquals(1, env.db.jobDao().observeAll().first().size)
    }

    @Test
    fun getDown_fetchesResultReadyJobs() = runTest {
        env.db.jobDao().upsert(resultReadyJob("frieren", "140"))

        val vm = SyncViewModel(env.container)
        vm.state.first { it.downCount == 1 && it.downSeries.isNotEmpty() }

        vm.onEvent(SyncEvent.GetDown)
        val state = vm.state.first { it.fetchState is FetchState.Done }

        assertEquals(FetchState.Done, state.fetchState)
    }

    @Test
    fun toggleAutoTranslate_flipsPref() = runTest {
        val vm = SyncViewModel(env.container)
        val initial = vm.state.first { true }
        assertEquals(true, initial.autoTranslate)

        vm.onEvent(SyncEvent.ToggleAutoTranslate)
        val state = vm.state.first { !it.autoTranslate }

        assertEquals(false, state.autoTranslate)
    }

    @Test
    fun state_reportsMissingChaptersNotDownloadedNorLocalFr() = runTest {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren")))
        env.db.chapterDao().upsertAll(
            listOf(
                ChapterEntity(chapterKey = "frieren/1", seriesKey = "frieren", url = "u1", name = "Ch 1", number = 1f, scanlator = null, read = false, dateUpload = 0L),
                ChapterEntity(chapterKey = "frieren/2", seriesKey = "frieren", url = "u2", name = "Ch 2", number = 2f, scanlator = null, read = false, dateUpload = 0L),
                ChapterEntity(chapterKey = "frieren/3", seriesKey = "frieren", url = "u3", name = "Ch 3", number = 3f, scanlator = null, read = false, dateUpload = 0L),
            ),
        )
        // Chapitre 2 jamais telecharge ni en FR local -> manquant entre 1 et 3.
        env.db.downloadedChapterDao().upsert(downloaded("frieren", "1", 1f).copy(chapterKey = "frieren/1"))
        env.db.downloadedChapterDao().upsert(downloaded("frieren", "3", 3f).copy(chapterKey = "frieren/3"))

        val vm = SyncViewModel(env.container)
        val state = vm.state.first { it.missingCount > 0 }

        assertEquals(1, state.missingCount)
        assertEquals("frieren", state.missingSeriesKey)
    }
}
