package com.tmm.mobile.transport

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.fake.FakeDrive
import com.tmm.mobile.fake.FakeFile
import com.tmm.mobile.fake.FakePc
import com.tmm.mobile.protocol.ChapterRef
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.RequestOptions
import com.tmm.mobile.protocol.SeriesRef
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.StatusJson
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveTransportTest {
    private lateinit var drive: FakeDrive
    private lateinit var pc: FakePc
    private lateinit var prefs: AppPrefs
    private lateinit var transport: DriveTransport
    private val tokenCalls = AtomicInteger(0)
    private var clockIso = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        drive = FakeDrive { clockIso.toString() }
        drive.start()
        pc = FakePc(drive) { clockIso.toString() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "drive_transport_test_prefs.preferences_pb") },
        )
        prefs = AppPrefs(dataStore)
        val api = DriveApi(
            client = OkHttpClient(),
            token = { forceRefresh -> tokenCalls.incrementAndGet(); if (forceRefresh) "refreshed-token" else "test-token" },
            baseUrl = drive.baseUrl(),
        )
        transport = DriveTransport(api, prefs)
    }

    @After
    fun tearDown() {
        drive.shutdown()
    }

    @Test
    fun ensureLayout_isIdempotent() = runTest {
        val first = transport.ensureLayout()
        assertTrue(first.created)
        val rootId = prefs.driveRootId.first()
        val jobsId = prefs.driveJobsId.first()
        val pcId = prefs.drivePcId.first()
        val commandsId = prefs.driveCommandsId.first()
        assertTrue(rootId != null && jobsId != null && pcId != null && commandsId != null)

        val second = transport.ensureLayout()
        assertFalse(second.created)
        assertEquals(rootId, prefs.driveRootId.first())
        assertEquals(jobsId, prefs.driveJobsId.first())
        assertEquals(pcId, prefs.drivePcId.first())
        assertEquals(commandsId, prefs.driveCommandsId.first())
    }

    @Test
    fun uploadSource_recoversFromInterruption() = runTest {
        transport.ensureLayout()
        val content = ByteArray(9 * 1024 * 1024) { (it % 256).toByte() }
        val file = File.createTempFile("source", ".cbz").apply { writeBytes(content) }
        drive.cutoffUploadAfterBytes = 8L * 1024 * 1024 + 1024

        var sessionSeen: String? = null
        val job = emptyJob("job-1")
        val fileId = transport.uploadSource(
            job = job,
            file = file,
            onSession = { sessionSeen = it },
            onProgress = { _, _ -> },
        )

        assertTrue(sessionSeen != null)
        val uploaded = drive.files.values.first { it.id == fileId }
        assertEquals(content.size, uploaded.bytes.size)
        assertTrue(content.contentEquals(uploaded.bytes))
    }

    @Test
    fun poll_returnsStatusAndDetectsProtocolTooNew() = runTest {
        transport.ensureLayout()
        val jobsId = prefs.driveJobsId.first()!!
        val folderId = testCreateFolder(drive, jobsId, "job-2")
        val request = RequestJson(
            jobId = "job-2",
            createdAt = clockIso.toString(),
            device = "TestDevice",
            series = SeriesRef("Titre", "MangaSource", null, null),
            chapter = ChapterRef("Chapitre 1", 1.0, null, null),
            mihonPath = MihonPath("MangaSource", "Titre", "Chapitre 1"),
            file = FileRef("source.cbz", 100L, "abc"),
            options = RequestOptions(mode = "translate"),
        )
        transport.writeRequest(folderId, request)

        clockIso = clockIso.plusSeconds(10)
        pc.accept("job-2")

        val result = transport.poll(null)
        assertEquals(1, result.statuses.size)
        assertEquals("processing", result.statuses.first().state)
        assertFalse(result.protocolTooNew)
        assertTrue(result.cursor != null)
    }

    @Test
    fun ensureLayout_usesExistingAppFolder() = runTest {
        transport.ensureLayout()
        assertEquals(FakeDrive.APP_ID, prefs.driveAppId.first())
        assertEquals(FakeDrive.APP_ID, drive.files[prefs.driveRootId.first()!!]!!.parents.single())
        assertEquals(1, drive.files.values.count { it.name.equals("App", ignoreCase = true) && "root" in it.parents })
    }

    @Test
    fun ensureLayout_failsWithoutAppFolderAndCreatesNothing() = runTest {
        drive.files.remove(FakeDrive.APP_ID)
        val error = runCatching { transport.ensureLayout() }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertEquals(setOf("root"), drive.files.keys)
        assertNull(prefs.driveAppId.first())
    }

    @Test
    fun ensureLayout_picksExactAppWhenDriveIgnoresCase() = runTest {
        drive.caseInsensitiveNames = true
        val older = com.tmm.mobile.fake.FakeFile(name = "APP", parents = mutableListOf("root"), mimeType = FakeDrive.FOLDER, modifiedTime = "2000-01-01T00:00:00Z")
        drive.files[older.id] = older
        transport.ensureLayout()
        assertEquals(FakeDrive.APP_ID, prefs.driveAppId.first())
    }

    @Test
    fun poll_ignoresSameNamedFilesOutsideMailbox() = runTest {
        transport.ensureLayout()
        // Dossier d'une autre application, hors de App/TranslateMyManga/jobs.
        val foreign = testCreateFolder(drive, "root", "job-x")
        clockIso = clockIso.plusSeconds(10)
        val status = StatusJson(jobId = "job-x", updatedAt = clockIso.toString(), mode = "translate", state = "processing")
        val decoy = com.tmm.mobile.fake.FakeFile(
            name = "status.json", parents = mutableListOf(foreign), mimeType = "application/json",
            modifiedTime = clockIso.toString(), bytes = ProtocolJson.encodeToString(StatusJson.serializer(), status).toByteArray(),
        )
        drive.files[decoy.id] = decoy

        val result = transport.poll(null)
        assertTrue(result.statuses.isEmpty())
    }

    private fun emptyJob(jobId: String) = com.tmm.mobile.data.db.JobEntity(
        jobId = jobId,
        mode = "translate",
        pages = null,
        translate = true,
        mihonPath = "MangaSource/Titre/Chapitre 1",
        sourceDir = "",
        seriesDir = "",
        chapterDir = "",
        seriesKey = null,
        chapterKey = null,
        phoneState = "uploading",
        failedStep = null,
        errorMessage = null,
        driveFolderId = null,
        uploadSessionUrl = null,
        cachePath = null,
        sha256 = null,
        sizeBytes = null,
        transferredBytes = 0L,
        transferTotal = 0L,
        pcState = null,
        pcStage = null,
        pcPage = null,
        pcPages = null,
        pcEngine = null,
        pcQueuePosition = null,
        pcErrorCode = null,
        pcErrorMessage = null,
        pcErrorPage = null,
        statusUpdatedAt = null,
        resultJson = null,
        resultFileId = null,
        createdAt = 0L,
        updatedAt = 0L,
        finishedAt = null,
        installedAt = null,
        installedPath = null,
        receivedWrittenAt = null,
        cancelWrittenAt = null,
        driveDeletedAt = null,
    )

    // La progression doit remonter bloc par bloc : un seul appel en fin de flux faisait passer la barre
    // de reception de 0 a 100 % d'un coup.
    @Test
    fun download_reportsProgressPerChunk() = runTest {
        val bytes = ByteArray(200 * 1024) { (it % 251).toByte() }
        val file = FakeFile(name = "result.cbz", parents = mutableListOf("root"), mimeType = "application/vnd.comicbook+zip", modifiedTime = clockIso.toString(), bytes = bytes)
        drive.files[file.id] = file
        val api = DriveApi(client = OkHttpClient(), token = { "test-token" }, baseUrl = drive.baseUrl())
        val calls = mutableListOf<Long>()
        val out = java.io.ByteArrayOutputStream()

        api.download(file.id, out) { received, _ -> calls += received }

        assertTrue("au moins deux remontees, recu $calls", calls.size >= 2)
        assertEquals(calls.sorted(), calls)
        assertEquals(bytes.size.toLong(), calls.last())
        assertTrue(bytes.contentEquals(out.toByteArray()))
    }
}

private fun testCreateFolder(drive: FakeDrive, jobsId: String, jobId: String): String {
    // ponytail: raccourci de test - cree directement le dossier de job dans le faux Drive plutot
    // que de repasser par DriveApi, puisque seul l'effet (dossier present) importe ici.
    val folder = com.tmm.mobile.fake.FakeFile(name = jobId, parents = mutableListOf(jobsId), mimeType = "application/vnd.google-apps.folder", modifiedTime = Instant.now().toString())
    drive.files[folder.id] = folder
    return folder.id
}
