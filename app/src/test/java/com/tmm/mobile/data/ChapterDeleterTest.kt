package com.tmm.mobile.data

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsValues
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

// Suppression depuis la Fiche serie : CBZ FR local, chapitre telecharge par Mihon, commande "delete"
// vers le PC, et chapitres a job actif ignores.
@RunWith(RobolectricTestRunner::class)
class ChapterDeleterTest {
    private val env = WorkerTestEnvironment()
    private lateinit var root: File
    private lateinit var fs: FakeMihonFs
    private val path = MihonPath("Source (EN)", "Serie", "Chapitre 1")

    @Before
    fun setUp() {
        env.setUp()
        root = kotlin.io.path.createTempDirectory("mihon").toFile()
        fs = FakeMihonFs(root)
        env.container.mihonFs = fs
    }

    @After
    fun tearDown() {
        env.tearDown()
        root.deleteRecursively()
    }

    private fun job(phoneState: String) = JobEntity(
        jobId = "j1", mode = "translate", pages = null, translate = true,
        mihonPath = path.flatten(), sourceDir = path.source, seriesDir = path.series, chapterDir = path.chapter,
        seriesKey = "sk", chapterKey = null, phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = 0, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    // Chapitre telecharge (downloads/...) avec sa traduction installee (local/...), connu du PC.
    private suspend fun seedChapter(latestJob: JobEntity? = null): Triple<ChapterJoinRow, File, File> {
        val chapterDir = File(root, "downloads/${path.source}/${path.series}/${path.chapter}").apply { mkdirs() }
        File(chapterDir, "001.jpg").writeBytes(byteArrayOf(1))
        val frFile = File(root, "local/Serie/Chapitre 1.cbz").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(2)) }
        val downloaded = DownloadedChapterEntity(
            mihonPath = path.flatten(), sourceDir = path.source, seriesDir = path.series, chapterDir = path.chapter,
            documentUri = chapterDir.absolutePath, isCbz = false, sizeBytes = 1, lastModified = 0, pageCount = 1,
            seriesKey = "sk", chapterKey = null, number = 1f,
        )
        env.db.downloadedChapterDao().upsert(downloaded)
        env.db.localFrChapterDao().upsert(
            LocalFrChapterEntity(
                localPath = "Serie/Chapitre 1.cbz", folderName = "Serie", fileName = "Chapitre 1.cbz",
                documentUri = frFile.absolutePath, lastModified = 0, jobId = null, mihonPath = path.flatten(),
            ),
        )
        val index = PcIndexChapterEntity(path.flatten(), original = true, fr = true)
        env.db.pcIndexChapterDao().insertAll(listOf(index))
        latestJob?.let { env.db.jobDao().upsert(it) }
        val facts = ChapterFacts(downloaded = true, inCatalog = false, index = index, latestJob = latestJob, localFr = true)
        val row = ChapterJoinRow("sk", chapter = null, downloaded = downloaded, mihonPath = path, facts = facts, status = deriveStatus(facts))
        return Triple(row, chapterDir, frFile)
    }

    private suspend fun seedSettings(version: Int) {
        val values = SettingsValues(
            sourceLanguage = "auto", targetLanguage = "FRA", translationProvider = "gemini",
            autoFailoverToLocalLlm = false, autoVerifyAfterTranslate = false,
            chapterConcurrency = 1, concurrency = 1, pileSize = 1, useGpu = false,
            detector = "default", preferredOcr = "auto", inpainter = "lama_large",
            inpaintingSize = 1024, font = "Anime Ace",
        )
        val settings = PcSettings(updatedAt = "2026-01-01T00:00:00Z", version = version, lastCommandId = null, values = values)
        env.prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), settings))
    }

    @Test fun localFrOnly_deletesInstalledCbz_keepsMihonDownload() = runTest {
        val (row, chapterDir, frFile) = seedChapter()

        val outcome = env.container.chapterDeleter.delete(listOf(row), DeleteOptions(localFr = true, mihon = false, pc = false))

        assertEquals(1, outcome.localFrDeleted)
        assertEquals(0, outcome.mihonDeleted)
        assertFalse(frFile.exists())
        assertTrue(env.db.localFrChapterDao().observeAll().first().isEmpty())
        assertTrue(chapterDir.exists())
        assertNotNull(env.db.downloadedChapterDao().get(path.flatten()))
        assertNull(env.prefs.pendingCommandsJson.first())
    }

    @Test fun mihon_deletesDownloadedChapter_andRoomRow() = runTest {
        val (row, chapterDir, frFile) = seedChapter()

        val outcome = env.container.chapterDeleter.delete(listOf(row), DeleteOptions(localFr = false, mihon = true, pc = false))

        assertEquals(1, outcome.mihonDeleted)
        assertEquals(0, outcome.failed)
        assertFalse(chapterDir.exists())
        assertNull(env.db.downloadedChapterDao().get(path.flatten()))
        assertTrue(frFile.exists())
    }

    @Test fun pc_queuesDeleteCommand_withExpectedJson_andKeepsPcIndex() = runTest {
        seedSettings(version = 7)
        val (row, _, _) = seedChapter()

        val outcome = env.container.chapterDeleter.delete(listOf(row), DeleteOptions(localFr = false, mihon = false, pc = true))
        env.container.settingsSync.sendPendingCommands()

        assertEquals(1, outcome.pcChapters)
        assertFalse(outcome.pcUnavailable)
        val commandsId = requireNotNull(env.prefs.driveCommandsId.first())
        val files = env.drive.files.values.filter { commandsId in it.parents }
        assertEquals(1, files.size)
        val json = ProtocolJson.parseToJsonElement(String(files.single().bytes)).jsonObject
        assertEquals(JsonPrimitive(1), json["protocol"])
        assertEquals(JsonPrimitive("delete"), json["type"])
        assertEquals(JsonPrimitive(7), json["baseVersion"])
        assertEquals(JsonObject(emptyMap()), json["changes"])
        assertEquals(files.single().name.removeSuffix(".json"), json.getValue("cmdId").jsonPrimitive.content)
        val delete = json.getValue("delete").jsonObject
        assertEquals(JsonPrimitive(true), delete["original"])
        assertEquals(JsonPrimitive(true), delete["fr"])
        val chapters = delete.getValue("chapters").jsonArray
        assertEquals(
            JsonArray(listOf(JsonObject(mapOf("source" to JsonPrimitive(path.source), "series" to JsonPrimitive(path.series), "chapter" to JsonPrimitive(path.chapter))))),
            chapters,
        )
        // Pas de marquage optimiste : on attend l'index republie par le PC.
        val index = requireNotNull(env.db.pcIndexChapterDao().get(path.flatten()))
        assertTrue(index.original && index.fr)
        // changes vide : values() inchange par la commande en attente.
        assertEquals("FRA", env.container.settingsSync.values()?.targetLanguage)
    }

    @Test fun pc_withoutPcSettings_queuesNothing_andReportsIt() = runTest {
        val (row, _, _) = seedChapter()

        val outcome = env.container.chapterDeleter.delete(listOf(row), DeleteOptions(localFr = false, mihon = false, pc = true))

        assertTrue(outcome.pcUnavailable)
        assertEquals(0, outcome.pcChapters)
        assertNull(env.prefs.pendingCommandsJson.first())
    }

    @Test fun activeJob_chapterIsSkipped() = runTest {
        seedSettings(version = 1)
        val (row, chapterDir, frFile) = seedChapter(latestJob = job("RESULT_READY"))

        val outcome = env.container.chapterDeleter.delete(listOf(row), DeleteOptions(localFr = true, mihon = true, pc = true))

        assertEquals(1, outcome.skippedActive)
        assertEquals(0, outcome.chapters)
        assertTrue(chapterDir.exists())
        assertTrue(frFile.exists())
        assertNotNull(env.db.downloadedChapterDao().get(path.flatten()))
        assertEquals(1, env.db.localFrChapterDao().observeAll().first().size)
        assertNull(env.prefs.pendingCommandsJson.first())
    }

    @Test fun finishedJob_doesNotBlockDeletion() = runTest {
        val (row, _, frFile) = seedChapter(latestJob = job("INSTALLED"))

        val outcome = env.container.chapterDeleter.delete(listOf(row), DeleteOptions(localFr = true, mihon = false, pc = false))

        assertEquals(0, outcome.skippedActive)
        assertFalse(frFile.exists())
    }

    @Test fun noMihonTree_keepsRoomRows_andCountsFailures() = runTest {
        val (row, chapterDir, _) = seedChapter()
        env.container.mihonFs = null

        val outcome = env.container.chapterDeleter.delete(listOf(row), DeleteOptions(localFr = true, mihon = true, pc = false))

        assertEquals(2, outcome.failed)
        assertTrue(chapterDir.exists())
        assertNotNull(env.db.downloadedChapterDao().get(path.flatten()))
        assertEquals(1, env.db.localFrChapterDao().observeAll().first().size)
    }
}
