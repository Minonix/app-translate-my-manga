package com.tmm.mobile.data

import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.SeriesSettingsEntity
import com.tmm.mobile.protocol.ChapterRef
import com.tmm.mobile.protocol.FileRef
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.Quality
import com.tmm.mobile.protocol.RequestJson
import com.tmm.mobile.protocol.RequestOptions
import com.tmm.mobile.protocol.SeriesRef
import com.tmm.mobile.transport.DirectTokenStore
import com.tmm.mobile.workers.FakeTokenCipher
import com.tmm.mobile.workers.WorkerTestEnvironment
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.15 : cycle refresh() = poll (statuses/results) + autoTranslate(), sur le meme
// FakeDrive/FakePc que DriveTransportTest (E7).
@RunWith(RobolectricTestRunner::class)
class RefreshCoordinatorTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private suspend fun writeOwnRequest(jobId: String, mihonPath: MihonPath = MihonPath("MangaSource", "Titre", "Chapitre 1")): String {
        env.container.transport.ensureLayout()
        val folderId = env.container.transport.ensureJobFolder(jobId)
        env.container.transport.writeRequest(
            folderId,
            RequestJson(
                jobId = jobId,
                createdAt = "2026-01-01T00:00:00Z",
                device = "TestDevice",
                series = SeriesRef(mihonPath.series, mihonPath.source, null, null),
                chapter = ChapterRef(mihonPath.chapter, 1.0, null, null),
                mihonPath = mihonPath,
                file = null,
                options = RequestOptions(mode = "translate"),
            ),
        )
        return folderId
    }

    private fun job(jobId: String, mihonPath: String = "MangaSource/Titre/Chapitre 1") = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = mihonPath, sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 1",
        seriesKey = null, chapterKey = null, phoneState = "SENT",
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = env.clock.millis, updatedAt = env.clock.millis, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    @Test fun refresh_relocatesMailboxOnce_andClearsAuthExpired() = runTest {
        // Ids en cache de l'ancienne boite APP/TranslateMyManga, version de layout jamais posee.
        env.prefs.setDriveAppId("old-app")
        env.prefs.setDriveJobsId("old-jobs")
        env.prefs.setAuthExpired(true)

        env.container.refreshCoordinator.refresh()

        val rootId = env.drive.childOf(com.tmm.mobile.fake.FakeDrive.APP_ID, "TranslateMyManga")!!.id
        assertEquals(com.tmm.mobile.fake.FakeDrive.APP_ID, env.prefs.driveAppId.first())
        assertEquals(env.drive.childOf(rootId, "jobs")!!.id, env.prefs.driveJobsId.first())
        assertEquals(2, env.prefs.driveLayoutVersion.first())
        assertEquals(false, env.prefs.authExpired.first())
    }

    // Heartbeat direct plus recent deja connu : celui, plus ancien, lu sur Drive ne doit pas l'ecraser
    // (l'accueil alternait sinon "PC en ligne"/"hors ligne" a chaque refresh).
    @Test fun refresh_keepsFresherHeartbeat_overOlderDriveOne() = runTest {
        env.container.transport.ensureLayout()
        env.pc.writeHeartbeat()
        val fresh = com.tmm.mobile.protocol.Heartbeat(
            pcName = "PC-DIRECT", appVersion = "1", updatedAt = "2099-01-01T00:00:00Z", slots = 1,
            activeJobIds = emptyList(), queue = com.tmm.mobile.protocol.QueueCounts(0, 0, 0),
        )
        env.prefs.setHeartbeatJson(com.tmm.mobile.protocol.ProtocolJson.encodeToString(com.tmm.mobile.protocol.Heartbeat.serializer(), fresh))

        env.container.refreshCoordinator.refresh()

        assertTrue(env.prefs.heartbeatJson.first()!!.contains("PC-DIRECT"))
    }

    // Le PC repond en direct et aucun job Drive n'est actif : Drive doit quand meme etre interroge
    // pour confirmer le nouveau consentement, sinon le bandeau "Reconnecter" ne disparait jamais.
    @Test fun refresh_clearsAuthExpired_evenWhenDirectChannelAnswers() = runTest {
        val pc = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.url.encodedPath.endsWith("/health")) {
                        MockResponse(code = 200, body = """{"pcName":"PC","appVersion":"1"}""")
                    } else {
                        MockResponse(code = 200, body = "{}")
                    }
            }
            start()
        }
        try {
            env.prefs.setDirectUrl(pc.url("/").toString())
            DirectTokenStore(env.prefs, FakeTokenCipher).set("jeton")
            env.prefs.setAuthExpired(true)

            env.container.refreshCoordinator.refresh()

            assertEquals(false, env.prefs.authExpired.first())
        } finally {
            pc.close()
        }
    }

    @Test fun refresh_updatesPcState_forOwnJob() = runTest {
        writeOwnRequest("job-1")
        env.db.jobDao().upsert(job("job-1"))
        env.pc.accept("job-1")

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-1")!!
        assertEquals("processing", updated.pcState)
        assertEquals("SENT", updated.phoneState)
    }

    @Test fun refresh_setsInstalled_andStoresQuality_whenResultHasNoFile() = runTest {
        writeOwnRequest("job-2")
        env.db.jobDao().upsert(job("job-2"))
        env.pc.finish(
            "job-2",
            resultBytes = ByteArray(0),
            fileRef = null,
            quality = Quality(totalPages = 10, translatedBubbles = 8, untranslatedBubbles = 1, sfxKept = 2, verifiedAt = null),
        )

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-2")!!
        assertEquals("INSTALLED", updated.phoneState)
        val quality = env.db.chapterQualityDao().observe("MangaSource/Titre/Chapitre 1").first()
        assertEquals(8, quality?.translatedBubbles)
    }

    // Le meme result.json reste visible plusieurs refresh de suite : il ne doit etre applique qu'une fois.
    @Test fun refresh_doesNotReapplyResult_toAlreadyInstalledJob() = runTest {
        writeOwnRequest("job-3")
        env.db.jobDao().upsert(job("job-3"))
        env.pc.finish("job-3", resultBytes = ByteArray(0), fileRef = null, quality = null)
        env.container.refreshCoordinator.refresh()
        val installed = env.db.jobDao().get("job-3")!!

        env.clock.millis += 60_000
        env.container.refreshCoordinator.refresh()

        val again = env.db.jobDao().get("job-3")!!
        assertEquals("INSTALLED", again.phoneState)
        assertEquals(installed.installedAt, again.installedAt)
        assertEquals(installed.updatedAt, again.updatedAt)
    }

    // Repli Drive : job envoye en direct, Tailscale coupe ensuite. Le PC depose aussi le resultat sur Drive
    // (request.json ici seulement parce que FakePc en a besoin) ; le job passe sur Drive pour le recuperer.
    @Test fun refresh_takesDriveCopy_ofDirectJobResult() = runTest {
        writeOwnRequest("job-d1")
        env.db.jobDao().upsert(job("job-d1").copy(channel = CHANNEL_DIRECT))
        val fileId = env.pc.finish("job-d1", resultBytes = ByteArray(3), fileRef = FileRef("result.cbz", 3, "ab"))

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-d1")!!
        assertEquals("RESULT_READY", updated.phoneState)
        assertEquals(CHANNEL_DRIVE, updated.channel)
        assertEquals(fileId, updated.resultFileId)
    }

    // Resultat deja connu en direct mais telechargement impossible (PC eteint) : la copie Drive prend le relais.
    @Test fun refresh_switchesPendingDirectDownload_toDriveCopy() = runTest {
        writeOwnRequest("job-d2")
        env.db.jobDao().upsert(job("job-d2").copy(channel = CHANNEL_DIRECT, phoneState = "DOWNLOADING", resultFileId = "job-d2"))
        val fileId = env.pc.finish("job-d2", resultBytes = ByteArray(3), fileRef = FileRef("result.cbz", 3, "ab"))

        env.container.refreshCoordinator.refresh()

        val updated = env.db.jobDao().get("job-d2")!!
        assertEquals("DOWNLOADING", updated.phoneState)
        assertEquals(CHANNEL_DRIVE, updated.channel)
        assertEquals(fileId, updated.resultFileId)
    }

    // Deja installe depuis le direct : la copie Drive est ignoree, jamais de seconde installation.
    @Test fun refresh_ignoresDriveCopy_ofDirectJobAlreadyInstalled() = runTest {
        writeOwnRequest("job-d3")
        val installed = job("job-d3").copy(channel = CHANNEL_DIRECT, phoneState = "INSTALLED", installedAt = env.clock.millis)
        env.db.jobDao().upsert(installed)
        env.pc.finish("job-d3", resultBytes = ByteArray(3), fileRef = FileRef("result.cbz", 3, "ab"))

        env.container.refreshCoordinator.refresh()

        assertEquals(installed, env.db.jobDao().get("job-d3"))
    }

    @Test fun refresh_ignoresStatus_forJobUnknownLocally() = runTest {
        writeOwnRequest("job-not-mine")
        env.pc.accept("job-not-mine")

        env.container.refreshCoordinator.refresh()

        assertTrue(env.db.jobDao().observeAll().first().isEmpty())
        assertNull(env.db.jobDao().get("job-not-mine"))
    }

    @Test fun refresh_triggersAutoTranslate_forDownloadedChapterMarkedAutoTranslate() = runTest {
        env.db.seriesSettingsDao().upsert(SeriesSettingsEntity(seriesKey = "sk1", autoTranslate = true))
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = "MangaSource/Titre/Chapitre 2",
                sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = "Chapitre 2",
                documentUri = "doc-1", isCbz = true, sizeBytes = 10L, lastModified = 0L,
                pageCount = 5, seriesKey = "sk1", chapterKey = null, number = 2f,
            ),
        )

        env.container.refreshCoordinator.refresh()

        val jobs = env.db.jobDao().observeAll().first()
        assertEquals(1, jobs.size)
        assertEquals("MangaSource/Titre/Chapitre 2", jobs.single().mihonPath)
        assertEquals(JobMode.TRANSLATE, jobs.single().mode)
    }

    // Test reel v1.6.0 : app ouverte en continu, « PC vu il y a 6 min » alors que le PC ecrit son
    // heartbeat chaque minute. Sans ON_START ni Actualiser, le heartbeat doit quand meme etre relu.
    @Test fun refreshWhileForeground_picksUpNewHeartbeat_withoutUserAction() = runTest {
        // Scope jamais avance : le cooldown (secondes reelles) ne bloque pas les ticks rapproches du test.
        val coordinator = RefreshCoordinator(
            env.db, env.prefs, env.router, env.container.workManager, env.clock,
            CoroutineScope(StandardTestDispatcher()),
        ) {}
        env.pc.writeHeartbeat()
        val loop = launch(Dispatchers.Default) { coordinator.refreshWhileForeground(intervalMillis = 20, directIntervalMillis = 20) }
        try {
            withContext(Dispatchers.Default) {
                withTimeout(20_000) { env.prefs.heartbeatSeenAt.first { it != null } }
                env.clock.millis += 6 * 60_000L
                env.pc.writeHeartbeat()
                withTimeout(20_000) { env.prefs.heartbeatSeenAt.first { it != null && it >= env.clock.millis - 60_000L } }
            }
        } finally {
            loop.cancel()
        }
    }

    // Test reel 14/10 : « PC vu il y a 51 min » sans erreur alors que le PC ecrivait son heartbeat. Un refresh
    // qui ne rendait jamais la main gardait le verrou et tous les suivants etaient ignores en silence.
    // Le faux suspend a l'infini : il prouve la borne du coordinateur, pas celle d'un socket OkHttp.
    @Test fun refreshWhileForeground_recovers_afterAStepThatNeverReturns() = runTest {
        var calls = 0
        val coordinator = RefreshCoordinator(
            env.db, env.prefs, env.router, env.container.workManager, env.clock,
            CoroutineScope(StandardTestDispatcher()),
            timeoutMillis = 300,
        ) {
            if (calls++ == 0) awaitCancellation()
        }
        env.pc.writeHeartbeat()
        val loop = launch(Dispatchers.Default) { coordinator.refreshWhileForeground(intervalMillis = 20, directIntervalMillis = 20) }
        try {
            withContext(Dispatchers.Default) {
                withTimeout(20_000) { env.prefs.heartbeatSeenAt.first { it != null } }
                env.clock.millis += 6 * 60_000L
                env.pc.writeHeartbeat()
                withTimeout(20_000) { env.prefs.heartbeatSeenAt.first { it != null && it >= env.clock.millis - 60_000L } }
            }
        } finally {
            loop.cancel()
        }
    }

    // Test reel 16/10 : apres une nuit de traduction, 4 Actualiser sans « PC allume » ni file a jour, puis tout
    // d'un coup. Le heartbeat n'etait applique qu'a la fin du poll complet, perdu entier s'il depassait le delai.
    @Test fun refresh_showsPcOnline_evenWhenJobFilesAreTooSlowToRead() = runTest {
        val coordinator = RefreshCoordinator(
            env.db, env.prefs, env.router, env.container.workManager, env.clock,
            CoroutineScope(StandardTestDispatcher()),
            timeoutMillis = 1_500,
        ) {}
        env.db.jobDao().upsert(job("job-lent"))
        writeOwnRequest("job-lent")
        env.pc.accept("job-lent")
        env.pc.writeHeartbeat()
        val statusIds = env.drive.files.values.filter { it.name == "status.json" }.map { it.id }.toSet()
        env.drive.beforeMediaGet = { id -> if (id in statusIds) Thread.sleep(4_000) }
        withContext(Dispatchers.Default) { coordinator.refresh() }
        assertNotNull(env.prefs.heartbeatSeenAt.first())
    }
}
