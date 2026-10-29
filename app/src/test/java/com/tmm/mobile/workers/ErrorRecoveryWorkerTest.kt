package com.tmm.mobile.workers

import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.tmm.mobile.TmmApp
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.status.ErrorAction
import com.tmm.mobile.data.status.ErrorCatalog
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.fake.FakeFile
import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.DriveApi
import com.tmm.mobile.transport.DriveTransport
import com.tmm.mobile.transport.TransportRouter
import com.tmm.mobile.transport.DirectTokenStore
import java.io.File
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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

// docs/ERREURS_PROTOCOLE.md : bugs A-D et R2/R10/R13 cote workers, sur FakeDrive + FakeMihonFs.
@RunWith(RobolectricTestRunner::class)
class ErrorRecoveryWorkerTest {
    private val env = WorkerTestEnvironment()
    private lateinit var mihonRoot: File

    @Before
    fun setUp() {
        env.setUp()
        mihonRoot = kotlin.io.path.createTempDirectory("mihon").toFile()
    }

    @After
    fun tearDown() {
        env.tearDown()
        mihonRoot.deleteRecursively()
    }

    private fun job(
        jobId: String,
        phoneState: String,
        mode: String = JobMode.VERIFY,
        redeliver: Boolean = false,
        resultJson: String? = null,
        resultFileId: String? = null,
    ) = JobEntity(
        jobId = jobId, mode = mode, pages = null, translate = true,
        mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = resultJson, resultFileId = resultFileId,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null, redeliver = redeliver,
    )

    private fun sendWorker(jobId: String, runAttemptCount: Int = 0): SendJobWorker =
        TestListenableWorkerBuilder<SendJobWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
            .setRunAttemptCount(runAttemptCount)
            .build()

    private fun installWorker(jobId: String, runAttemptCount: Int = 0): InstallJobWorker =
        TestListenableWorkerBuilder<InstallJobWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
            .setRunAttemptCount(runAttemptCount)
            .build()

