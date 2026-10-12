package com.tmm.mobile.ui.screens.series

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.ChapterQualityEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.flatten
import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.mihon.MihonNaming
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.QualityAlert
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// SeriesViewModel (maquette v2, ecran 3) : etat construit depuis Room, actions A (traduire / copier
// sans traduire) et B option 1 (le suivi envoie aussi les chapitres deja telecharges a traduire).
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

    // Catalogue + telecharge, aucun job/index -> TO_SEND, affiche « À traduire ».
    private suspend fun seedToTranslateChapter(number: Int = 1) {
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

    // Chapitre installe (localFr) avec une page encore en VO (alerte de qualite).
    private suspend fun seedInMihonChapter(number: Int) {
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

    @Test fun state_toTranslateChapter_feedsBigButtonAndProgress() = runTest {
        seedSeries()
        seedToTranslateChapter(number = 1)

        val vm = SeriesViewModel(env.container, seriesKey)
        val state = vm.state.first { it.rows.isNotEmpty() }

        assertEquals(title, state.title)
        assertEquals(ChapterKind.ToTranslate, state.rows.single { it.number == 1 }.kind)
        assertEquals(listOf(1), state.toTranslateNumbers)
        assertEquals(1, state.progress.toTranslate)
        assertEquals(1, state.seriesCounts.translate)
        assertFalse(state.followed)
    }

    @Test fun state_inMihonChapter_carriesStats() = runTest {
        seedSeries()
        seedInMihonChapter(number = 2)

        val vm = SeriesViewModel(env.container, seriesKey)
        val state = vm.state.first { it.rows.isNotEmpty() }

        val row = state.rows.single { it.number == 2 }
        assertEquals(ChapterKind.InMihon, row.kind)
        assertEquals(19, row.stats?.pagesTranslated)
        assertEquals(1, state.progress.inMihon)
    }

    @Test fun state_missingChapterNumber_becomesMissingRow() = runTest {
        seedSeries()
        seedToTranslateChapter(number = 1)
        seedInMihonChapter(number = 3)

        val vm = SeriesViewModel(env.container, seriesKey)
        val state = vm.state.first { it.rows.size >= 3 }

        assertEquals(listOf(2), state.missing)
        assertEquals(ChapterKind.Missing, state.rows.single { it.number == 2 }.kind)
    }

    @Test fun dismissGap_persistsIgnoredGap() = runTest {
        seedSeries()
        seedToTranslateChapter(number = 1)
        seedInMihonChapter(number = 3)

        val vm = SeriesViewModel(env.container, seriesKey)
        vm.state.first { it.missing.isNotEmpty() }

        vm.onEvent(SeriesEvent.DismissGap)
        vm.state.first { it.missing.isEmpty() }

        assertEquals(listOf(2), env.db.ignoredGapDao().observeForSeries(seriesKey).first().map { it.number })
    }

    @Test fun translateAll_createsTranslatingJob() = runTest {
        seedSeries()
        seedToTranslateChapter(number = 1)

        val vm = SeriesViewModel(env.container, seriesKey)
        vm.state.first { it.toTranslateNumbers.isNotEmpty() }
        vm.onEvent(SeriesEvent.TranslateAll)
        val state = vm.state.first { it.toast != null }

        assertEquals(SeriesToast.Translated(1), state.toast)
        assertTrue(env.db.jobDao().observeAll().first().single().translate)
    }

    // A : « Copier sur le PC sans traduire » envoie translate = false.
    @Test fun copyToPc_createsJobWithoutTranslation() = runTest {
        seedSeries()
        seedToTranslateChapter(number = 1)

        val vm = SeriesViewModel(env.container, seriesKey)
        vm.state.first { it.rows.isNotEmpty() }
        vm.onEvent(SeriesEvent.OpenSeriesMore)
        vm.state.first { it.sheet == SeriesSheet.More(allSeries = true) }
        vm.onEvent(SeriesEvent.CopyToPc)
        val state = vm.state.first { it.toast != null }

        assertEquals(SeriesToast.Copied(1), state.toast)
        assertFalse(env.db.jobDao().observeAll().first().single().translate)
    }

    // B, option 1 : la feuille annonce le rattrapage, la confirmation l'envoie et active le suivi.
    @Test fun follow_sendsAlreadyDownloadedChapters_undoRevertsIt() = runTest {
        seedSeries()
        seedToTranslateChapter(number = 1)
        seedInMihonChapter(number = 2)

        val vm = SeriesViewModel(env.container, seriesKey)
        vm.state.first { it.rows.size >= 2 }
        vm.onEvent(SeriesEvent.SetFollow(true))
        assertEquals(SeriesSheet.Follow(listOf(1)), vm.state.first { it.sheet is SeriesSheet.Follow }.sheet)

        vm.onEvent(SeriesEvent.ConfirmFollow)
        val followed = vm.state.first { it.followed && it.toast != null }
        assertEquals(SeriesToast.Followed(1), followed.toast)
        assertEquals(listOf(1), followed.sentOnFollow)
        assertEquals(1, env.db.jobDao().observeAll().first().size)

        vm.onEvent(SeriesEvent.UndoFollow)
        val undone = vm.state.first { !it.followed }
        assertEquals(emptyList<Int>(), undone.sentOnFollow)
        assertEquals(false, env.db.seriesSettingsDao().observe(seriesKey).first()?.autoTranslate)
    }

    @Test fun confirmDelete_removesLocalFr_andLeavesSelectionMode() = runTest {
        seedSeries()
        seedInMihonChapter(number = 2)
        val root = kotlin.io.path.createTempDirectory("mihon").toFile()
        val frFile = java.io.File(root, "2.cbz").apply { writeBytes(byteArrayOf(1)) }
        env.db.localFrChapterDao().upsert(env.db.localFrChapterDao().observeAll().first().single().copy(documentUri = frFile.absolutePath))
        env.container.mihonFs = FakeMihonFs(root)

        val vm = SeriesViewModel(env.container, seriesKey)
        vm.state.first { it.rows.isNotEmpty() }
        vm.onEvent(SeriesEvent.StartSelection)
        vm.onEvent(SeriesEvent.ToggleChapter(2))
        vm.state.first { it.selectionToDelete == 1 }
        vm.onEvent(SeriesEvent.OpenSelectionMore)
        vm.state.first { it.sheet is SeriesSheet.More }
        vm.onEvent(SeriesEvent.RequestDelete)
        assertEquals(SeriesSheet.Delete(chapterCount = 1, skippedCount = 0), vm.state.first { it.sheet is SeriesSheet.Delete }.sheet)

        vm.onEvent(SeriesEvent.ConfirmDelete)
        val state = vm.state.first { it.toast != null && it.progress.inMihon == 0 }

        assertEquals(SeriesToast.Deleted(1), state.toast)
        assertEquals(SeriesSheet.None, state.sheet)
        assertEquals(emptySet<Int>(), state.selected)
        assertFalse(state.selecting)
        assertFalse(frFile.exists())
        root.deleteRecursively()
    }
}
