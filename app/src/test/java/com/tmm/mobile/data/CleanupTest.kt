package com.tmm.mobile.data

import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.fake.FakeFile
import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.protocol.CommandResult
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsValues
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
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

// Bouton « Nettoyer » : essai a blanc sans effet, regles Mihon (_tmp, doublons « (1) »), dossiers Drive
// (jamais un job en cours) et accuse du PC (refus d'un PC pas a jour).
@RunWith(RobolectricTestRunner::class)
class CleanupTest {
    private val env = WorkerTestEnvironment()
    private lateinit var root: File

    @Before
    fun setUp() {
        env.setUp()
        root = createTempDirectory("mihon-cleanup").toFile()
        env.container.mihonFs = FakeMihonFs(root)
        env.container.mihonRootDocId = root.absolutePath
    }

    @After
    fun tearDown() {
        env.tearDown()
        root.deleteRecursively()
    }

    private val hour = 3_600_000L
    private fun old() = env.clock.millis - 48 * hour
    private fun recent() = env.clock.millis - hour

    private fun cleanup() = Cleanup(env.db, env.container.transport, env.container.settingsSync, env.clock) { env.container.mihonRoot() }

    private fun seriesDir(): File = File(root, "downloads/MangaDex (EN)/Blue Lock").apply { mkdirs() }

    private fun file(dir: File, name: String, bytes: Int, modified: Long = old()): File =
        File(dir, name).apply {
            writeBytes(ByteArray(bytes))
            setLastModified(modified)
        }

    private fun tmpDir(parent: File, name: String, modified: Long): File {
        val dir = File(parent, name).apply { mkdirs() }
        file(dir, "001.jpg", 10, modified)
        dir.setLastModified(modified)
        return dir
    }

    private fun job(jobId: String, phoneState: String, pcState: String? = null, receivedAt: Long? = null) = JobEntity(
        jobId = jobId, mode = "translate", pages = null, translate = true,
        mihonPath = "MangaSource/Titre/$jobId", sourceDir = "MangaSource", seriesDir = "Titre", chapterDir = jobId,
        seriesKey = null, chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 1000,
        pcState = pcState, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = 0, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = receivedAt, cancelWrittenAt = null, driveDeletedAt = null,
    )

    private suspend fun jobFolder(jobId: String, vararg files: String): String {
        val folderId = env.container.transport.ensureJobFolder(jobId)
        for (name in files) {
            val f = FakeFile(name = name, parents = mutableListOf(folderId), mimeType = "application/json",
                modifiedTime = Instant.ofEpochMilli(env.clock.millis).toString(), bytes = "{}".toByteArray())
            env.drive.files[f.id] = f
        }
        return folderId
    }

    private fun settingsValues() = SettingsValues(
        sourceLanguage = "auto", targetLanguage = "FRA", translationProvider = "gemini",
        autoFailoverToLocalLlm = false, autoVerifyAfterTranslate = false,
        chapterConcurrency = 1, concurrency = 1, pileSize = 1, useGpu = false,
        detector = "default", preferredOcr = "auto", inpainter = "lama_large",
        inpaintingSize = 1024, font = "Anime Ace",
    )

    private fun pcSettings(version: Int) =
        PcSettings(updatedAt = "2026-01-01T00:00:00Z", version = version, lastCommandId = null, values = settingsValues())

    @Test fun preview_isDryRun_deletesNothing() = runTest {
        val series = seriesDir()
        val tmp = tmpDir(series, "Chapter 5_tmp", old())
        val base = file(series, "Chapter 6_a1b2c3.cbz", 100)
        val dup = file(series, "Chapter 6_a1b2c3 (1).cbz", 100)
        val lone = file(series, "Chapter 7_a1b2c4 (1).cbz", 50)
        env.db.jobDao().upsert(job("done", "INSTALLED", receivedAt = 1))
        jobFolder("done", "received.json")

        val report = cleanup().preview()

        assertEquals(PhoneCleanup(tmpRemoved = 1, duplicatesRenamed = 1, duplicatesRemoved = 1), report.phone)
        assertEquals(DriveCleanup(folders = 1), report.drive)
        assertEquals(PcCleanupState.NEVER_SEEN, report.pc.state)
        assertTrue(tmp.exists() && base.exists() && dup.exists() && lone.exists())
        assertFalse(File(series, "Chapter 7_a1b2c4.cbz").exists())
        assertNotNull(env.drive.childOf(requireNotNull(env.prefs.driveJobsId.first()), "done"))
        assertNull(env.db.jobDao().get("done")!!.driveDeletedAt)
    }

