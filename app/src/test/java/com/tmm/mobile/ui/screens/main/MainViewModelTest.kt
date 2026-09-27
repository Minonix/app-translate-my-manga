package com.tmm.mobile.ui.screens.main

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.QueueCounts
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.1/E10.1 : MainViewModel combine Room + prefs (heartbeat, dernier
// rafraichissement/scan) - on seme une serie a envoyer et une serie recue puis on verifie les
// textes/compteurs produits, comme la maquette Main.dc.html.
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun series(key: String, title: String) = SeriesEntity(
        seriesKey = key, sourceId = "src", url = "url/$key", title = title, author = null,
        status = 0, thumbnailUrl = null, sourceName = "Mihon", dateAdded = 0L, mangaType = null,
    )

    @Test
    fun state_reflectsToSendChapterAndInstalledRecentChapter() = runTest {
        env.db.seriesDao().upsert(series("frieren", "Frieren"))
        env.db.seriesDao().upsert(series("one-piece", "One Piece"))
        env.db.chapterDao().upsertAll(
            listOf(
                ChapterEntity("frieren/140", "frieren", "url", "Ch. 140", 140f, null, false, 0L),
                ChapterEntity("one-piece/1120", "one-piece", "url", "Ch. 1120", 1120f, null, false, 0L),
            ),
        )
        // Frieren 140 : telechargee, aucun job/index -> TO_SEND.
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = "Mihon/frieren/140", sourceDir = "Mihon", seriesDir = "frieren", chapterDir = "140",
                documentUri = "doc://140", isCbz = true, sizeBytes = 50_000_000L, lastModified = 0L, pageCount = 20,
                seriesKey = "frieren", chapterKey = "frieren/140", number = 140f,
            ),
        )
        // One Piece 1120 : deja installee (recue) -> apparait dans les chapitres recents.
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = "Mihon/one-piece/1120", sourceDir = "Mihon", seriesDir = "one-piece", chapterDir = "1120",
                documentUri = "doc://1120", isCbz = true, sizeBytes = 10_000_000L, lastModified = 0L, pageCount = 18,
                seriesKey = "one-piece", chapterKey = "one-piece/1120", number = 1120f,
            ),
        )
        env.db.jobDao().upsert(
            JobEntity(
                jobId = "job-1", mode = "translate", pages = null, translate = true,
                mihonPath = "Mihon/one-piece/1120", sourceDir = "Mihon", seriesDir = "one-piece", chapterDir = "1120",
                seriesKey = "one-piece", chapterKey = "one-piece/1120", phoneState = "INSTALLED",
                failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
                cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0L, transferTotal = 0L,
                pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
                pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
                statusUpdatedAt = null, resultJson = null, resultFileId = null,
                createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L, finishedAt = "2026-01-01T00:00:00Z",
                installedAt = 1_700_000_000_000L, installedPath = null, receivedWrittenAt = null,
            ),
        )

        val heartbeat = Heartbeat(
            pcName = "PC-BUREAU", appVersion = "1.0", updatedAt = Instant.ofEpochMilli(env.clock.millis).toString(),
            slots = 1, activeJobIds = emptyList(), queue = QueueCounts(active = 0, waiting = 0, failed = 0),
        )
        env.prefs.setHeartbeatJson(ProtocolJson.encodeToString(Heartbeat.serializer(), heartbeat))
        env.prefs.setLastRefreshAt(env.clock.millis)

        val vm = MainViewModel(env.container)
        val state = vm.state.first { it.toSendCount == 1 }

        assertEquals(1, state.toSendCount)
        assertEquals("1 série · environ 50 Mo", state.toSendSummary)
        assertEquals(PcTone.Online, state.pcTone)
        assertEquals(1, state.recentChapters.size)
        assertEquals("One Piece", state.recentChapters.first().title)
        assertEquals("Ch. 1120", state.recentChapters.first().chapterLabel)
        assertEquals(true, state.recentChapters.first().inMihon)
    }
}
