package com.tmm.mobile.data

import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.fake.FakeFile
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.17 : dedoublonnage createJobs(), annulation par phoneState. Les workers ne sont
// pas executes ici (WorkManager.enqueueSendJob n'est jamais attendu) : seul l'etat DB/CreateJobsResult
// produit par JobRepository est verifie.
@RunWith(RobolectricTestRunner::class)
class JobRepositoryTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun target() = JobTarget(
        mihonPath = MihonPath("MangaSource", "Titre", "Chapitre 1"),
        downloaded = null,
        series = null,
        chapter = null,
    )

    @Test fun createJobs_refusesTranslate_whenActiveJobAlreadyExists() = runTest {
        val repo = env.container.jobRepository
        val first = repo.createJobs(JobMode.TRANSLATE, listOf(target()))
        assertEquals(1, first.createdJobIds.size)
        assertEquals(0, first.refused)

        val second = repo.createJobs(JobMode.TRANSLATE, listOf(target()))
        assertEquals(0, second.createdJobIds.size)
        assertEquals(1, second.refused)
        assertEquals(1, env.db.jobDao().observeAll().first().size)
    }

    @Test fun createJobs_allowsTranslate_onceActiveJobIsTerminal() = runTest {
        val repo = env.container.jobRepository
        val first = repo.createJobs(JobMode.TRANSLATE, listOf(target()))
        val jobId = first.createdJobIds.single()
        val job = env.db.jobDao().get(jobId)!!
        env.db.jobDao().upsert(job.copy(phoneState = "INSTALLED"))

        val second = repo.createJobs(JobMode.TRANSLATE, listOf(target()))
        assertEquals(1, second.createdJobIds.size)
        assertEquals(0, second.refused)
    }

    @Test fun createJobs_refusesVerify_whenIndexHasNoOriginal() = runTest {
        val flatPath = MihonPath("MangaSource", "Titre", "Chapitre 1").flatten()
        env.db.pcIndexChapterDao().insertAll(listOf(PcIndexChapterEntity(flatPath, original = false, fr = false)))

        val result = env.container.jobRepository.createJobs(JobMode.VERIFY, listOf(target()))
        assertEquals(0, result.createdJobIds.size)
        assertEquals(1, result.refused)
    }

    @Test fun createJobs_allowsVerify_whenIndexHasOriginal() = runTest {
        val flatPath = MihonPath("MangaSource", "Titre", "Chapitre 1").flatten()
        env.db.pcIndexChapterDao().insertAll(listOf(PcIndexChapterEntity(flatPath, original = true, fr = false)))

        val result = env.container.jobRepository.createJobs(JobMode.VERIFY, listOf(target()))
        assertEquals(1, result.createdJobIds.size)
        assertEquals(0, result.refused)
    }

    @Test fun cancel_onPreparing_deletesJobAndDriveFolder() = runTest {
        val folder = FakeFile(name = "job-1", parents = mutableListOf("jobs-root"), mimeType = "application/vnd.google-apps.folder", modifiedTime = "2026-01-01T00:00:00Z")
        env.drive.files[folder.id] = folder
        env.db.jobDao().upsert(job(jobId = "job-1", phoneState = "PREPARING", driveFolderId = folder.id))

        env.container.jobRepository.cancel("job-1")

        assertNull(env.db.jobDao().get("job-1"))
        assertFalse(env.drive.files.containsKey(folder.id))
    }

    @Test fun cancel_onSent_marksCancelRequested_keepsJob() = runTest {
        env.db.jobDao().upsert(job(jobId = "job-2", phoneState = "SENT"))

        env.container.jobRepository.cancel("job-2")

        val job = env.db.jobDao().get("job-2")!!
        assertEquals("CANCEL_REQUESTED", job.phoneState)
    }

    @Test fun cancel_onInstalled_isNoop() = runTest {
        env.db.jobDao().upsert(job(jobId = "job-3", phoneState = "INSTALLED"))

        env.container.jobRepository.cancel("job-3")

        assertEquals("INSTALLED", env.db.jobDao().get("job-3")!!.phoneState)
    }

    // Job Drive SENT jamais vu par le PC (aucun statut) : supprime avec son dossier Drive et recree.
    @Test fun resendStalledDriveJobs_recreatesUnseenSentJob() = runTest {
        val folder = FakeFile(name = "job-4", parents = mutableListOf("jobs-root"), mimeType = "application/vnd.google-apps.folder", modifiedTime = "2026-01-01T00:00:00Z")
        env.drive.files[folder.id] = folder
        env.db.jobDao().upsert(job(jobId = "job-4", phoneState = "SENT", driveFolderId = folder.id))
        env.clock.millis += STALLED_SENT_MILLIS

        env.container.jobRepository.resendStalledDriveJobs()

        assertNull(env.db.jobDao().get("job-4"))
        assertFalse(env.drive.files.containsKey(folder.id))
        val recreated = env.db.jobDao().observeAll().first().single()
        assertEquals("PREPARING", recreated.phoneState)
        assertEquals("MangaSource/Titre/Chapitre 1", recreated.mihonPath)
    }

    @Test fun resendStalledDriveJobs_keepsSentJobSeenByPc_orTooRecent() = runTest {
        env.db.jobDao().upsert(job(jobId = "job-5", phoneState = "SENT").copy(pcState = "queued", mihonPath = "S/T/C5"))
        env.db.jobDao().upsert(job(jobId = "job-6", phoneState = "SENT").copy(mihonPath = "S/T/C6"))
        env.clock.millis += STALLED_SENT_MILLIS - 1

        env.container.jobRepository.resendStalledDriveJobs()

        assertEquals(listOf("job-5", "job-6"), env.db.jobDao().observeAll().first().map { it.jobId }.sorted())
    }

    private fun job(jobId: String, phoneState: String, driveFolderId: String? = null) = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = driveFolderId, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )
}
