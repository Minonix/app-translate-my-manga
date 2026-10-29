package com.tmm.mobile.data

import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_PC_DIRECT
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.transport.DirectTokenStore
import com.tmm.mobile.workers.FakeTokenCipher
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Refresh sur la chaine Hub VPS -> PC en direct -> Drive, et pc/remote.json qui n'ecrase plus l'URL du Hub.
@RunWith(RobolectricTestRunner::class)
class DirectFallbackRefreshTest {
    private val env = WorkerTestEnvironment()
    private val servers = mutableListOf<MockWebServer>()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
        env.tearDown()
    }

    private fun iso(millis: Long) = Instant.ofEpochMilli(millis).toString()

    private fun heartbeat(updatedAt: String, pcName: String) =
        """{"protocol":1,"pcName":"$pcName","appVersion":"1","updatedAt":"$updatedAt","slots":1,"activeJobIds":[],"queue":{"active":0,"waiting":0,"failed":0}}"""

    // Nombre de /poll recus par chaque serveur direct.
    private val pollCounts = mutableMapOf<MockWebServer, java.util.concurrent.atomic.AtomicInteger>()

    private fun server(pollBody: String): MockWebServer {
        val polls = java.util.concurrent.atomic.AtomicInteger()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.url.encodedPath.endsWith("/health")) {
                        MockResponse(code = 200, body = """{"protocol":1,"pcName":"PC","appVersion":"1"}""")
                    } else {
                        if (request.url.encodedPath.endsWith("/poll")) polls.incrementAndGet()
                        MockResponse(code = 200, body = pollBody)
                    }
            }
            start()
        }
        servers += server
        pollCounts[server] = polls
        return server
    }

    private fun MockWebServer.polls(): Int = pollCounts.getValue(this).get()

    private suspend fun pairHub(url: String) {
        env.prefs.hubSlot.setUrl(url)
        DirectTokenStore(env.prefs.hubSlot, FakeTokenCipher).set("jeton-hub")
    }

    private suspend fun pairPc(url: String) {
        env.prefs.pcSlot.setUrl(url)
        DirectTokenStore(env.prefs.pcSlot, FakeTokenCipher).set("jeton-pc")
    }

    private fun job(jobId: String, channel: String) = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = "S/T/$jobId", sourceDir = "S", seriesDir = "T", chapterDir = jobId,
        seriesKey = null, chapterKey = null, phoneState = "SENT",
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null, channel = channel,
    )

    private fun result(jobId: String) =
        """{"protocol":1,"jobId":"$jobId","finishedAt":"${iso(env.clock.millis)}","mode":"translate","file":{"name":"result.cbz","size":3,"sha256":"ab"},"localFolderName":"T","chapterFileName":"C.cbz","pages":1}"""

    // Bug du 29/10 : le PC publie sa propre adresse dans remote.json ; elle ne doit jamais remplacer celle du
    // Hub (jeton different -> 401 -> jeton du Hub efface). Elle ne pre-remplit que l'emplacement PC.
    @Test fun remoteJson_neverOverwritesHubUrl_onlyPrefillsUnpairedPcSlot() = runTest {
        val now = env.clock.millis
        val hub = server("""{"cursor":"${iso(now)}","heartbeat":${heartbeat(iso(now - 3_600_000L), "PC-HUB")}}""")
        val hubUrl = hub.url("/api/remote/v1/").toString()
        pairHub(hubUrl)
        env.pc.writeRemote("pc-bureau.tail.ts.net")

        env.container.refreshCoordinator.refresh()

        assertEquals(hubUrl, env.prefs.hubSlot.url.first())
        assertNotNull(env.prefs.hubSlot.tokenEnc.first())
        assertEquals("https://pc-bureau.tail.ts.net/api/remote/v1/", env.prefs.pcSlot.url.first())
    }

    @Test fun remoteJson_neverOverwritesPairedPcUrl() = runTest {
        val pc = server("{}")
        val pcUrl = pc.url("/api/remote/v1/").toString()
        pairPc(pcUrl)
        env.pc.writeRemote("autre-adresse.tail.ts.net")

        env.container.refreshCoordinator.refresh()

        assertEquals(pcUrl, env.prefs.pcSlot.url.first())
    }

    // Hub frais et aucun job "pc_direct" : le PC n'est pas interroge.
    @Test fun hubFresh_doesNotContactPc() = runTest {
        val now = env.clock.millis
        val hub = server("""{"cursor":"${iso(now)}","heartbeat":${heartbeat(iso(now - 30_000L), "PC-HUB")}}""")
        val pc = server("{}")
        pairHub(hub.url("/").toString())
        pairPc(pc.url("/").toString())

        env.container.refreshCoordinator.refresh()

        assertEquals(0, pc.requestCount)
        assertEquals(now - 30_000L, env.prefs.heartbeatSeenAt.first())
    }

    // Panne du 29/10 : le Hub repond mais son heartbeat PC a 10 min. Le PC est tente en direct : son heartbeat
    // frais l'emporte, son statut est pris pour le job du Hub, mais pas son resultat (fichier sur le PC seulement
    // pour ses propres jobs) ; le job "direct" reste sur le Hub.
    @Test fun hubStale_pollsPcDirect_andKeepsHubJobOnHub() = runTest {
        val now = env.clock.millis
        env.db.jobDao().upsert(job("job-hub", CHANNEL_DIRECT))
        val hub = server("""{"cursor":"${iso(now)}","heartbeat":${heartbeat(iso(now - 600_000L), "PC-HUB")}}""")
        val pc = server(
            """{"cursor":"${iso(now)}","heartbeat":${heartbeat(iso(now - 5_000L), "PC-DIRECT")},
               "statuses":[{"protocol":1,"jobId":"job-hub","updatedAt":"${iso(now)}","mode":"translate","state":"translating"}],
               "results":[${result("job-hub")}]}""",
        )
        pairHub(hub.url("/").toString())
        pairPc(pc.url("/").toString())

        env.container.refreshCoordinator.refresh()

        assertEquals(1, pc.polls())
        assertTrue(env.prefs.heartbeatJson.first()!!.contains("PC-DIRECT"))
        assertEquals(now - 5_000L, env.prefs.heartbeatSeenAt.first())
        val updated = env.db.jobDao().get("job-hub")!!
        assertEquals("translating", updated.pcState)
        assertEquals("SENT", updated.phoneState)
        assertEquals(CHANNEL_DIRECT, updated.channel)
    }

    // Hub injoignable : repli sur le PC, dont le resultat est applique a son propre job.
    @Test fun hubDown_fallsBackToPc_andAppliesPcJobResult() = runTest {
        val now = env.clock.millis
        env.db.jobDao().upsert(job("job-pc", CHANNEL_PC_DIRECT))
        val down = MockWebServer().run { start(); val u = url("/").toString(); close(); u }
        pairHub(down)
        val pc = server("""{"cursor":"${iso(now)}","heartbeat":${heartbeat(iso(now), "PC-DIRECT")},"results":[${result("job-pc")}]}""")
        pairPc(pc.url("/").toString())

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-pc")!!
        assertEquals("RESULT_READY", updated.phoneState)
        assertEquals(CHANNEL_PC_DIRECT, updated.channel)
        assertNotNull(env.prefs.hubSlot.tokenEnc.first())
    }

    // Job "pc_direct" en cours : le PC est aussi interroge quand le Hub est frais (le Hub ne le connait pas).
    @Test fun activePcJob_pollsPcEvenWhenHubIsFresh() = runTest {
        val now = env.clock.millis
        env.db.jobDao().upsert(job("job-pc", CHANNEL_PC_DIRECT))
        val hub = server("""{"cursor":"${iso(now)}","heartbeat":${heartbeat(iso(now - 10_000L), "PC-HUB")}}""")
        val pc = server("""{"cursor":"${iso(now)}","results":[${result("job-pc")}]}""")
        pairHub(hub.url("/").toString())
        pairPc(pc.url("/").toString())

        env.container.refreshCoordinator.refresh()

        assertEquals(1, pc.polls())
        assertEquals("RESULT_READY", env.db.jobDao().get("job-pc")!!.phoneState)
    }
}
