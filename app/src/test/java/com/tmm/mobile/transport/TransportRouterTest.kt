package com.tmm.mobile.transport

import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.CHANNEL_PC_DIRECT
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.workers.FakeTokenCipher
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Chaine de repli Hub VPS -> PC en direct -> Drive, et routage de chaque canal vers son transport.
@RunWith(RobolectricTestRunner::class)
class TransportRouterTest {
    private val env = WorkerTestEnvironment()
    private val servers = mutableListOf<MockWebServer>()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
        env.tearDown()
    }

    // Serveur direct : health OK, poll repondu avec `pollCode`.
    private fun server(pollCode: Int = 200): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/health") ->
                    MockResponse(code = 200, body = """{"protocol":1,"pcName":"PC","appVersion":"1"}""")
                else -> MockResponse(code = pollCode, body = "{}")
            }
        }
        start()
        servers += this
    }

    // Adresse d'un serveur arrete : connexion refusee aussitot, sans attendre les 3 s du health-check.
    private fun downUrl(): String = MockWebServer().run {
        start()
        val url = url("/").toString()
        close()
        url
    }

    private suspend fun pairHub(url: String) {
        env.prefs.hubSlot.setUrl(url)
        DirectTokenStore(env.prefs.hubSlot, FakeTokenCipher).set("jeton-hub")
    }

    private suspend fun pairPc(url: String) {
        env.prefs.pcSlot.setUrl(url)
        DirectTokenStore(env.prefs.pcSlot, FakeTokenCipher).set("jeton-pc")
    }

    @Test fun activeChannel_hubAnswers_isHub_withoutAskingPc() = runTest {
        val hub = server()
        val pc = server()
        pairHub(hub.url("/").toString())
        pairPc(pc.url("/").toString())

        assertEquals(CHANNEL_DIRECT, env.router.activeChannel())
        assertSame(env.direct, env.router.active())
        assertEquals(0, pc.requestCount)
    }

    @Test fun activeChannel_hubDown_fallsBackToPc() = runTest {
        val pc = server()
        pairHub(downUrl())
        pairPc(pc.url("/").toString())

        assertEquals(CHANNEL_PC_DIRECT, env.router.activeChannel())
        assertSame(env.pcDirect, env.router.active())
    }

    @Test fun activeChannel_hubAndPcDown_fallsBackToDrive() = runTest {
        pairHub(downUrl())
        pairPc(downUrl())

        assertEquals(CHANNEL_DRIVE, env.router.activeChannel())
        assertSame(env.transport, env.router.active())
    }

    // PC non associe (adresse publiee par remote.json, sans jeton) : aucun appel, pas d'attente.
    @Test fun activeChannel_pcNotPaired_isNeverContacted() = runTest {
        val pc = server()
        pairHub(downUrl())
        env.prefs.pcSlot.setUrl(pc.url("/").toString())

        assertEquals(CHANNEL_DRIVE, env.router.activeChannel())
        assertEquals(0, pc.requestCount)
    }

    // Les jobs "direct" d'avant le PC en secours restent sur le Hub (aucune migration de la colonne channel).
    @Test fun forJob_routesEachChannelToItsTransport() {
        assertSame(env.direct, env.router.forJob(job(CHANNEL_DIRECT)))
        assertSame(env.pcDirect, env.router.forJob(job(CHANNEL_PC_DIRECT)))
        assertSame(env.transport, env.router.forJob(job(CHANNEL_DRIVE)))
        assertSame(env.transport, env.router.forChannel("inconnu"))
    }

    // Jetons distincts : un 401 du PC n'efface que l'appairage du PC, le Hub reste associe.
    @Test fun pc401_clearsOnlyPcPairing() = runTest {
        val hub = server()
        val pc = server(pollCode = 401)
        pairHub(hub.url("/").toString())
        pairPc(pc.url("/").toString())

        val error = runCatching { env.pcDirect.poll(null) }.exceptionOrNull()

        assertTrue(error is DirectPairingExpiredException)
        assertNull(env.prefs.pcSlot.tokenEnc.first())
        assertTrue(env.prefs.pcSlot.pairingExpired.first())
        assertNotNull(env.prefs.hubSlot.tokenEnc.first())
        assertFalse(env.prefs.hubSlot.pairingExpired.first())
        assertTrue(env.direct.isPaired())
    }

    // Appairage du PC : URL, jeton et nom ecrits dans l'emplacement PC, l'emplacement Hub n'est pas touche.
    @Test fun pairPc_writesPcSlotOnly() = runTest {
        val pc = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse(code = 200, body = """{"token":"jeton-pc","pcName":"PC-BUREAU"}""")
            }
            start()
            servers += this
        }
        env.prefs.hubSlot.setUrl("https://hub.example/api/remote/v1/")

        env.pcDirect.pair(pc.url("/").toString(), "123456", "Pixel")

        assertEquals(pc.url("/api/remote/v1/").toString(), env.prefs.pcSlot.url.first())
        assertEquals("PC-BUREAU", env.prefs.pcSlot.pcName.first())
        assertTrue(env.pcDirect.isPaired())
        assertEquals("https://hub.example/api/remote/v1/", env.prefs.hubSlot.url.first())
        assertNull(env.prefs.hubSlot.tokenEnc.first())
    }

    private fun job(channel: String) = JobEntity(
        jobId = "job-$channel", mode = "translate", pages = null, translate = true,
        mihonPath = "S/T/C", sourceDir = "S", seriesDir = "T", chapterDir = "C",
        seriesKey = null, chapterKey = null, phoneState = "SENT",
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0L, updatedAt = 0L, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null, channel = channel,
    )
}
