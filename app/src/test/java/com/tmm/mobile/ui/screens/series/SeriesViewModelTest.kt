package com.tmm.mobile.ui.screens.series

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.flatten
import com.tmm.mobile.mihon.MihonNaming
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.QualityAlert
import com.tmm.mobile.ui.components.StatusTone
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

// PLAN_GEMINI.md 7.3 (E10) : SeriesViewModel doit reproduire le meme SeriesState que la maquette
// (Series.dc.html) a partir de Room - un chapitre a envoyer, un a jour avec des alertes de qualite
// et un trou de numerotation suffisent a couvrir buildChapterJoinRows/deriveSeriesSummary.missing.
@RunWith(RobolectricTestRunner::class)
class SeriesViewModelTest {
    private val env = WorkerTestEnvironment()
    private val seriesKey = "blue-lock"
    private val sourceName = "MangaSource"
    private val title = "Blue Lock"

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun mihonPathFor(number: Int, url: String): MihonPath = MihonPath(
        MihonNaming.getSourceDirName(sourceName),
        MihonNaming.getMangaDirName(title),
        MihonNaming.getChapterDirName("Chapitre $number", null, url),
    )

    private suspend fun seedSeries() {
        env.db.seriesDao().upsert(
            SeriesEntity(seriesKey = seriesKey, sourceId = "1", url = "https://x/$seriesKey", title = title, author = null, status = 0, thumbnailUrl = null, sourceName = sourceName, dateAdded = 0L, mangaType = "manga"),
        )
    }

    private suspend fun addCatalogChapter(number: Int): MihonPath {
        val url = "https://x/$seriesKey-$number"
        val chapterKey = "$seriesKey-$number"
        env.db.chapterDao().upsertAll(listOf(ChapterEntity(chapterKey = chapterKey, seriesKey = seriesKey, url = url, name = "Chapitre $number", number = number.toFloat(), scanlator = null, read = false, dateUpload = 0L)))
        return mihonPathFor(number, url)
    }

    // Chapitre 1 : catalogue + telecharge, aucun job/index -> TO_SEND (deriveStatus).
    private suspend fun seedToSendChapter(number: Int = 1) {
        val mihonPath = addCatalogChapter(number)
        val chapterKey = "$seriesKey-$number"
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = mihonPath.flatten(), sourceDir = mihonPath.source, seriesDir = mihonPath.series, chapterDir = mihonPath.chapter,
                documentUri = "content://x/$number", isCbz = false, sizeBytes = 10L, lastModified = 0L, pageCount = 5,
                seriesKey = seriesKey, chapterKey = chapterKey, number = number.toFloat(),
            ),
        )
    }

    // Chapitre a jour (localFr) avec une page encore en VO (alerte de qualite).
    private suspend fun seedUpToDateChapterWithAlert(number: Int) {
        val mihonPath = addCatalogChapter(number)
        env.db.localFrChapterDao().upsert(
            LocalFrChapterEntity(localPath = "content://fr/$number", folderName = title, fileName = "$number.cbz", documentUri = "content://fr/$number", lastModified = 0L, jobId = null, mihonPath = mihonPath.flatten()),
        )
        env.db.chapterQualityDao().upsert(
            ChapterQualityEntity(
                mihonPath = mihonPath.flatten(), totalPages = 20, translatedBubbles = 40, untranslatedBubbles = 1,
                sfxKept = 2, verifiedAt = "2026-01-24T10:00:00Z", alerts = listOf(QualityAlert(kind = "source_text", page = 3)), jobId = "job-x",
            ),
        )
    }

    @Test fun state_toSendChapter_hasSendTone() = runTest {
        seedSeries()
        seedToSendChapter(number = 1)

        val vm = SeriesViewModel(env.container, seriesKey)
        val state = vm.state.first { it.chapters.isNotEmpty() }

        assertEquals(title, state.title)
        val chapter = state.chapters.single { it.number == 1 }
        assertEquals(StatusTone.Send, chapter.kind)
        assertEquals(1, state.onPhoneCount)
        assertEquals(0, state.onPcCount)
    }

    @Test fun state_upToDateChapter_showsRemainingVoPagesAndStats() = runTest {
        seedSeries()
        seedUpToDateChapterWithAlert(number = 2)

        val vm = SeriesViewModel(env.container, seriesKey)
        val state = vm.state.first { it.chapters.isNotEmpty() }

        val chapter = state.chapters.single { it.number == 2 }
        assertEquals(StatusTone.Ok, chapter.kind)
        assertEquals("À jour · 1 page restée en VO", chapter.statusLabel)
        assertEquals(19, chapter.stats?.pagesTranslated)
        assertEquals(1, state.frCount)
    }

    @Test fun state_missingChapterNumber_becomesGapRow() = runTest {
        seedSeries()
        seedToSendChapter(number = 1)
        seedUpToDateChapterWithAlert(number = 3)

        val vm = SeriesViewModel(env.container, seriesKey)
        val state = vm.state.first { it.chapters.size >= 3 }

        assertEquals(2, state.gapChapter?.number)
        assertEquals(StatusTone.Gap, state.gapChapter?.kind)
    }

    @Test fun dismissGap_persistsIgnoredGap_andRemovesItFromMissing() = runTest {
        seedSeries()
        seedToSendChapter(number = 1)
        seedUpToDateChapterWithAlert(number = 3)

        val vm = SeriesViewModel(env.container, seriesKey)
        vm.state.first { it.gapChapter != null }

        vm.onEvent(SeriesEvent.DismissGap)
        val state = vm.state.first { it.gapChapter == null }

        assertNull(state.gapChapter)
        assertEquals(listOf(2), env.db.ignoredGapDao().observeForSeries(seriesKey).first().map { it.number })
    }

    @Test fun sendToPc_createsJobForSelectedToSendChapter() = runTest {
        seedSeries()
        seedToSendChapter(number = 1)

        val vm = SeriesViewModel(env.container, seriesKey)
        vm.state.first { it.chapters.isNotEmpty() }
        vm.onEvent(SeriesEvent.ToggleChapter(1))
        vm.state.first { 1 in it.selected }

        vm.onEvent(SeriesEvent.SendToPc)
        val state = vm.state.first { it.toast != null }

        assertEquals(SeriesToast.Sent(1), state.toast)
        assertEquals(1, env.db.jobDao().observeAll().first().size)
    }
}