    // Arbre Mihon factice : root/local/ (destination) et un chapitre source en CBZ.
    private suspend fun seedMihon(): File {
        File(mihonRoot, "local").mkdirs()
        val cbz = File(mihonRoot, "source.cbz").apply { writeBytes("cbz-source".toByteArray()) }
        env.container.mihonFs = FakeMihonFs(mihonRoot)
        env.container.mihonRootDocId = mihonRoot.absolutePath
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = "MangaSource/Titre/Chapitre 1", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
                documentUri = cbz.absolutePath, isCbz = true, sizeBytes = cbz.length(), lastModified = 0L, pageCount = 1,
                seriesKey = null, chapterKey = null, number = 1f,
            ),
        )
        return cbz
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun resultJson(jobId: String, file: FileRef?) = ProtocolJson.encodeToString(
        ResultJson.serializer(),
        ResultJson(jobId = jobId, finishedAt = "2026-01-01T00:00:00Z", mode = "translate", file = file,
            localFolderName = "Titre", chapterFileName = "Chapitre 1.cbz", pages = 1),
    )

    // Bug A / R2 : en mode TRANSLATE, un seul dossier jobs/<jobId>, qui contient source.cbz ET request.json.
    // Avant le correctif, uploadSource creait un second dossier (createFolder aveugle) et l'id enregistre
    // etait ecrase par la copie du job d'avant ensureJobFolder.
    @Test fun bugA_translate_singleJobFolder_withSourceAndRequest() = runTest {
        seedMihon()
        env.db.jobDao().upsert(job("job-a", phoneState = "PREPARING", mode = JobMode.TRANSLATE))

        assertEquals(Result.success(), sendWorker("job-a").doWork())

        assertEquals(1, env.drive.createdFolderNames.count { it == "job-a" })
        val jobsId = requireNotNull(env.prefs.driveJobsId.first())
        val folders = env.drive.files.values.filter { it.name == "job-a" && jobsId in it.parents }
        assertEquals(1, folders.size)
        assertNotNull(env.drive.childOf(folders.single().id, "source.cbz"))
        assertNotNull(env.drive.childOf(folders.single().id, "request.json"))
        val updated = env.db.jobDao().get("job-a")!!
        assertEquals("SENT", updated.phoneState)
        assertEquals(folders.single().id, updated.driveFolderId)
        assertNotNull(env.pc.readRequest("job-a").file)
    }

    // §2 redeliver : request.json seul (file = null, options.redeliver = true), aucun fichier envoye.
    @Test fun redeliver_writesRequestWithoutFile() = runTest {
        env.db.jobDao().upsert(job("job-r", phoneState = "PREPARING", mode = JobMode.TRANSLATE, redeliver = true))

        assertEquals(Result.success(), sendWorker("job-r").doWork())

        val request = env.pc.readRequest("job-r")
        assertNull(request.file)
        assertEquals(true, request.options.redeliver)
        val folder = env.drive.files.values.single { it.name == "job-r" }
        assertNull(env.drive.childOf(folder.id, "source.cbz"))
    }

    // Requete normale : le champ optionnel redeliver est omis du JSON (PC v1 inchange).
    @Test fun normalRequest_omitsRedeliverKey() = runTest {
        env.db.jobDao().upsert(job("job-n", phoneState = "PREPARING"))
        sendWorker("job-n").doWork()
        val folder = env.drive.files.values.single { it.name == "job-n" }
        val raw = String(env.drive.childOf(folder.id, "request.json")!!.bytes)
        assertFalse(raw.contains("redeliver"))
    }

    // Bug C : Drive plein -> FAILED drive_quota sans reessai, bandeau « Google Drive plein ».
    @Test fun bugC_storageQuota_failsWithDriveQuota_andRaisesBanner() = runTest {
        env.db.jobDao().upsert(job("job-c", phoneState = "PREPARING"))
        env.drive.force403Reason = "storageQuotaExceeded"
        env.drive.force403ForNextAttempts = 100

        assertEquals(Result.failure(), sendWorker("job-c").doWork())

        val updated = env.db.jobDao().get("job-c")!!
        assertEquals("FAILED", updated.phoneState)
        assertEquals(LocalErrorCode.DRIVE_QUOTA, updated.errorCode)
        assertTrue(env.prefs.driveFull.first())
    }

    // Bug C/D, R13 : 403 d'autorisation -> retry + drapeau auth, jamais FAILED, meme a la derniere tentative.
    @Test fun bugD_forbiddenAuth_retriesAndFlags_neverFailed() = runTest {
        env.db.jobDao().upsert(job("job-d", phoneState = "PREPARING"))
        env.drive.force403Reason = "insufficientPermissions"
        env.drive.force403ForNextAttempts = 100

        assertEquals(Result.retry(), sendWorker("job-d", runAttemptCount = MAX_RETRY_ATTEMPTS).doWork())

        assertEquals("PREPARING", env.db.jobDao().get("job-d")!!.phoneState)
        assertTrue(env.prefs.authExpired.first())
    }

    // Bug D / R13 : exception levee par le fournisseur de jeton (Play Services hors ligne, convertie en
    // AuthExpiredException par DefaultAppContainer.provideToken) -> retry + drapeau, jamais FAILED.
    @Test fun bugD_tokenProviderFailure_retriesAndFlags() = runTest {
        val api = DriveApi(OkHttpClient(), token = { _ -> throw AuthExpiredException("Play Services indisponible") }, baseUrl = env.drive.baseUrl())
        val container = FakeAppContainer(env.context, env.db, env.prefs, TransportRouter(DriveTransport(api, env.prefs), env.direct, env.pcDirect), env.clock)
        (env.context as TmmApp).container = container
        env.db.jobDao().upsert(job("job-d2", phoneState = "PREPARING"))

        assertEquals(Result.retry(), sendWorker("job-d2", runAttemptCount = MAX_RETRY_ATTEMPTS).doWork())

        assertEquals("PREPARING", env.db.jobDao().get("job-d2")!!.phoneState)
        assertTrue(env.prefs.authExpired.first())
    }

    // Bug B / R10 : result.json avec file = null -> job clos (INSTALLED) sans installation ni exception.
    @Test fun bugB_resultWithoutFile_closesJobWithoutInstall() = runTest {
        env.db.jobDao().upsert(job("job-b", phoneState = "RESULT_READY", resultJson = resultJson("job-b", null)))

        assertEquals(Result.success(), installWorker("job-b").doWork())

        val updated = env.db.jobDao().get("job-b")!!
        assertEquals("INSTALLED", updated.phoneState)
        assertNull(updated.installedPath)
        assertTrue(env.db.localFrChapterDao().observeAll().first().isEmpty())
    }

    // R10 : un echec d'installation garde le CBZ en cache ; « Reessayer » reprend a l'installation
    // (RESULT_READY) et le nouvel essai ne retelecharge pas.
    @Test fun r10_installRetry_resumesWithoutRedownload() = runTest {
        val bytes = "cbz-traduit".toByteArray()
        val remote = FakeFile(name = "result.cbz", parents = mutableListOf("jobs-root"), mimeType = "application/vnd.comicbook+zip", modifiedTime = "2026-01-01T00:00:00Z", bytes = bytes)
        env.drive.files[remote.id] = remote
        val downloads = AtomicInteger(0)
        env.drive.beforeMediaGet = { if (it == remote.id) downloads.incrementAndGet() }
        val fileRef = FileRef(name = "result.cbz", size = bytes.size.toLong(), sha256 = sha256(bytes))
        env.db.jobDao().upsert(job("job-10", phoneState = "RESULT_READY", resultJson = resultJson("job-10", fileRef), resultFileId = remote.id))

        // Pas d'arbre Mihon : echec a l'etape install (permission), apres le telechargement.
        assertEquals(Result.failure(), installWorker("job-10").doWork())
        val failed = env.db.jobDao().get("job-10")!!
        assertEquals("FAILED", failed.phoneState)
        assertEquals("install", failed.failedStep)
        assertEquals(LocalErrorCode.PERMISSION, failed.errorCode)
        assertEquals(1, downloads.get())

        env.container.jobRepository.retry("job-10")
        val resumed = env.db.jobDao().get("job-10")!!
        assertEquals("RESULT_READY", resumed.phoneState)
        assertNull(resumed.errorCode)
        assertEquals(1, env.db.jobDao().observeAll().first().size)

        seedMihon()
        assertEquals(Result.success(), installWorker("job-10").doWork())
        val installed = env.db.jobDao().get("job-10")!!
        assertEquals("INSTALLED", installed.phoneState)
        assertEquals("Titre/Chapitre 1.cbz", installed.installedPath)
        assertEquals(1, downloads.get())
        assertTrue(File(mihonRoot, "local/Titre/Chapitre 1.cbz").readBytes().contentEquals(bytes))
    }

    // result_missing : result.cbz absent (404) -> FAILED result_missing, et « Redemander » cree un job
    // redeliver (request.json sans fichier) au lieu de renvoyer le chapitre.
    @Test fun resultMissing_retryCreatesRedeliverJob() = runTest {
        val fileRef = FileRef(name = "result.cbz", size = 3, sha256 = "0".repeat(64))
        env.db.jobDao().upsert(job("job-m", phoneState = "RESULT_READY", mode = JobMode.TRANSLATE, resultJson = resultJson("job-m", fileRef), resultFileId = "absent"))

        assertEquals(Result.failure(), installWorker("job-m").doWork())
        assertEquals(LocalErrorCode.RESULT_MISSING, env.db.jobDao().get("job-m")!!.errorCode)

        env.container.jobRepository.retry("job-m")

        val created = env.db.jobDao().observeAll().first().single { it.jobId != "job-m" }
        assertTrue(created.redeliver)
        assertEquals(JobMode.TRANSLATE, created.mode)
        assertEquals("PREPARING", created.phoneState)
    }

    // Canal direct : le PC refuse le job en HTTP 200 (status failed + error.code) -> meme echec et meme
    // libelle que le status.json "failed" du canal Drive, sans reessai.
    @Test fun direct_jobRefusedWithHttp200_failsWithPcErrorCode() = runTest {
        val pc = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.url.encodedPath.endsWith("/health") -> MockResponse(code = 200, body = """{"pcName":"PC","appVersion":"1"}""")
                    request.method == "POST" -> MockResponse(
                        code = 200,
                        body = """{"protocol":1,"jobId":"job-x","updatedAt":"2026-01-01T00:00:00Z","mode":"verify","state":"failed","error":{"code":"integrity","message":"sha256"}}""",
                    )
                    else -> MockResponse(code = 200, body = "{}")
                }
            }
            start()
        }
        try {
            env.prefs.setDirectUrl(pc.url("/").toString())
            DirectTokenStore(env.prefs, FakeTokenCipher).set("jeton")
            env.db.jobDao().upsert(job("job-x", phoneState = "PREPARING"))

            assertEquals(Result.failure(), sendWorker("job-x").doWork())

            val updated = env.db.jobDao().get("job-x")!!
            assertEquals("FAILED", updated.phoneState)
            assertEquals("integrity", updated.pcErrorCode)
            assertNull(updated.errorCode)
        } finally {
            pc.close()
        }
    }

    // PC direct joignable (health 200) qui repond `postCode` a tout POST.
    private fun directPc(postCode: Int, body: String): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/health") -> MockResponse(code = 200, body = """{"pcName":"PC","appVersion":"1"}""")
                request.method == "POST" -> MockResponse(code = postCode, body = body)
                else -> MockResponse(code = 200, body = "{}")
            }
        }
        start()
    }

    private suspend fun pairWith(pc: MockWebServer) {
        env.prefs.setDirectUrl(pc.url("/").toString())
        DirectTokenStore(env.prefs, FakeTokenCipher).set("jeton")
    }

    // 409 other_channel : le PC a deja le job par Drive -> SENT sur le canal Drive, suivi par le poll,
    // ni echec ni reessai (un « Relancer » creerait un doublon).
    @Test fun direct_otherChannel409_followsJobOnDrive_withoutFailure() = runTest {
        val pc = directPc(409, """{"error":{"code":"other_channel","message":"deja recu par Drive"}}""")
        try {
            pairWith(pc)
            env.db.jobDao().upsert(job("job-409", phoneState = "PREPARING"))

            assertEquals(Result.success(), sendWorker("job-409").doWork())

            val updated = env.db.jobDao().get("job-409")!!
            assertEquals("SENT", updated.phoneState)
            assertEquals(CHANNEL_DRIVE, updated.channel)
            assertNull(updated.driveFolderId)
            assertNull(updated.errorCode)
            assertNull(updated.pcErrorCode)
        } finally {
            pc.close()
        }
    }

    // 404 not_found (PC sans route directe) avec Drive configure : repli immediat sur Drive, request.json
    // ecrit dans jobs/<jobId>, un seul POST direct tente.
    @Test fun direct_notFound404_fallsBackToDrive() = runTest {
        val pc = directPc(404, """{"error":{"code":"not_found","message":"route inconnue"}}""")
        try {
            pairWith(pc)
            env.prefs.setGoogleEmail("test@example.com")
            env.db.jobDao().upsert(job("job-404", phoneState = "PREPARING"))

            assertEquals(Result.success(), sendWorker("job-404").doWork())

            val updated = env.db.jobDao().get("job-404")!!
            assertEquals("SENT", updated.phoneState)
            assertEquals(CHANNEL_DRIVE, updated.channel)
            val folder = env.drive.files.values.single { it.name == "job-404" }
            assertNotNull(env.drive.childOf(folder.id, "request.json"))
            assertEquals(updated.driveFolderId, folder.id)
        } finally {
            pc.close()
        }
    }

    // 405 sans Drive configure : FAILED tout de suite avec « version du PC trop ancienne » (update_pc).
    @Test fun direct_notFound405_withoutDrive_failsUpdatePc() = runTest {
        val pc = directPc(405, "")
        try {
            pairWith(pc)
            env.db.jobDao().upsert(job("job-405", phoneState = "PREPARING"))

            assertEquals(Result.failure(), sendWorker("job-405").doWork())

            val updated = env.db.jobDao().get("job-405")!!
            assertEquals("FAILED", updated.phoneState)
            assertEquals("protocol", updated.pcErrorCode)
            assertEquals(ErrorAction.UPDATE_PC, ErrorCatalog.forJob(updated).action)
        } finally {
            pc.close()
        }
    }
}
