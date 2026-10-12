package com.tmm.mobile.ui.screens.settings

import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.mihon.MihonBackup
import com.tmm.mobile.mihon.MihonBackupCategory
import com.tmm.mobile.mihon.MihonBackupChapter
import com.tmm.mobile.mihon.MihonBackupManga
import com.tmm.mobile.mihon.MihonBackupSource
import com.tmm.mobile.workers.TestClock
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// « Relire la sauvegarde » (Reglages) rejoue un import complet (importReimportSequence) sans
// bouton "Importer" manuel (PLAN_GEMINI.md 5.21).
@RunWith(RobolectricTestRunner::class)
class BackupReimportTest {
    private val env = WorkerTestEnvironment()

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    @OptIn(ExperimentalSerializationApi::class)
    private fun tachibkBytes(): ByteArray {
        val backup = MihonBackup(
            backupManga = listOf(
                MihonBackupManga(
                    source = 1L, url = "/manga/1", title = "Kagurabachi", categories = listOf(0L),
                    chapters = listOf(MihonBackupChapter(url = "/manga/1/ch1", name = "Chapitre 1", chapterNumber = 1f)),
                ),
            ),
            backupCategories = listOf(MihonBackupCategory(name = "En cours", order = 0)),
            backupSources = listOf(MihonBackupSource(name = "MangaSource", sourceId = 1L)),
        )
        val bytes = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup)
        return ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(bytes) } }.toByteArray()
    }

    @Test
    fun importReimportSequence_readsNewestTachibkAndUpdatesBackupImport() = runTest {
        val root = tmp.newFolder("mihon-tree")
        val autobackup = File(root, "autobackup").apply { mkdirs() }
        File(autobackup, "com.mihon_older.tachibk").apply {
            writeBytes(tachibkBytes())
            setLastModified(1_000L)
        }
        val newest = File(autobackup, "com.mihon_newest.tachibk").apply {
            writeBytes(tachibkBytes())
            setLastModified(2_000L)
        }

        val fs = FakeMihonFs(root)
        val steps = mutableListOf<BackupImportStep>()
        importReimportSequence(fs, root.absolutePath, env.db, TestClock()) { steps.add(it) }

        assertEquals(
            listOf(
                BackupImportStep.ReadingFile,
                BackupImportStep.Decompressing,
                BackupImportStep.ReadingSeries,
                BackupImportStep.ReadingCategories,
                BackupImportStep.Comparing,
            ),
            steps,
        )
        val imported = env.db.backupImportDao().observe().first()
        assertEquals(newest.name, imported?.name)
        assertEquals(1, env.db.seriesDao().observeAll().first().size)
        assertTrue(env.db.seriesDao().observeAll().first().any { it.title == "Kagurabachi" })
    }
}
