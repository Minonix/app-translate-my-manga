package com.tmm.mobile.transport

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.fake.FakeDrive
import com.tmm.mobile.fake.FakePc
import com.tmm.mobile.protocol.ChapterRef
import com.tmm.mobile.protocol.JobError
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.RequestOptions
import com.tmm.mobile.protocol.ResultJson
import com.tmm.mobile.protocol.SeriesRef
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// docs/ERREURS_PROTOCOLE.md R3/R4/R5 (bug C : 403 non traite) cote DriveApi/DriveTransport, sur FakeDrive.
@RunWith(RobolectricTestRunner::class)
class DriveErrorHandlingTest {
    private lateinit var drive: FakeDrive
    private lateinit var pc: FakePc
    private lateinit var transport: DriveTransport
    private lateinit var api: DriveApi
    private val rejectedTokens = AtomicInteger(0)
    private val clockIso = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        drive = FakeDrive { clockIso.toString() }
        drive.start()
        pc = FakePc(drive) { clockIso.toString() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.filesDir, "drive_error_test_prefs.preferences_pb").apply { delete() }
        val prefs = AppPrefs(PreferenceDataStoreFactory.create(produceFile = { file }))
        api = DriveApi(
            client = OkHttpClient(),
            token = { _ -> "test-token" },
            baseUrl = drive.baseUrl(),
            onTokenRejected = { rejectedTokens.incrementAndGet() },
        )
        transport = DriveTransport(api, prefs)
    }

    @After
    fun tearDown() {
        drive.shutdown()
    }

    private suspend fun newJob(jobId: String): String {
        transport.ensureLayout()
        val folderId = transport.ensureJobFolder(jobId)
        transport.writeRequest(
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

    private fun resultJson(jobId: String, chapterFileName: String) = ProtocolJson.encodeToString(
        ResultJson.serializer(),
        ResultJson(jobId = jobId, finishedAt = "2026-01-01T00:00:00Z", mode = "translate", file = null,
            localFolderName = "Titre", chapterFileName = chapterFileName, pages = 1),
    )

    // Bug C / R5 : 403 rateLimitExceeded se traite comme un 429 (backoff puis reussite), pas comme un succes.
    @Test fun forbidden_rateLimit_isRetriedWithBackoff() = runTest {
        drive.force403Reason = "rateLimitExceeded"
        drive.force403ForNextAttempts = 2

        transport.ensureLayout()

        assertEquals(0, drive.force403ForNextAttempts)
        assertEquals(0, rejectedTokens.get())
    }

    // Bug C / R5 : 403 storageQuotaExceeded -> DriveQuotaException (code drive_quota), sans invalider le jeton.
    @Test fun forbidden_storageQuota_throwsDriveQuota() = runTest {
        drive.force403Reason = "storageQuotaExceeded"
        drive.force403ForNextAttempts = 1
        try {
            transport.ensureLayout()
            fail("403 storageQuotaExceeded accepte")
        } catch (e: DriveQuotaException) {
            // attendu
        }
        assertEquals(0, rejectedTokens.get())
    }

    // Bug C/D, R5/R13 : autre 403 -> auth, et le jeton refuse est invalide (clearToken en production).
    @Test fun forbidden_otherReason_isAuth_andRejectsToken() = runTest {
        drive.force403Reason = "insufficientPermissions"
        drive.force403ForNextAttempts = 1
        try {
            transport.ensureLayout()
            fail("403 d'autorisation accepte")
        } catch (e: AuthExpiredException) {
            // attendu
        }
        assertEquals(1, rejectedTokens.get())
    }

    // R5 : 404 traite par l'appelant ; le corps d'erreur n'est jamais ecrit dans le fichier de destination.
    @Test fun notFound_download_throwsAndWritesNothing() = runTest {
        val sink = ByteArrayOutputStream()
        try {
            api.download("absent", sink) { _, _ -> }
            fail("404 telecharge comme un succes")
        } catch (e: DriveNotFoundException) {
            // attendu
        }
        assertEquals(0, sink.size())
    }

    // R5 : supprimer un dossier deja absent (404) atteint l'objectif, sans exception.
    @Test fun notFound_delete_isSuccess() = runTest {
        api.delete("absent")
    }

    // R3 : doublons de nom -> status le plus recent, result le plus ancien.
    @Test fun poll_duplicates_newestStatus_oldestResult_R3() = runTest {
        newJob("job-r3")
        pc.writeDuplicateJobFile("job-r3", "status.json", pc.statusJson("job-r3", "processing"), "2026-01-01T00:01:00Z")
        pc.writeDuplicateJobFile("job-r3", "status.json", pc.statusJson("job-r3", "failed", error = JobError("job_lost", "perdu")), "2026-01-01T00:02:00Z")
        pc.writeDuplicateJobFile("job-r3", "result.json", resultJson("job-r3", "A.cbz"), "2026-01-01T00:03:00Z")
        pc.writeDuplicateJobFile("job-r3", "result.json", resultJson("job-r3", "B.cbz"), "2026-01-01T00:04:00Z")

        val result = transport.poll(null)

        assertEquals(listOf("failed"), result.statuses.map { it.state })
        assertEquals(listOf("A.cbz"), result.results.map { it.first.chapterFileName })
    }

    // R3/R4 : un status.json vide ne leve rien et le curseur s'arrete juste avant lui ; une fois
    // reecrit, il est lu au cycle suivant.
    @Test fun poll_unreadableEntry_holdsCursor_R4() = runTest {
        newJob("job-ok")
        newJob("job-vide")
        val empty = pc.writeDuplicateJobFile("job-vide", "status.json", "", "2026-01-01T00:02:30Z")
        pc.writeDuplicateJobFile("job-ok", "status.json", pc.statusJson("job-ok", "processing"), "2026-01-01T00:05:00Z")

        val first = transport.poll(null)

        assertEquals(listOf("job-ok"), first.statuses.map { it.jobId })
        assertEquals("2026-01-01T00:00:29.999Z", first.cursor)

        empty.bytes = pc.statusJson("job-vide", "queued").toByteArray()
        empty.modifiedTime = "2026-01-01T00:06:00Z"
        val second = transport.poll(first.cursor)

        assertTrue(second.statuses.any { it.jobId == "job-vide" && it.state == "queued" })
        assertEquals("2026-01-01T00:04:00Z", second.cursor)
    }
}
