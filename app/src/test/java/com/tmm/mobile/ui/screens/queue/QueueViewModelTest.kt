package com.tmm.mobile.ui.screens.queue

import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.status.GlobalCounts
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

    private fun downloaded(mihonPath: String) = DownloadedChapterEntity(
        mihonPath = mihonPath, sourceDir = "Mihon", seriesDir = mihonPath.split("/")[1], chapterDir = mihonPath.split("/")[2],
        documentUri = "doc://$mihonPath", isCbz = true, sizeBytes = 1_000L, lastModified = 0L, pageCount = 10,
        seriesKey = mihonPath.split("/")[1], chapterKey = null, number = null,
    )

    private fun heartbeatJson(activeJobIds: List<String>) = ProtocolJson.encodeToString(
        Heartbeat.serializer(),
        Heartbeat(pcName = "PC-BUREAU", appVersion = "1.0", updatedAt = "2026-01-01T00:00:00Z", slots = 4, activeJobIds = activeJobIds, queue = QueueCounts(0, 0, 0)),
    )

    @Test
    fun activity_excludesStaleHeartbeatAndFailedJobs_andCountsTransfersOnce() = runTest {
        val jobs = listOf(
            baseJob("sent", "Mihon/s/1", "SENT"),
            baseJob("upload", "Mihon/s/2", "UPLOADING").copy(transferredBytes = 98, transferTotal = 100),
            baseJob("download", "Mihon/s/3", "DOWNLOADING"),
            baseJob("done", "Mihon/s/4", "INSTALLED"),
            baseJob("failed", "Mihon/s/5", "SENT").copy(pcState = "failed"),
            baseJob("cancel", "Mihon/s/6", "CANCEL_REQUESTED"),
        )
        jobs.forEach { env.db.jobDao().upsert(it) }
        env.prefs.setHeartbeatJson(heartbeatJson(jobs.map { it.jobId } + "sent"))
        val state = QueueViewModel(env.container).state.first { it.transfers.size == 2 && it.activeJobs.size == 2 }
        assertEquals(4, state.activeCount)
        assertEquals(2, state.slotsCount)
        assertTrue(state.waitingJobs.isEmpty())
        assertEquals(98, state.transfers.first { it.isUpload }.percent)
        assertEquals(state.activeCount, GlobalCounts.activityBadgeCount(env.db).first())
    }

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
        // Chapitre telecharge : l'echec est relancable, donc affiche.
        env.db.downloadedChapterDao().upsert(downloaded("Mihon/frieren/138"))
        env.db.jobDao().upsert(baseJob("job-4", "Mihon/frieren/138", "FAILED").copy(pcErrorMessage = "moteur indisponible", pcErrorPage = 5))

        val vm = QueueViewModel(env.container)
        val withFailure = vm.state.first { it.failedJobs.isNotEmpty() }
        assertEquals(1, withFailure.failedJobs.size)
        assertEquals("job-4", withFailure.failedJobs.first().jobId)

        // Un retry() cree un job plus recent sur le meme mihonPath -> l'echec disparait de la liste.
        env.clock.millis += 1_000L // horloge figee : le nouveau job doit etre strictement plus recent
        vm.onEvent(QueueEvent.RetryFailedJob("job-4"))
        val afterRetry = vm.state.first { it.failedJobs.isEmpty() }
        assertTrue(afterRetry.failedJobs.isEmpty())
        assertEquals(2, env.db.jobDao().observeAll().first().size)
    }

    // Echecs herites des anciennes versions (chapitre jamais telecharge) : masques a l'affichage,
    // compteur compris, sans toucher a la base ; un chapitre telecharge ou en original sur le PC reste.
    @Test
    fun state_hidesFailedJobs_thatCanNoLongerBeRetried() = runTest {
        env.db.jobDao().upsert(
            baseJob("job-gone", "Mihon/frieren/10", "FAILED")
                .copy(failedStep = "send", errorMessage = "chapitre telecharge introuvable: Mihon/frieren/10"),
        )
        env.db.jobDao().upsert(baseJob("job-src", "Mihon/frieren/11", "FAILED").copy(pcErrorCode = "source_missing"))
        env.db.jobDao().upsert(baseJob("job-dl", "Mihon/frieren/12", "FAILED").copy(pcErrorMessage = "moteur indisponible"))
        env.db.downloadedChapterDao().upsert(downloaded("Mihon/frieren/12"))
        env.db.jobDao().upsert(baseJob("job-pc", "Mihon/frieren/13", "FAILED").copy(pcErrorMessage = "moteur indisponible"))
        env.db.pcIndexChapterDao().insertAll(
            listOf(
                PcIndexChapterEntity("Mihon/frieren/13", original = true, fr = false),
                PcIndexChapterEntity("Mihon/frieren/11", original = false, fr = false),
            ),
        )

        val vm = QueueViewModel(env.container)
        val state = vm.state.first { it.failedJobs.size >= 2 }

        assertEquals(setOf("job-dl", "job-pc"), state.failedJobs.map { it.jobId }.toSet())
        assertEquals(2, state.failCount)
        assertEquals(4, env.db.jobDao().observeAll().first().size)
    }

    @Test
    fun removeWaitingJob_cancelsThroughJobRepository() = runTest {
        env.db.jobDao().upsert(baseJob("job-5", "Mihon/frieren/137", "SENT"))

        val vm = QueueViewModel(env.container)
        vm.state.first { it.waitingJobs.size == 1 }

        vm.onEvent(QueueEvent.RemoveWaitingJob("job-5"))

        // PLAN_GEMINI.md 5.x : SENT -> CANCEL_REQUESTED, le job reste en attente jusqu'a la
        // confirmation du PC (CANCEL_REQUESTED compte comme actif).
        val job = env.db.jobDao().observeAll().first { jobs -> jobs.single().phoneState == "CANCEL_REQUESTED" }.single()
        assertEquals("CANCEL_REQUESTED", job.phoneState)
    }

    @Test
    fun cancelAllWaiting_cancelsEveryWaitingJob_butNotActiveOnes() = runTest {
        env.db.jobDao().upsert(baseJob("active", "Mihon/s/1", "SENT"))
        env.db.jobDao().upsert(baseJob("wait-1", "Mihon/s/2", "SENT"))
        env.db.jobDao().upsert(baseJob("wait-2", "Mihon/s/3", "PREPARING"))
        env.prefs.setHeartbeatJson(heartbeatJson(listOf("active")))

        val vm = QueueViewModel(env.container)
        vm.state.first { it.waitingJobs.size == 2 && it.activeJobs.size == 1 }

        vm.onEvent(QueueEvent.CancelAllWaiting)

        val jobs = env.db.jobDao().observeAll().first { jobs -> jobs.count { it.phoneState == "CANCEL_REQUESTED" } == 2 }
        assertEquals("SENT", jobs.single { it.jobId == "active" }.phoneState)
        // Un job pas encore envoye est clos tout de suite : le PC n'en a jamais entendu parler (R3).
        assertEquals("cancelled", jobs.single { it.jobId == "wait-2" }.pcState)
    }

    @Test
    fun cancelAllUploads_cancelsUploads_butNotReturnsToMihon() = runTest {
        env.db.jobDao().upsert(baseJob("up-1", "Mihon/s/1", "UPLOADING"))
        env.db.jobDao().upsert(baseJob("up-2", "Mihon/s/2", "UPLOADING"))
        env.db.jobDao().upsert(baseJob("down", "Mihon/s/3", "DOWNLOADING"))

        val vm = QueueViewModel(env.container)
        vm.state.first { it.uploadCount == 2 && it.transfers.size == 3 }

        vm.onEvent(QueueEvent.CancelAllUploads)

        val jobs = env.db.jobDao().observeAll().first { jobs -> jobs.count { it.phoneState == "CANCEL_REQUESTED" } == 2 }
        assertEquals("DOWNLOADING", jobs.single { it.jobId == "down" }.phoneState)
    }
}
