package com.tmm.mobile.workers

import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.db.JobEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.12 : SendJobWorker. Le mode TRANSLATE (upload d'un fichier) exige un arbre Mihon
// SAF reel (MihonTree, construit en dur dans le worker via applicationContext.contentResolver) que
// Robolectric ne peut pas simuler ici (aucun DocumentsProvider de test dans ce module) : seuls les
// modes VERIFY/RETRANSLATE (qui sautent uploadFile(), cf SendJobWorker.kt ligne "if (job.mode ==
// JobMode.TRANSLATE) uploadFile(...) else null") et les gardes d'entree sont donc testables ici.
@RunWith(RobolectricTestRunner::class)
class SendJobWorkerTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun job(jobId: String, phoneState: String, mode: String = JobMode.VERIFY) = JobEntity(
        jobId = jobId, mode = mode, pages = listOf(2), translate = true,
        mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    private fun buildWorker(jobId: String, runAttemptCount: Int = 0): SendJobWorker =
        TestListenableWorkerBuilder<SendJobWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
            .setRunAttemptCount(runAttemptCount)
            .build()

    @Test fun doWork_missingJobId_fails() = runTest {
        val worker = TestListenableWorkerBuilder<SendJobWorker>(ApplicationProvider.getApplicationContext()).build()
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test fun doWork_unknownJob_succeedsAsNoop() = runTest {
        val result = buildWorker("does-not-exist").doWork()
        assertEquals(Result.success(), result)
    }

    @Test fun doWork_wrongPhoneState_isNoop() = runTest {
        env.db.jobDao().upsert(job("job-1", phoneState = "SENT"))
        val result = buildWorker("job-1").doWork()
        assertEquals(Result.success(), result)
        assertEquals("SENT", env.db.jobDao().get("job-1")!!.phoneState)
    }

    @Test fun doWork_verifyMode_writesRequestWithoutFile_andMovesToSent() = runTest {
        env.db.jobDao().upsert(job("job-2", phoneState = "PREPARING", mode = JobMode.VERIFY))

        val result = buildWorker("job-2").doWork()

        assertEquals(Result.success(), result)
        val updated = env.db.jobDao().get("job-2")!!
        assertEquals("SENT", updated.phoneState)
        assertNull(updated.cachePath)
        val request = env.pc.readRequest("job-2")
        assertNull(request.file)
        assertEquals("verify", request.options.mode)
    }

    @Test fun doWork_failsAfterMaxRetries_whenDriveKeepsErroring() = runTest {
        env.db.jobDao().upsert(job("job-3", phoneState = "PREPARING", mode = JobMode.VERIFY))
        env.drive.force429ForNextAttempts = 10

        val result = buildWorker("job-3", runAttemptCount = MAX_RETRY_ATTEMPTS).doWork()

        assertEquals(Result.failure(), result)
        val updated = env.db.jobDao().get("job-3")!!
        assertEquals("FAILED", updated.phoneState)
        assertEquals("send", updated.failedStep)
    }
}
