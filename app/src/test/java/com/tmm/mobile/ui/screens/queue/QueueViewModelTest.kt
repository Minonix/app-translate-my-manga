package com.tmm.mobile.ui.screens.queue

import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.QueueCounts
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

// PLAN_GEMINI.md 7.5 (E10) : le heartbeat (activeJobIds/slots) dit quels jobs tournent reellement
// sur le PC, le reste de la pipeline vient de job.phoneState (Active/Ensuite), les jobs
// RESULT_READY/INSTALLED des 7 derniers jours nourrissent l'onglet Termines, les FAILED sans job
// plus recent sur le meme mihonPath nourrissent l'onglet Echecs.
@RunWith(RobolectricTestRunner::class)
class QueueViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun baseJob(jobId: String, mihonPath: String, phoneState: String) = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = mihonPath, sourceDir = "Mihon", seriesDir = mihonPath.split("/")[1], chapterDir = mihonPath.split("/")[2],
        seriesKey = mihonPath.split("/")[1], chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = "translate", pcPage = 3, pcPages = 10, pcEngine = "gpt",
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    private fun heartbeatJson(activeJobIds: List<String>) = ProtocolJson.encodeToString(
        Heartbeat.serializer(),
        Heartbeat(pcName = "PC-BUREAU", appVersion = "1.0", updatedAt = "2026-01-01T00:00:00Z", slots = 4, activeJobIds = activeJobIds, queue = QueueCounts(0, 0, 0)),
    )

    @Test
    fun state_usesHeartbeatActiveJobIds_forActiveTab_andRestForWaiting() = runTest {
        env.db.jobDao().upsert(baseJob("job-1", "Mihon/frieren/140", "SENT"))
        env.db.jobDao().upsert(baseJob("job-2", "Mihon/one-piece/1120", "PREPARING"))
        env.prefs.setHeartbeatJson(heartbeatJson(listOf("job-1")))

        val vm = QueueViewModel(env.container)
        val state = vm.state.first { it.activeJobs.size == 1 }

        assertEquals(1, state.activeJobs.size)
        assertEquals(1, state.waitingJobs.size)
        assertEquals("job-2", state.waitingJobs.first().id)
        assertEquals(1, state.slotsCount)
        assertEquals(4, state.slotsMax)
    }

    @Test
    fun state_listsResultReadyJobsAsDone_withQualityJoin() = runTest {
        env.db.jobDao().upsert(baseJob("job-3", "Mihon/frieren/139", "RESULT_READY"))
        env.db.chapterQualityDao().upsert(
            ChapterQualityEntity(
                mihonPath = "Mihon/frieren/139", totalPages = 20, translatedBubbles = 40,
                untranslatedBubbles = 2, sfxKept = 3, verifiedAt = null, alerts = emptyList(), jobId = "job-3",
            ),
        )

        val vm = QueueViewModel(env.container)
        val state = vm.state.first { it.doneJobs.isNotEmpty() }

        val done = state.doneJobs.first()
        assertEquals("Mihon/frieren/139", done.mihonPath)
        assertEquals(20, done.totalPages)
        assertEquals(40, done.totalBubbles)
    }

    @Test
    fun state_listsFailedJobs_untilANewerJobExistsForSameChapter() = runTest {
        env.db.jobDao().upsert(baseJob("job-4", "Mihon/frieren/138", "FAILED").copy(pcErrorMessage = "moteur indisponible", pcErrorPage = 5))

        val vm = QueueViewModel(env.container)
        val withFailure = vm.state.first { it.failedJobs.isNotEmpty() }
        assertEquals(1, withFailure.failedJobs.size)
        assertEquals("job-4", withFailure.failedJobs.first().jobId)

        // Un retry() cree un job plus recent sur le meme mihonPath -> l'echec disparait de la liste.
        vm.onEvent(QueueEvent.RetryFailedJob("job-4"))
        val afterRetry = vm.state.first { it.failedJobs.isEmpty() }
        assertTrue(afterRetry.failedJobs.isEmpty())
        assertEquals(2, env.db.jobDao().observeAll().first().size)
    }

    @Test
    fun removeWaitingJob_cancelsThroughJobRepository() = runTest {
        env.db.jobDao().upsert(baseJob("job-5", "Mihon/frieren/137", "SENT"))

        val vm = QueueViewModel(env.container)
        vm.state.first { it.waitingJobs.size == 1 }

        vm.onEvent(QueueEvent.RemoveWaitingJob("job-5"))
        val state = vm.state.first { it.waitingJobs.isEmpty() }

        assertTrue(state.waitingJobs.isEmpty())
        assertEquals("CANCEL_REQUESTED", env.db.jobDao().get("job-5")?.phoneState)
    }
}
