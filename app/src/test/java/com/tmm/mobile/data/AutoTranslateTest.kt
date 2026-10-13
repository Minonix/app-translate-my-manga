package com.tmm.mobile.data

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.SeriesSettingsEntity
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PBS R3 / R7 : le suivi automatique ne renvoie jamais un chapitre que j'ai annule ou supprime.
@RunWith(RobolectricTestRunner::class)
class AutoTranslateTest {
    private val env = WorkerTestEnvironment()
    private val path = "MangaSource/Titre/Chapitre 1"

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private suspend fun followSeriesWithDownloadedChapter() {
        env.db.seriesSettingsDao().upsert(SeriesSettingsEntity(seriesKey = "serie", autoTranslate = true))
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = path, sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
                documentUri = "doc://1", isCbz = true, sizeBytes = 1_000L, lastModified = 0L, pageCount = 10,
                seriesKey = "serie", chapterKey = null, number = 1f,
            ),
        )
    }

    private suspend fun jobCount() = env.db.jobDao().observeAll().first().size

    private fun job(phoneState: String, pcState: String? = null) = JobEntity(
        jobId = "job-1", mode = "translate", pages = null, translate = true,
        mihonPath = path, sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = "serie", chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = pcState, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    @Test fun newDownloadedChapter_isSent() = runTest {
        followSeriesWithDownloadedChapter()

        runAutoTranslate(env.db, env.container.jobRepository)

        assertEquals(1, jobCount())
    }

    // Annuler un envoi encore local supprimait le job : le chapitre redevenait « a envoyer ».
    @Test fun cancelledBeforeSending_isNotResent() = runTest {
        followSeriesWithDownloadedChapter()
        env.db.jobDao().upsert(job("PREPARING"))

        env.container.jobRepository.cancel("job-1")
        runAutoTranslate(env.db, env.container.jobRepository)

        assertEquals(listOf("job-1"), env.db.jobDao().observeAll().first().map { it.jobId })
    }

    // Annulation confirmee par le PC sans qu'il garde l'original : le chapitre ne doit pas repartir.
    @Test fun cancelledAndConfirmedByPc_isNotResent() = runTest {
        followSeriesWithDownloadedChapter()
        env.db.jobDao().upsert(job("CANCEL_REQUESTED", pcState = "cancelled"))

        runAutoTranslate(env.db, env.container.jobRepository)

        assertEquals(1, jobCount())
    }

    // Copie FR locale et copie PC supprimees, chapitre Mihon conserve : deja traite, le suivi n'y revient pas.
    @Test fun installedThenDeletedEverywhere_isNotResent() = runTest {
        followSeriesWithDownloadedChapter()
        env.db.jobDao().upsert(job("INSTALLED", pcState = "done"))
        env.db.localFrChapterDao().upsert(
            LocalFrChapterEntity(localPath = "local/fr", folderName = "Titre", fileName = "c1.cbz", documentUri = "doc://fr", lastModified = 0L, jobId = "job-1", mihonPath = path),
        )
        env.db.localFrChapterDao().delete(env.db.localFrChapterDao().observeAll().first().single())

        runAutoTranslate(env.db, env.container.jobRepository)

        assertEquals(1, jobCount())
    }

    // R3 : « je peux le relancer a la main ».
    @Test fun cancelledChapter_canBeSentAgainManually() = runTest {
        followSeriesWithDownloadedChapter()
        env.db.jobDao().upsert(job("PREPARING"))
        env.container.jobRepository.cancel("job-1")

        val target = JobTarget(MihonPath("MangaSource", "Titre", "Chapitre 1"), downloaded = null, series = null, chapter = null)
        val result = env.container.jobRepository.createJobs(JobMode.TRANSLATE, listOf(target))

        assertEquals(1, result.createdJobIds.size)
        assertNotNull(env.db.jobDao().get(result.createdJobIds.single()))
    }
}
