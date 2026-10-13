package com.tmm.mobile.ui.screens.queue

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Boutons « Vider » des onglets Terminées et Échecs : aucun job supprime, pour que le suivi
// automatique ne renvoie pas ces chapitres (R3, R7).
@RunWith(RobolectricTestRunner::class)
class QueueClearTest {
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

    @Test
    fun clearDone_masqueLesInstalles_sansSupprimer() = runTest {
        env.db.jobDao().upsert(baseJob("installed", "Mihon/frieren/1", "INSTALLED"))
        env.db.jobDao().upsert(baseJob("ready", "Mihon/frieren/2", "RESULT_READY"))
        env.db.jobDao().upsert(baseJob("sent", "Mihon/frieren/3", "SENT"))

        val vm = QueueViewModel(env.container)
        vm.state.first { it.doneJobs.size == 2 }

        vm.onEvent(QueueEvent.ClearDone)

        // L'installe disparait de l'onglet, le RESULT_READY reste ; aucun job n'est supprime (R7).
        val state = vm.state.first { it.doneJobs.size == 1 }
        assertEquals(listOf("Mihon/frieren/2"), state.doneJobs.map { it.mihonPath })
        assertEquals(3, env.db.jobDao().observeAll().first().size)

        // Un chapitre termine apres le vidage reapparait.
        env.clock.millis += 1_000
        env.db.jobDao().upsert(baseJob("later", "Mihon/frieren/4", "INSTALLED"))
        vm.state.first { s -> s.doneJobs.any { it.mihonPath == "Mihon/frieren/4" } }
    }

    @Test
    fun clearFailed_closLesEchecs_sansRenvoiParLeSuivi() = runTest {
        // Chapitres telecharges : les echecs sont affiches dans l'onglet, donc concernes par « Vider ».
        env.db.downloadedChapterDao().upsert(downloaded("Mihon/frieren/10"))
        env.db.downloadedChapterDao().upsert(downloaded("Mihon/frieren/11"))
        env.db.jobDao().upsert(baseJob("failed", "Mihon/frieren/10", "FAILED").copy(pcErrorMessage = "moteur indisponible"))
        env.db.jobDao().upsert(
            baseJob("stalled", "Mihon/frieren/11", "SENT").copy(errorCode = LocalErrorCode.PC_STALLED),
        )
        val vm = QueueViewModel(env.container)
        vm.state.first { it.failedJobs.size == 2 }

        vm.onEvent(QueueEvent.ClearFailed)

        // Les deux restent en base, clos comme annules : le chapitre garde un job et le suivi
        // automatique ne le renvoie pas (R3). L'onglet Echecs est vide.
        val jobs = env.db.jobDao().observeAll().first { list ->
            list.size == 2 && list.all { it.phoneState == "CANCEL_REQUESTED" }
        }
        assertEquals(setOf("failed", "stalled"), jobs.map { it.jobId }.toSet())
        vm.state.first { it.failedJobs.isEmpty() }
        assertEquals(emptyList<Any>(), com.tmm.mobile.data.autoTranslateTargets(env.db, setOf("frieren")))
    }
}
