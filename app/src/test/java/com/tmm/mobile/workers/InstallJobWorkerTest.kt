package com.tmm.mobile.workers

import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.fake.FakeFile
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.4/5.12 : InstallJobWorker. Le chemin nominal (sha/taille valides -> installation
// locale via MihonTree) exige un arbre SAF reel non simulable ici (cf SendJobWorkerTest) : seul le
// chemin sha/taille invalide, qui echoue avant tout acces MihonFs, est testable de bout en bout.
@RunWith(RobolectricTestRunner::class)
class InstallJobWorkerTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun job(jobId: String, phoneState: String, resultFileId: String?, resultJson: String?) = JobEntity(
        jobId = jobId, mode = "verify", pages = null, translate = true,
        mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = resultJson, resultFileId = resultFileId,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    private fun buildWorker(jobId: String, runAttemptCount: Int = 0): InstallJobWorker =
        TestListenableWorkerBuilder<InstallJobWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
            .setRunAttemptCount(runAttemptCount)
            .build()

    @Test fun doWork_wrongPhoneState_isNoop() = runTest {
        env.db.jobDao().upsert(job("job-1", phoneState = "SENT", resultFileId = null, resultJson = null))
        val result = buildWorker("job-1").doWork()
        assertEquals(Result.success(), result)
        assertEquals("SENT", env.db.jobDao().get("job-1")!!.phoneState)
    }

    // Fichier stocke dans le faux Drive avec un contenu different du sha256/size annonces par
    // resultJson.file : la verification post-telechargement doit echouer avant tout acces MihonFs.
    private fun resultJson(jobId: String, size: Long = 999L, sha256: String = "0".repeat(64)) = ProtocolJson.encodeToString(
        ResultJson.serializer(),
        ResultJson(
            jobId = jobId,
            finishedAt = "2026-01-01T00:00:00Z",
            mode = "verify",
            file = FileRef(name = "result.cbz", size = size, sha256 = sha256),
            localFolderName = "Titre",
            chapterFileName = "Chapitre 1.cbz",
            pages = 10,
        ),
    )

    @Test fun doWork_shaMismatch_writesDownloadingThenFailsAfterMaxRetries() = runTest {
        val resultFile = FakeFile(name = "result.cbz", parents = mutableListOf("jobs-root"), mimeType = "application/vnd.comicbook+zip", modifiedTime = "2026-01-01T00:00:00Z", bytes = "contenu-different".toByteArray())
        env.drive.files[resultFile.id] = resultFile
        env.db.jobDao().upsert(job("job-2", phoneState = "RESULT_READY", resultFileId = resultFile.id, resultJson = resultJson("job-2")))

        // PLAN_GEMINI.md 5.4 : RESULT_READY -> DOWNLOADING doit etre ecrit avant l'appel reseau, donc
        // avant meme que la verification sha/taille echoue (Result.retry(), pas encore MAX_RETRY_ATTEMPTS).
        val firstResult = buildWorker("job-2", runAttemptCount = 0).doWork()
        assertEquals(Result.retry(), firstResult)
        val afterFirst = env.db.jobDao().get("job-2")!!
        assertEquals("DOWNLOADING", afterFirst.phoneState)
        assertEquals(resultFile.bytes.size.toLong(), afterFirst.transferredBytes)

        // Deuxieme appel simulant la derniere tentative WorkManager : DOWNLOADING -> FAILED.
        val secondResult = buildWorker("job-2", runAttemptCount = MAX_RETRY_ATTEMPTS).doWork()
        assertEquals(Result.failure(), secondResult)
        val afterSecond = env.db.jobDao().get("job-2")!!
        assertEquals("FAILED", afterSecond.phoneState)
        assertEquals("download", afterSecond.failedStep)
    }

    // Job oublie (remise a plat, suppression) pendant le telechargement : le resultat n'est pas installe
    // et la ligne n'est pas recreee par une ecriture faite a partir du job lu avant le reseau.
    @Test fun doWork_jobDeletedDuringDownload_skipsInstallAndDoesNotResurrectRow() = runTest {
        val bytes = "contenu-valide".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val resultFile = FakeFile(name = "result.cbz", parents = mutableListOf("jobs-root"), mimeType = "application/vnd.comicbook+zip", modifiedTime = "2026-01-01T00:00:00Z", bytes = bytes)
        env.drive.files[resultFile.id] = resultFile
        env.db.jobDao().upsert(job("job-3", phoneState = "RESULT_READY", resultFileId = resultFile.id, resultJson = resultJson("job-3", bytes.size.toLong(), sha)))
        env.drive.beforeMediaGet = { runBlocking { env.db.jobDao().get("job-3")?.let { env.db.jobDao().delete(it) } } }

        val result = buildWorker("job-3").doWork()

        assertEquals(Result.success(), result)
        assertNull(env.db.jobDao().get("job-3"))
        assertEquals(0, env.db.localFrChapterDao().observeAll().first().size)
    }
}
