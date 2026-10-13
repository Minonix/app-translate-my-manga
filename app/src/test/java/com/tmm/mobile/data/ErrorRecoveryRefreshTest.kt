package com.tmm.mobile.data

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.protocol.ChapterRef
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.RequestOptions
import com.tmm.mobile.protocol.SeriesRef
import com.tmm.mobile.protocol.StatusJson
import com.tmm.mobile.workers.WorkerTestEnvironment
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

// docs/ERREURS_PROTOCOLE.md R8/R11/R14 et heartbeat driveOk (§2), via refresh() sur FakeDrive/FakePc.
@RunWith(RobolectricTestRunner::class)
class ErrorRecoveryRefreshTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private suspend fun writeRequest(jobId: String): String {
        val folderId = env.container.transport.ensureJobFolder(jobId)
        env.container.transport.writeRequest(
            folderId,
            RequestJson(
                jobId = jobId, createdAt = "2026-01-01T00:00:00Z", device = "Test",
                series = SeriesRef("Titre", "MangaSource", null, null),
                chapter = ChapterRef("Chapitre 1", 1.0, null, null),
                mihonPath = MihonPath("MangaSource", "Titre", "Chapitre 1"),
                file = null, options = RequestOptions(mode = "translate"),
            ),
        )
        return folderId
    }

    private suspend fun seedDownloaded() = env.db.downloadedChapterDao().upsert(
        DownloadedChapterEntity(
            mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
            documentUri = "doc://1", isCbz = true, sizeBytes = 1_000L, lastModified = 0L, pageCount = 10,
            seriesKey = null, chapterKey = null, number = 1f,
        ),
    )

    private fun job(jobId: String, phoneState: String, folderId: String?, updatedAt: Long = env.clock.millis) = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = folderId, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = updatedAt, updatedAt = updatedAt, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    // R8 : PC en ligne, job envoye sans status.json depuis 15 min -> pc_stalled ; « Relancer » supprime
    // le dossier puis recree le job (nouveau jobId).
    @Test fun r8_sentJobWithoutStatus_isStalled_andRetryRecreates() = runTest {
        seedDownloaded()
        val folderId = writeRequest("job-8")
        env.db.jobDao().upsert(job("job-8", "SENT", folderId, updatedAt = env.clock.millis - PC_STALLED_NO_STATUS_MILLIS - 1))
        env.pc.writeHeartbeat()

        env.container.refreshCoordinator.refresh()

        assertEquals(LocalErrorCode.PC_STALLED, env.db.jobDao().get("job-8")!!.errorCode)

        env.container.jobRepository.retry("job-8")

        assertNull(env.db.jobDao().get("job-8"))
        assertFalse(env.drive.files.containsKey(folderId))
        val recreated = env.db.jobDao().observeAll().first().single()
        assertEquals("PREPARING", recreated.phoneState)
    }

    // R8 : si la suppression du dossier echoue, rien n'est recree (le PC pourrait encore traiter l'ancien).
    @Test fun r8_retryStalled_keepsJob_whenFolderDeleteFails() = runTest {
        seedDownloaded()
        val folderId = writeRequest("job-8b")
        env.db.jobDao().upsert(job("job-8b", "SENT", folderId).copy(errorCode = LocalErrorCode.PC_STALLED))
        env.drive.deleteFailsWith = 500

        env.container.jobRepository.retry("job-8b")

        assertNotNull(env.db.jobDao().get("job-8b"))
        assertEquals(1, env.db.jobDao().observeAll().first().size)
    }

    // R8 : PC hors ligne (aucun heartbeat) -> pas de faux diagnostic.
    @Test fun r8_pcOffline_noStalledDiagnosis() = runTest {
        val folderId = writeRequest("job-8c")
        env.db.jobDao().upsert(job("job-8c", "SENT", folderId, updatedAt = env.clock.millis - PC_STALLED_NO_STATUS_MILLIS - 1))

        env.container.refreshCoordinator.refresh()

        assertNull(env.db.jobDao().get("job-8c")!!.errorCode)
    }

    // R11 : resultat arrive apres la demande d'annulation -> clos localement (annule), non installe, et
    // le chapitre n'est plus bloque par un job actif.
    @Test fun r11_resultAfterCancel_closesLocally() = runTest {
        seedDownloaded()
        val folderId = writeRequest("job-11")
        env.db.jobDao().upsert(job("job-11", "CANCEL_REQUESTED", folderId))
        env.pc.finish("job-11", "cbz".toByteArray(), fileRef = FileRef("result.cbz", 3, "0".repeat(64)))

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-11")!!
        assertEquals("CANCEL_REQUESTED", updated.phoneState)
        assertEquals("cancelled", updated.pcState)
        assertNull(updated.resultJson)
        val again = env.container.jobRepository.createJobs(JobMode.TRANSLATE, listOf(JobTarget(MihonPath("MangaSource", "Titre", "Chapitre 1"), null, null, null)))
        assertEquals(1, again.createdJobIds.size)
    }

    // Un job « fetching » (recuperation de la source) n'est jamais dans heartbeat.activeJobIds, qui ne liste que
    // les traductions : il ne doit pas etre declare « bloque sur le PC » apres 10 min.
    @Test fun r8_fetchingJob_isNotDiagnosedStalled() = runTest {
        val folderId = writeRequest("job-8d")
        env.db.jobDao().upsert(job("job-8d", "SENT", folderId))
        env.pc.writeStatus("job-8d", StatusJson(jobId = "job-8d", updatedAt = "2026-01-01T00:00:00Z", mode = "translate", state = "fetching"))
        env.pc.writeHeartbeat()
        // Coordinateur dedie dont le delai entre deux refresh tourne en temps virtuel (celui du conteneur
        // attend 30 s reelles, ce qui rendrait le second refresh sans effet).
        val coordinator = RefreshCoordinator(
            env.db, env.prefs, env.router, WorkManager.getInstance(env.context), env.clock,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)), autoTranslate = {},
        )
        coordinator.refresh()
        advanceUntilIdle()

        env.clock.millis += PC_STALLED_INACTIVE_MILLIS + 60_000
        env.pc.writeHeartbeat()
        coordinator.refresh()

        val updated = env.db.jobDao().get("job-8d")!!
        assertEquals("fetching", updated.pcState)
        assertNull(updated.errorCode)
    }

    // Le resultat est arrive : le diagnostic « bloque sur le PC » pose avant ne doit pas rester sur le job.
    @Test fun r8_stalledFlag_isClearedWhenResultArrives() = runTest {
        val folderId = writeRequest("job-8e")
        env.db.jobDao().upsert(job("job-8e", "SENT", folderId).copy(errorCode = LocalErrorCode.PC_STALLED))
        env.pc.finish("job-8e", ByteArray(0), fileRef = null)

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-8e")!!
        assertEquals("INSTALLED", updated.phoneState)
        assertNull(updated.errorCode)
    }

    // Annulation jamais acquittee par le PC : apres le delai, le job n'est plus « en file » indefiniment
    // et le chapitre peut etre renvoye.
    @Test fun cancelWithoutPcAnswer_isClosedLocallyAfterDelay() = runTest {
        seedDownloaded()
        val folderId = writeRequest("job-c1")
        env.db.jobDao().upsert(
            job("job-c1", "CANCEL_REQUESTED", folderId).copy(cancelWrittenAt = env.clock.millis - CANCEL_UNANSWERED_MILLIS - 1),
        )
        env.pc.writeHeartbeat()

        env.container.refreshCoordinator.refresh()

        assertEquals("cancelled", env.db.jobDao().get("job-c1")!!.pcState)
        val again = env.container.jobRepository.createJobs(JobMode.TRANSLATE, listOf(JobTarget(MihonPath("MangaSource", "Titre", "Chapitre 1"), null, null, null)))
        assertEquals(1, again.createdJobIds.size)
    }

    @Test fun cancelWithoutPcAnswer_waitsBeforeDelay_andWhilePcTranslatesIt() = runTest {
        val folderId = writeRequest("job-c2")
        env.db.jobDao().upsert(job("job-c2", "CANCEL_REQUESTED", folderId).copy(cancelWrittenAt = env.clock.millis - 60_000))
        val folderId3 = writeRequest("job-c3")
        env.db.jobDao().upsert(
            job("job-c3", "CANCEL_REQUESTED", folderId3).copy(cancelWrittenAt = env.clock.millis - CANCEL_UNANSWERED_MILLIS - 1),
        )
        env.pc.writeHeartbeat(active = listOf("job-c3"))

        env.container.refreshCoordinator.refresh()

        assertNull(env.db.jobDao().get("job-c2")!!.pcState)
        assertNull(env.db.jobDao().get("job-c3")!!.pcState)
    }

    // §2 : heartbeat driveOk = false -> bandeau neutre « le PC n'accede plus a Google Drive », sans
    // drapeau Drive plein ni auth du telephone ; driveOk = true le fait tomber.
    @Test fun heartbeat_driveNotOk_raisesNeutralPcBanner_only() = runTest {
        env.pc.writeHeartbeat(driveOk = false, lastErrorCode = "drive_quota")

        env.container.refreshCoordinator.refresh()

        assertTrue(env.prefs.pcDriveDown.first())
        assertFalse(env.prefs.driveFull.first())
        assertFalse(env.prefs.authExpired.first())
    }

    // Retour v1.7.0 : le PC qui demarre publie driveOk avant son premier cycle Drive. Un avis absent
    // est inconnu, pas une panne, et efface le bandeau laisse par un ancien heartbeat.
    @Test fun heartbeat_driveOkUnknown_clearsPcBanner() = runTest {
        val coordinator = virtualTimeCoordinator()
        env.pc.writeHeartbeat(driveOk = false)
        coordinator.refresh()
        advanceUntilIdle()
        assertTrue(env.prefs.pcDriveDown.first())

        env.clock.millis += 10_000
        env.pc.writeHeartbeat(driveOk = null)
        coordinator.refresh()
        advanceUntilIdle()

        assertFalse(env.prefs.pcDriveDown.first())
    }

    // Retour v1.7.0 : PC eteint avec un dernier heartbeat driveOk = false -> pas de faux bandeau Drive.
    @Test fun pcOffline_dropsStalePcDriveBanner() = runTest {
        val coordinator = virtualTimeCoordinator()
        env.pc.writeHeartbeat(driveOk = false)
        coordinator.refresh()
        advanceUntilIdle()
        assertTrue(env.prefs.pcDriveDown.first())

        env.clock.millis += PC_ONLINE_MILLIS + 1
        coordinator.refresh()
        advanceUntilIdle()

        assertFalse(env.prefs.pcDriveDown.first())
    }

    // Delai entre deux refresh en temps virtuel (celui du conteneur attend 30 s reelles).
    private fun kotlinx.coroutines.test.TestScope.virtualTimeCoordinator() = RefreshCoordinator(
        env.db, env.prefs, env.router, WorkManager.getInstance(env.context), env.clock,
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)), autoTranslate = {},
    )
}
