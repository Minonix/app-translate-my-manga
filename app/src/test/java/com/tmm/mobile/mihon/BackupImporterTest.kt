package com.tmm.mobile.mihon

import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupImporterTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    // Retour utilisateur : une serie de la « Source locale » de Mihon (dossier local/, ou TMM installe
    // les traductions) ajoutee a la bibliotheque apparaissait dans TMM comme une serie de plus.
    @Test
    fun import_skipsMihonLocalSourceSeries() = runTest {
        val backup = MihonBackup(
            backupManga = listOf(
                MihonBackupManga(
                    source = 1L, url = "/manga/1", title = "Kagurabachi", categories = listOf(0L),
                    chapters = listOf(MihonBackupChapter(url = "/manga/1/ch1", name = "Chapitre 1", chapterNumber = 1f)),
                ),
                MihonBackupManga(
                    source = BackupImporter.MIHON_LOCAL_SOURCE_ID, url = "Kagurabachi (FR)", title = "Kagurabachi (FR)",
                    categories = listOf(0L),
                    chapters = listOf(MihonBackupChapter(url = "Kagurabachi (FR)/ch1.cbz", name = "ch1", chapterNumber = 1f)),
                ),
            ),
            backupCategories = listOf(MihonBackupCategory(name = "En cours", order = 0)),
            backupSources = listOf(
                MihonBackupSource(name = "MangaSource", sourceId = 1L),
                MihonBackupSource(name = "Local source", sourceId = 0L),
            ),
        )

        BackupImporter(env.db).import(backup)

        assertEquals(listOf("Kagurabachi"), env.db.seriesDao().observeAll().first().map { it.title })
        assertNull(env.db.chapterDao().get("0|Kagurabachi (FR)|Kagurabachi (FR)/ch1.cbz"))
    }
}
