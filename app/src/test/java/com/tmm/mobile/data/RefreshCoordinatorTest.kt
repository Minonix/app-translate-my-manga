package com.tmm.mobile.data

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.SeriesSettingsEntity
import com.tmm.mobile.protocol.ChapterRef
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.Quality
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.RequestOptions
import com.tmm.mobile.protocol.SeriesRef
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.15 : cycle refresh() = poll (statuses/results) + autoTranslate(), sur le meme
// FakeDrive/FakePc que DriveTransportTest (E7).
@RunWith(RobolectricTestRunner::class)
class RefreshCoordinatorTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private suspend fun writeOwnRequest(jobId: String, mihonPath: MihonPath = MihonPath("MangaSource", "Titre", "Chapitre 1")): String {
        env.container.transport.ensureLayout()
        val folderId = env.container.transport.ensureJobFolder(jobId)
        env.container.transport.writeRequest(
            folderId,
            RequestJson(
                jobId = jobId,
                createdAt = "2026-01-01T00:00:00Z",
                device = "TestDevice",
                series = SeriesRef(mihonPath.series, mihonPath.source, null, null),
                chapter = ChapterRef(mihonPath.chapter, 1.0, null, null),
                mihonPath = mihonPath,
                file = null,
                options = RequestOptions(mode = "translate"),
            ),
        )
        return folderId
    }

    private fun job(jobId: String, mihonPath: String = "MangaSource/Titre/Chapitre 1") = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = mihonPath, sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = "SENT",
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    @Test fun refresh_updatesPcState_forOwnJob() = runTest {
        writeOwnRequest("job-1")
        env.db.jobDao().upsert(job("job-1"))
        env.pc.accept("job-1")

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-1")!!
        assertEquals("processing", updated.pcState)
        assertEquals("SENT", updated.phoneState)
    }

    @Test fun refresh_setsInstalled_andStoresQuality_whenResultHasNoFile() = runTest {
        writeOwnRequest("job-2")
        env.db.jobDao().upsert(job("job-2"))
        env.pc.finish(
            "job-2",
            resultBytes = ByteArray(0),
            fileRef = null,
            quality = Quality(totalPages = 10, translatedBubbles = 8, untranslatedBubbles = 1, sfxKept = 2, verifiedAt = null),
        )

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-2")!!
        assertEquals("INSTALLED", updated.phoneState)
        val quality = env.db.chapterQualityDao().observe("MangaSource/Titre/Chapitre 1").first()
        assertEquals(8, quality?.translatedBubbles)
    }

    @Test fun refresh_ignoresStatus_forJobUnknownLocally() = runTest {
        writeOwnRequest("job-not-mine")
        env.pc.accept("job-not-mine")

        env.container.refreshCoordinator.refresh()

        assertTrue(env.db.jobDao().observeAll().first().isEmpty())
        assertNull(env.db.jobDao().get("job-not-mine"))
    }

    @Test fun refresh_triggersAutoTranslate_forDownloadedChapterMarkedAutoTranslate() = runTest {
        env.db.seriesSettingsDao().upsert(SeriesSettingsEntity(seriesKey = "sk1", autoTranslate = true))
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = "MangaSource/Titre/Chapitre 2",
                sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 2",
                documentUri = "doc-1", isCbz = true, sizeBytes = 10L, lastModified = 0L,
                pageCount = 5, seriesKey = "sk1", chapterKey = null, number = 2f,
            ),
        )

        env.container.refreshCoordinator.refresh()

        val jobs = env.db.jobDao().observeAll().first()
        assertEquals(1, jobs.size)
        assertEquals("MangaSource/Titre/Chapitre 2", jobs.single().mihonPath)
        assertEquals(JobMode.TRANSLATE, jobs.single().mode)
    }
}
