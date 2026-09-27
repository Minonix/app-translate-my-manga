package com.tmm.mobile.ui.screens.onboarding

import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.mihon.MihonBackup
import com.tmm.mobile.mihon.MihonBackupCategory
import com.tmm.mobile.mihon.MihonBackupChapter
import com.tmm.mobile.mihon.MihonBackupManga
import com.tmm.mobile.mihon.MihonBackupSource
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md E10.3 : couvre les deux seams testables de OnboardingViewModel (attachMihonTree
// et runImport) sans passer par SAF/Play Services, sur FakeMihonFs + Room en memoire (WorkerTestEnvironment).
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    private val env = WorkerTestEnvironment()
    private lateinit var root: File

    @Before
    fun setUp() {
        env.setUp()
        root = kotlin.io.path.createTempDirectory().toFile()
    }

    @After
    fun tearDown() {
        env.tearDown()
        root.deleteRecursively()
    }

    private fun vm() = OnboardingViewModel(env.container)

    @Test
    fun attachMihonTree_detectsMissingAndFoundSubfolders() {
        File(root, "downloads").mkdirs()
        File(root, "local").mkdirs()
        // autobackup/ volontairement absent

        val fs = FakeMihonFs(root)
        val vm = vm()
        vm.attachMihonTree(fs, root.absolutePath, "/Documents/Mihon")

        val state = vm.state.value
        assertTrue(state.isMihonSelected)
        assertFalse(state.isWorking)
        assertEquals("/Documents/Mihon", state.mihonFolderPath)
        val byName = state.subfolders.associate { it.name to it.found }
        assertTrue(byName["downloads/"] == true)
        assertTrue(byName["local/"] == true)
        assertTrue(byName["autobackup/"] == false)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeBackup(dir: File, name: String) {
        val backup = MihonBackup(
            backupManga = listOf(
                MihonBackupManga(
                    source = 42L,
                    url = "/manga/1",
                    title = "One Piece",
                    categories = emptyList(),
                    chapters = listOf(MihonBackupChapter(url = "/manga/1/ch1", name = "Chapitre 1", chapterNumber = 1f)),
                ),
            ),
            backupCategories = listOf(MihonBackupCategory(name = "Lecture", order = 0)),
            backupSources = listOf(MihonBackupSource(name = "MangaSource", sourceId = 42L)),
        )
        val bytes = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup)
        val gzipped = ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(bytes) } }.toByteArray()
        File(dir, name).writeBytes(gzipped)
    }

    @Test
    fun runImport_noBackup_reportsEmptyLibrary() = runTest {
        File(root, "autobackup").mkdirs()
        val fs = FakeMihonFs(root)
        val vm = vm()

        vm.runImport(fs, root.absolutePath)

        val state = vm.state.value
        assertFalse(state.hasBackup)
        assertTrue(state.isReadDone)
        assertEquals(0, state.seriesCount)
        assertEquals(0, state.toSendCount)
    }

    @Test
    fun runImport_withBackupAndDownloads_importsAndCountsToSend() = runTest {
        val autobackup = File(root, "autobackup").apply { mkdirs() }
        writeBackup(autobackup, "com.mihon_2026-09-26.tachibk")

        val downloads = File(root, "downloads").apply { mkdirs() }
        val sourceDir = File(downloads, "MangaSource").apply { mkdirs() }
        val seriesDir = File(sourceDir, "One Piece").apply { mkdirs() }
        File(seriesDir, "Chapitre 1").apply { mkdirs() }
        File(seriesDir, "Chapitre 1/1.jpg").writeBytes(byteArrayOf(1))

        val fs = FakeMihonFs(root)
        val vm = vm()

        vm.runImport(fs, root.absolutePath)

        val state = vm.state.value
        assertTrue(state.hasBackup)
        assertEquals("autobackup/com.mihon_2026-09-26.tachibk", state.backupFileName)
        assertTrue(state.isReadDone)
        assertEquals(1, state.seriesCount)
        assertEquals(1, state.categoriesCount)
        assertEquals(1, state.downloadedCount)
        // Chapitre importe mais non lu et telecharge -> compte comme TO_SEND (badge = toSend + toFetch).
        assertEquals(1, state.toSendCount)
    }
}
