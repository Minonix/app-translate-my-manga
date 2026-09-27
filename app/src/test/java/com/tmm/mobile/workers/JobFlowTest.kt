package com.tmm.mobile.workers

import com.tmm.mobile.data.flatten
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.JobTarget
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.Quality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.12/5.17 : flux VERIFY complet PREPARING -> SENT (SendJobWorker) -> INSTALLED
// (RefreshCoordinator.refresh() sur un result.json sans fichier). Le mode TRANSLATE (upload +
// installation locale via MihonTree/SAF) n'est pas rejouable ici, cf SendJobWorkerTest/InstallJobWorkerTest.
@RunWith(RobolectricTestRunner::class)
class JobFlowTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun buildSendWorker(jobId: String): SendJobWorker =
        TestListenableWorkerBuilder<SendJobWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
            .build()

    @Test fun verifyJob_flowsFromPreparingToInstalled_viaSendAndRefresh() = runTest {
        val mihonPath = MihonPath("MangaSource", "Titre", "Chapitre 1")
        env.db.pcIndexChapterDao().insertAll(listOf(PcIndexChapterEntity(mihonPath.flatten(), original = true, fr = false)))

        val created = env.container.jobRepository.createJobs(
            JobMode.VERIFY,
            listOf(JobTarget(mihonPath = mihonPath, downloaded = null, series = null, chapter = null)),
        )
        val jobId = created.createdJobIds.single()
        assertEquals("PREPARING", env.db.jobDao().get(jobId)!!.phoneState)

        val sendResult = buildSendWorker(jobId).doWork()
        assertEquals(Result.success(), sendResult)
        assertEquals("SENT", env.db.jobDao().get(jobId)!!.phoneState)

        env.pc.finish(
            jobId,
            resultBytes = ByteArray(0),
            fileRef = null,
            quality = Quality(totalPages = 12, translatedBubbles = 11, untranslatedBubbles = 0, sfxKept = 3, verifiedAt = null),
        )

        env.container.refreshCoordinator.refresh()

        val installed = env.db.jobDao().get(jobId)!!
        assertEquals("INSTALLED", installed.phoneState)
        val quality = env.db.chapterQualityDao().observe(mihonPath.flatten()).first()
        assertEquals(11, quality?.translatedBubbles)
        assertEquals(jobId, quality?.jobId)
    }
}
