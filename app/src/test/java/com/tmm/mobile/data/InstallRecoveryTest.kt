package com.tmm.mobile.data

import androidx.work.WorkInfo
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.workers.WorkerTestEnvironment
import com.tmm.mobile.workers.enqueueInstallJob
import com.tmm.mobile.workers.installWorkName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Relance des installations figees (DOWNLOADING sans travail vivant apres une OutOfMemoryError).
// Le WorkManager de test garde en ENQUEUED les travaux soumis a une contrainte reseau : aucun worker ne tourne.
@RunWith(RobolectricTestRunner::class)
class InstallRecoveryTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun job(jobId: String, phoneState: String, updatedAt: Long, transferredBytes: Long = 0) = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = "MangaSource/Titre/$jobId", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = jobId,
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = transferredBytes, transferTotal = 1000,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = "result-file",
        createdAt = updatedAt, updatedAt = updatedAt, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    private suspend fun liveWork(jobId: String): List<WorkInfo> =
        env.container.workManager.getWorkInfosForUniqueWorkFlow(installWorkName(jobId)).first().filter { !it.state.isFinished }

    private fun recovery() = InstallRecovery(env.db, env.prefs, env.container.workManager, env.clock)

    private val stallMillis = STALL_MINUTES * 60_000L

    @Test fun refresh_orphanDownloading_isRequeuedWithProgressReset() = runTest {
        val old = env.clock.millis - 3 * stallMillis
        env.db.jobDao().upsert(job("orphan", "DOWNLOADING", updatedAt = old, transferredBytes = 500))
        assertTrue(liveWork("orphan").isEmpty())

        env.container.refreshCoordinator.refresh()

        assertEquals(1, liveWork("orphan").size)
        val after = env.db.jobDao().get("orphan")!!
        assertEquals("DOWNLOADING", after.phoneState)
        assertEquals(0L, after.transferredBytes)
        assertEquals(env.clock.millis, after.updatedAt)
    }

    // Travail vivant + progression recente : jamais touche (pas de telechargement en double).
    @Test fun refresh_activeDownloadingWithRecentProgress_isUntouched() = runTest {
        env.db.jobDao().upsert(job("active", "DOWNLOADING", updatedAt = env.clock.millis - 60_000L, transferredBytes = 500))
        env.container.workManager.enqueueInstallJob("active", wifiOnly = false)
        val before = liveWork("active").single().id

        env.container.refreshCoordinator.refresh()

        assertEquals(before, liveWork("active").single().id)
        assertEquals(500L, env.db.jobDao().get("active")!!.transferredBytes)
    }

    // RESULT_READY en attente d'une place ou du reseau : updatedAt vieux par construction, jamais relance.
    // Travail ENQUEUED sans progression depuis STALL_MINUTES (backoff de plusieurs heures) : relance auto.
    @Test fun recover_staleEnqueuedWork_isRelaunched() = runTest {
        env.db.jobDao().upsert(job("waiting", "RESULT_READY", updatedAt = env.clock.millis - 3 * stallMillis))
        env.container.workManager.enqueueInstallJob("waiting", wifiOnly = false)
        val before = liveWork("waiting").single().id

        val report = recovery().recover()

        assertEquals(InstallRecoveryReport(relaunched = 1, alreadyActive = 0), report)
        assertTrue(before != liveWork("waiting").single().id)
    }

    // Sans le bouton, un orphelin recent attend STALL_MINUTES ; le bouton le relance tout de suite.
    @Test fun recover_force_relaunchesRecentOrphan_andCountsActiveJobs() = runTest {
        env.db.jobDao().upsert(job("recent-orphan", "DOWNLOADING", updatedAt = env.clock.millis - 60_000L, transferredBytes = 200))
        env.db.jobDao().upsert(job("active", "DOWNLOADING", updatedAt = env.clock.millis - 10_000L, transferredBytes = 500))
        env.db.jobDao().upsert(job("installed", "INSTALLED", updatedAt = env.clock.millis - 3 * stallMillis))
        env.container.workManager.enqueueInstallJob("active", wifiOnly = false)
        val activeId = liveWork("active").single().id

        assertEquals(InstallRecoveryReport(relaunched = 0, alreadyActive = 1), recovery().recover())
        assertTrue(liveWork("recent-orphan").isEmpty())

        assertEquals(InstallRecoveryReport(relaunched = 1, alreadyActive = 1), recovery().recover(force = true))
        assertEquals(1, liveWork("recent-orphan").size)
        assertEquals(0L, env.db.jobDao().get("recent-orphan")!!.transferredBytes)
        assertEquals(activeId, liveWork("active").single().id)
        assertTrue(liveWork("installed").isEmpty())
        assertEquals("INSTALLED", env.db.jobDao().get("installed")!!.phoneState)
    }

    // Bouton : un travail vivant mais sans progression (backoff, contrainte, worker fige) est relance.
    @Test fun recover_force_relaunchesLiveWorkWithoutRecentProgress() = runTest {
        env.db.jobDao().upsert(job("stuck", "DOWNLOADING", updatedAt = env.clock.millis - 5 * 60_000L, transferredBytes = 300))
        env.db.jobDao().upsert(job("stuck-ready", "RESULT_READY", updatedAt = env.clock.millis - 3 * stallMillis))
        env.container.workManager.enqueueInstallJob("stuck", wifiOnly = false)
        env.container.workManager.enqueueInstallJob("stuck-ready", wifiOnly = false)
        val stuckId = liveWork("stuck").single().id
        val readyId = liveWork("stuck-ready").single().id

        assertEquals(InstallRecoveryReport(relaunched = 2, alreadyActive = 0), recovery().recover(force = true))
        assertTrue(liveWork("stuck").single().id != stuckId)
        assertTrue(liveWork("stuck-ready").single().id != readyId)
        assertEquals(0L, env.db.jobDao().get("stuck")!!.transferredBytes)
    }
}
