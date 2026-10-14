package com.tmm.mobile.mihon

import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

    // Une serie de la source Locale de Mihon ajoutee a la bibliotheque n'apparait pas dans Series.
    @Test fun localSourceSeries_isNotImported() = runTest {
        val backup = MihonBackup(
            backupManga = listOf(
                MihonBackupManga(source = 42L, url = "/manga/1", title = "En ligne", chapters = listOf(MihonBackupChapter(url = "/c/1"))),
                MihonBackupManga(source = MIHON_LOCAL_SOURCE_ID, url = "Titre FR", title = "Titre FR", chapters = listOf(MihonBackupChapter(url = "Titre FR/c0001.cbz"))),
            ),
            backupSources = listOf(MihonBackupSource(name = "Source", sourceId = 42L)),
        )

        BackupImporter(env.db).import(backup)

        assertEquals(listOf("En ligne"), env.db.seriesDao().observeAll().first().map { it.title })
        assertEquals(1, env.db.chapterDao().observeForSeries("42|/manga/1").first().size)
        assertEquals(0, env.db.chapterDao().observeForSeries("0|Titre FR").first().size)
    }
}