    @Test fun tmp_youngerThan24h_isKept_olderIsDeleted() = runTest {
        val series = seriesDir()
        val stale = tmpDir(series, "Chapter 1_tmp", old())
        val fresh = tmpDir(series, "Chapter 2_tmp", recent())
        val freshFile = file(series, "Chapter 3.cbz.tmp", 10, recent())
        // Dossier ancien mais une page ecrite il y a une heure : telechargement encore vivant.
        val living = tmpDir(series, "Chapter 4_tmp", old())
        File(living, "002.jpg").apply { writeBytes(ByteArray(1)); setLastModified(recent()) }
        living.setLastModified(old())

        val report = cleanup().execute()

        assertEquals(1, report.phone?.tmpRemoved)
        assertFalse(stale.exists())
        assertTrue(fresh.exists() && freshFile.exists() && living.exists())
    }

    @Test fun duplicate_renamedIfFree_deletedIfSameSize_keptIfSizeDiffers() = runTest {
        val series = seriesDir()
        val free = file(series, "Chapter 1_0a0b01 (1).cbz", 40)
        file(series, "Chapter 2_0a0b02.cbz", 70)
        val same = file(series, "Chapter 2_0a0b02 (1).cbz", 70)
        file(series, "Chapter 3_0a0b03.cbz", 70)
        val differs = file(series, "Chapter 3_0a0b03 (1).cbz", 71)
        val localSeries = File(root, "local/Blue Lock").apply { mkdirs() }
        val localDup = file(localSeries, "c0009 - Chapter 9 (1).cbz", 5)

        val report = cleanup().execute()

        assertEquals(PhoneCleanup(duplicatesRenamed = 2, duplicatesRemoved = 1, duplicatesToCheck = 1), report.phone)
        assertFalse(free.exists())
        assertEquals(40L, File(series, "Chapter 1_0a0b01.cbz").length())
        assertFalse(same.exists())
        assertTrue(File(series, "Chapter 2_0a0b02.cbz").exists())
        assertTrue(differs.exists())
        assertEquals(70L, File(series, "Chapter 3_0a0b03.cbz").length())
        assertFalse(localDup.exists())
        assertTrue(File(localSeries, "c0009 - Chapter 9.cbz").exists())
    }

    // Un titre qui finit vraiment par « (2) » n'est pas une copie ; cover (1) n'est rangee que sans autre couverture.
    @Test fun duplicate_onlyMihonAndTmmNames_andCovers() = runTest {
        val series = seriesDir()
        val titled = file(series, "Scan_Chapter 4 (2).cbz", 10)
        val localSeries = File(root, "local/Blue Lock").apply { mkdirs() }
        val localTitled = file(localSeries, "Blue Lock (2).cbz", 10)
        file(localSeries, "cover.jpg", 30)
        val sameCover = file(localSeries, "cover (1).jpg", 30)
        val otherLocal = File(root, "local/Kagurabachi").apply { mkdirs() }
        file(otherLocal, "cover.webp", 30)
        val strayCover = file(otherLocal, "cover (1).jpg", 31)

        val report = cleanup().execute()

        assertEquals(PhoneCleanup(duplicatesRemoved = 1, duplicatesToCheck = 1), report.phone)
        assertTrue(titled.exists() && localTitled.exists() && strayCover.exists())
        assertFalse(sameCover.exists())
        assertFalse(File(otherLocal, "cover.jpg").exists())
    }

    // Installation en cours dans local/ : la serie n'est pas touchee.
    @Test fun localSeries_withInstallInFlight_isSkipped() = runTest {
        val localSeries = File(root, "local/Blue Lock").apply { mkdirs() }
        val dup = file(localSeries, "c0009 - Chapter 9 (1).cbz", 5)
        file(localSeries, ".Chapter 10.cbz.tmm-job1.tmp", 5)

        cleanup().execute()

        assertTrue(dup.exists())
    }

