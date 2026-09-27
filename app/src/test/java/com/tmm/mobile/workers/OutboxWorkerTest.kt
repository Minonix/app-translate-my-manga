package com.tmm.mobile.workers

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.tmm.mobile.data.Retention
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.fake.FakeFile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.12/CdC 7.3.10 : OutboxWorker (cancel.json/received.json + retention Drive).
@RunWith(RobolectricTestRunner::class)
class OutboxWorkerTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun folder(id: String) = FakeFile(id = id, name = id, parents = mutableListOf("jobs-root"), mimeType = "application/vnd.google-apps.folder", modifiedTime = "2026-01-01T00:00:00Z")

    private fun job(
        jobId: String,
        phoneState: String,
        driveFolderId: String?,
        installedPath: String? = null,
        receivedWrittenAt: Long? = null,
        cancelWrittenAt: Long? = null,
        driveDeletedAt: Long? = null,
    ) = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = driveFolderId, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = installedPath,
        receivedWrittenAt = receivedWrittenAt, cancelWrittenAt = cancelWrittenAt, driveDeletedAt = driveDeletedAt,
    )

    private fun buildWorker(): OutboxWorker =
        TestListenableWorkerBuilder<OutboxWorker>(ApplicationProvider.getApplicationContext()).build()

    @Test fun doWork_writesCancelJson_forCancelRequestedJob() = runTest {
        val f = folder("job-1")
        env.drive.files[f.id] = f
        env.db.jobDao().upsert(job("job-1", phoneState = "CANCEL_REQUESTED", driveFolderId = f.id))

        val result = buildWorker().doWork()

        assertEquals(Result.success(), result)
        assertNotNull(env.drive.childOf(f.id, "cancel.json"))
        assertNotNull(env.db.jobDao().get("job-1")!!.cancelWrittenAt)
    }

    @Test fun doWork_writesReceivedJson_andDeletesFolderImmediately_forAfterReceiveRetention() = runTest {
        env.container.prefs.setRetention(Retention.AFTER_RECEIVE)
        val f = folder("job-2")
        env.drive.files[f.id] = f
        env.db.jobDao().upsert(job("job-2", phoneState = "INSTALLED", driveFolderId = f.id, installedPath = "Titre/Chapitre 1.cbz"))

        buildWorker().doWork()

        assertNotNull(env.drive.childOf(f.id, "received.json"))
        val updated = env.db.jobDao().get("job-2")!!
        assertNotNull(updated.receivedWrittenAt)
        assertNotNull(updated.driveDeletedAt)
        assertFalse(env.drive.files.containsKey(f.id))
    }

    @Test fun doWork_days30Retention_keepsFolder_untilCutoffElapsed() = runTest {
        env.container.prefs.setRetention(Retention.DAYS30)
        val f = folder("job-3")
        env.drive.files[f.id] = f
        env.db.jobDao().upsert(job("job-3", phoneState = "INSTALLED", driveFolderId = f.id, installedPath = "Titre/Chapitre 1.cbz"))

        buildWorker().doWork()
        val afterFirstRun = env.db.jobDao().get("job-3")!!
        assertNotNull(afterFirstRun.receivedWrittenAt)
        assertNull(afterFirstRun.driveDeletedAt)
        assertTrue(env.drive.files.containsKey(f.id))

        // Toujours avant le cutoff : la deuxieme passe (received deja ecrit) ne supprime rien non plus.
        buildWorker().doWork()
        assertTrue(env.drive.files.containsKey(f.id))

        env.clock.millis += 31L * 24 * 60 * 60 * 1000
        buildWorker().doWork()

        val afterCutoff = env.db.jobDao().get("job-3")!!
        assertNotNull(afterCutoff.driveDeletedAt)
        assertFalse(env.drive.files.containsKey(f.id))
    }
}