    @Test fun drive_neverDeletesActiveJobs_deletesFinishedAndAcknowledgedOnes() = runTest {
        val dao = env.db.jobDao()
        dao.upsert(job("sent", "SENT", pcState = "processing"))
        dao.upsert(job("ready", "RESULT_READY", pcState = "done"))
        dao.upsert(job("installed", "INSTALLED", pcState = "done", receivedAt = 1))
        dao.upsert(job("unacked", "INSTALLED", pcState = "done"))
        dao.upsert(job("cancelled", "CANCEL_REQUESTED", pcState = "cancelled"))
        dao.upsert(job("failed", "FAILED", pcState = "failed"))
        jobFolder("sent", "status.json")
        jobFolder("ready", "status.json", "result.json")
        jobFolder("installed", "result.json", "received.json")
        jobFolder("unacked", "result.json")
        jobFolder("cancelled", "status.json")
        jobFolder("failed", "status.json")
        // Inconnus de la base : resultat pas encore installe (garde) et resultat deja installe (supprime).
        jobFolder("orphan-result", "status.json", "result.json")
        jobFolder("orphan-received", "result.json", "received.json")
        // Le PC traite un job d'un autre telephone : status non terminal, garde.
        env.pc.writeStatus("orphan-result", ProtocolJson.decodeFromString(
            com.tmm.mobile.protocol.StatusJson.serializer(), env.pc.statusJson("orphan-result", "done")))
        jobFolder("orphan-running")
        env.pc.writeStatus("orphan-running", ProtocolJson.decodeFromString(
            com.tmm.mobile.protocol.StatusJson.serializer(), env.pc.statusJson("orphan-running", "processing")))

        val report = cleanup().execute()

        val jobsId = requireNotNull(env.prefs.driveJobsId.first())
        val left = setOf("sent", "ready", "unacked", "orphan-result", "orphan-running")
        val gone = setOf("installed", "cancelled", "failed", "orphan-received")
        assertEquals(DriveCleanup(folders = gone.size), report.drive)
        left.forEach { assertNotNull("$it garde", env.drive.childOf(jobsId, it)) }
        gone.forEach { assertNull("$it supprime", env.drive.childOf(jobsId, it)) }
        assertNotNull(dao.get("installed")!!.driveDeletedAt)
        assertNull(dao.get("sent")!!.driveDeletedAt)
        // Pas de purge de la base locale.
        assertEquals(6, dao.getAll(listOf("sent", "ready", "installed", "unacked", "cancelled", "failed")).size)
    }

    // R10 : echec a l'installation avec resultat connu, la relance relit result.cbz sur Drive.
    @Test fun drive_keepsFailedJobRetryableOnSameResult() {
        val retryable = job("r", "FAILED", pcState = "done").copy(
            failedStep = "install", resultJson = "{}", resultFileId = "file",
        )
        assertFalse(Cleanup.driveFolderDeletable(retryable))
        assertTrue(Cleanup.driveFolderDeletable(retryable.copy(failedStep = "upload", resultFileId = null)))
        assertFalse(Cleanup.driveFolderDeletable(job("s", "FAILED", pcState = "processing")))
    }

    @Test fun pc_unknownCommandType_isOutdated_notBlocking() = runTest {
        env.prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), pcSettings(version = 3)))
        val sync = env.container.settingsSync

        val report = cleanup().execute()
        val cmdId = requireNotNull(report.pc.cmdId)
        assertEquals(PcCleanupState.WAITING, report.pc.state)
        sync.sendPendingCommands()
        // Reponse d'un PC anterieur a la commande (bridge.py : type inconnu).
        sync.reconcile(pcSettings(version = 3).copy(lastCommandId = cmdId, lastCommandResult = CommandResult(
            cmdId, "rejected", code = "bad_request", message = Cleanup.UNKNOWN_COMMAND_MESSAGE)))

        val banner = sync.awaitConfirmation(cmdId)
        assertEquals(PcCleanupState.OUTDATED, Cleanup.pcOutcome(banner, sync.commandResult(cmdId)))
        assertEquals(PcCleanupState.REJECTED, Cleanup.pcOutcome(banner, CommandResult(cmdId, "rejected", code = "stale_version")))
    }

    @Test fun pc_appliedCleanup_reportsForgottenJobs() = runTest {
        env.prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), pcSettings(version = 3)))
        val sync = env.container.settingsSync
        val cmdId = requireNotNull(cleanup().execute().pc.cmdId)
        sync.sendPendingCommands()
        val commandsId = requireNotNull(env.prefs.driveCommandsId.first())
        val sent = String(requireNotNull(env.drive.childOf(commandsId, "$cmdId.json")).bytes)
        assertTrue(sent.contains("\"type\":\"cleanup\"") && sent.contains("\"baseVersion\":3"))

        sync.reconcile(pcSettings(version = 3).copy(lastCommandId = cmdId,
            lastCommandResult = CommandResult(cmdId, "applied", deleted = 4, keptJobs = 2)))

        val banner = sync.awaitConfirmation(cmdId)
        assertEquals(PcCleanupState.DONE, Cleanup.pcOutcome(banner, sync.commandResult(cmdId)))
        assertEquals(4, sync.commandResult(cmdId)?.deleted)
    }
}
