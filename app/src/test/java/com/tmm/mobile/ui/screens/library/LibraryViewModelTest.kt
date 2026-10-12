package com.tmm.mobile.ui.screens.library

import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.SeriesSettingsEntity
import com.tmm.mobile.data.JobMode
import com.tmm.mobile.data.flatten
import com.tmm.mobile.mihon.MihonNaming
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.screens.series.SeriesSheet
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.2 (E10) : verifie que LibraryViewModel agrege series/categories/chapitres reels
// (Room) en LibraryState, sur un jeu de donnees representatif (pas la maquette entiere - un
// "Envoyer" et un "A jour" suffisent pour couvrir SeriesSummary.badge()/deriveStatus()).
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private suspend fun seedSeriesWithOneChapterToSend(seriesKey: String, title: String, categoryOrder: Long? = null) {
        env.db.seriesDao().upsert(
            SeriesEntity(
                seriesKey = seriesKey, sourceId = "1", url = "https://x/$seriesKey", title = title, author = null,
                status = 0, thumbnailUrl = null, sourceName = "MangaSource", dateAdded = 0L, mangaType = "manga",
            ),
        )
        val chapterKey = "$seriesKey-1"
        env.db.chapterDao().upsertAll(
            listOf(ChapterEntity(chapterKey = chapterKey, seriesKey = seriesKey, url = "https://x/$chapterKey", name = "Chapitre 1", number = 1f, scanlator = null, read = false, dateUpload = 0L)),
        )
        val mihonPath = MihonPath(MihonNaming.getSourceDirName("MangaSource"), MihonNaming.getMangaDirName(title), MihonNaming.getChapterDirName("Chapitre 1", null, "https://x/$chapterKey"))
        env.db.downloadedChapterDao().upsert(
            DownloadedChapterEntity(
                mihonPath = mihonPath.flatten(), sourceDir = mihonPath.source, seriesDir = mihonPath.series, chapterDir = mihonPath.chapter,
                documentUri = "content://x", isCbz = false, sizeBytes = 10L, lastModified = 0L, pageCount = 5,
                seriesKey = seriesKey, chapterKey = chapterKey, number = 1f,
            ),
        )
        if (categoryOrder != null) env.db.seriesCategoryDao().insertAll(listOf(SeriesCategoryEntity(seriesKey, categoryOrder)))
    }

    @Test fun state_reflectsRealSeriesAndBadge_toSend() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)

        val state = vm.state.first { it.allSeries.isNotEmpty() }
        val series = state.allSeries.single()
        assertEquals("Blue Lock", series.title)
        assertEquals("blue-lock", series.seriesKey)
        assertEquals(1, series.toSend)
        assertEquals(StatusTone.Send, series.tone)
        assertEquals("1 à envoyer", series.badge)
    }

    @Test fun state_upToDateSeries_hasOkBadgeAndFrCount() = runTest {
        val seriesKey = "dandadan"
        env.db.seriesDao().upsert(
            SeriesEntity(seriesKey = seriesKey, sourceId = "1", url = "https://x/$seriesKey", title = "Dandadan", author = null, status = 0, thumbnailUrl = null, sourceName = "MangaSource", dateAdded = 0L, mangaType = "manga"),
        )
        val chapterKey = "$seriesKey-1"
        env.db.chapterDao().upsertAll(
            listOf(ChapterEntity(chapterKey = chapterKey, seriesKey = seriesKey, url = "https://x/$chapterKey", name = "Chapitre 1", number = 1f, scanlator = null, read = false, dateUpload = 0L)),
        )
        val mihonPath = MihonPath(MihonNaming.getSourceDirName("MangaSource"), MihonNaming.getMangaDirName("Dandadan"), MihonNaming.getChapterDirName("Chapitre 1", null, "https://x/$chapterKey"))
        env.db.localFrChapterDao().upsert(
            LocalFrChapterEntity(localPath = "content://fr/1", folderName = "Dandadan", fileName = "1.cbz", documentUri = "content://fr/1", lastModified = 0L, jobId = null, mihonPath = mihonPath.flatten()),
        )

        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        val state = vm.state.first { it.allSeries.isNotEmpty() }
        val series = state.allSeries.single()
        assertEquals(1, series.frChapters)
        assertEquals(0, series.toSend)
        assertEquals(StatusTone.Ok, series.tone)
    }

    @Test fun categoryFilter_countsAndFiltersBySeriesCategoryLink() = runTest {
        env.db.categoryDao().upsertAll(listOf(CategoryEntity(order = 1L, name = "En cours")))
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock", categoryOrder = 1L)
        seedSeriesWithOneChapterToSend("chainsaw-man", "Chainsaw Man")

        val vm = LibraryViewModel(env.container, initialCategoryOrder = 1L)
        val state = vm.state.first { it.allSeries.size == 2 && it.category == "En cours" }
        assertEquals(listOf("Tous" to 2, "En cours" to 1), state.categories)
    }

    /** Passe le chapitre unique de la serie en VF installee ; `originalOnPc` : l'original est encore sur le PC. */
    private suspend fun markUpToDate(seriesKey: String, title: String, originalOnPc: Boolean) {
        val chapterKey = "$seriesKey-1"
        val mihonPath = MihonPath(MihonNaming.getSourceDirName("MangaSource"), MihonNaming.getMangaDirName(title), MihonNaming.getChapterDirName("Chapitre 1", null, "https://x/$chapterKey")).flatten()
        env.db.localFrChapterDao().upsert(
            LocalFrChapterEntity(localPath = "content://fr/$seriesKey", folderName = title, fileName = "1.cbz", documentUri = "content://fr/$seriesKey", lastModified = 0L, jobId = null, mihonPath = mihonPath),
        )
        if (originalOnPc) env.db.pcIndexChapterDao().insertAll(listOf(PcIndexChapterEntity(mihonPath, original = true, fr = true)))
    }

    private suspend fun openMoreFor(vm: LibraryViewModel, vararg keys: String): LibraryActions {
        vm.state.first { s -> keys.all { k -> s.allSeries.any { it.seriesKey == k } } }
        keys.forEach { vm.onEvent(LibraryEvent.ToggleSelected(seriesKey = it)) }
        vm.state.first { it.selected.size == keys.size }
        vm.onEvent(LibraryEvent.OpenMore)
        return vm.state.first { it.actions != null }.actions!!
    }

    // « Corriger les oublis » ne compte que les chapitres en VF dont l'original est sur le PC
    // (canReprocess), pas tous les chapitres des series choisies (le « 1122 au lieu de 1 »).
    @Test fun openMore_countsWithSeriesPredicates() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        seedSeriesWithOneChapterToSend("dandadan", "Dandadan")
        markUpToDate("dandadan", "Dandadan", originalOnPc = true)
        seedSeriesWithOneChapterToSend("frieren", "Frieren")
        markUpToDate("frieren", "Frieren", originalOnPc = false)
        env.db.seriesSettingsDao().upsert(SeriesSettingsEntity("frieren", autoTranslate = true))
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        vm.state.first { it.details["frieren"]?.followed == true }

        val actions = openMoreFor(vm, "blue-lock", "dandadan", "frieren")

        assertEquals(1, actions.counts.verify)
        assertEquals(1, actions.counts.retranslate)
        assertEquals(1, actions.counts.copy)
        assertEquals(0, actions.counts.install)
        assertEquals(2, actions.toFollow)
        assertEquals(3, actions.deletable)
    }

    // A : « Copier sur le PC sans traduire » part avec translate = false.
    @Test fun copyToPc_createsUntranslatedJobs() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        openMoreFor(vm, "blue-lock")

        vm.onEvent(LibraryEvent.CopyToPc)
        val state = vm.state.first { it.toast != null }

        assertEquals(LibraryToast.Copied(1), state.toast)
        // L'action ferme la feuille « Que faire ? » et termine la selection.
        assertEquals(false, state.moreOpen)
        assertEquals(false, state.picking)
        assertEquals(false, env.db.jobDao().observeAll().first().single().translate)
    }

    @Test fun verify_sendsOnlyReprocessableChapters() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        seedSeriesWithOneChapterToSend("dandadan", "Dandadan")
        markUpToDate("dandadan", "Dandadan", originalOnPc = true)
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        openMoreFor(vm, "blue-lock", "dandadan")

        vm.onEvent(LibraryEvent.Verify)
        val state = vm.state.first { it.toast != null }

        assertEquals(LibraryToast.Verified(1), state.toast)
        val job = env.db.jobDao().observeAll().first().single()
        assertEquals("dandadan", job.seriesKey)
        assertEquals(JobMode.VERIFY, job.mode)
    }

    // B, option 1 : suivre plusieurs series envoie aussi leurs chapitres deja telecharges.
    @Test fun follow_enablesAutoTranslateAndSendsDownloadedChapters() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        seedSeriesWithOneChapterToSend("kagurabachi", "Kagurabachi")
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        openMoreFor(vm, "blue-lock", "kagurabachi")

        vm.onEvent(LibraryEvent.Follow)
        val state = vm.state.first { it.toast != null }

        assertEquals(LibraryToast.Followed(seriesCount = 2, chapterCount = 2), state.toast)
        assertEquals(setOf("blue-lock", "kagurabachi"), env.db.seriesSettingsDao().observeAutoTranslateSeriesKeys().first().toSet())
        assertEquals(2, env.db.jobDao().observeAll().first().count { it.translate })
    }

    @Test fun delete_removesLocalFrOfSelectedSeries() = runTest {
        seedSeriesWithOneChapterToSend("dandadan", "Dandadan")
        markUpToDate("dandadan", "Dandadan", originalOnPc = false)
        val vm = LibraryViewModel(env.container, initialCategoryOrder = null)
        openMoreFor(vm, "dandadan")

        vm.onEvent(LibraryEvent.RequestDelete)
        assertEquals(SeriesSheet.Delete(1, 0), vm.state.first { it.delete != null }.delete)
        vm.onEvent(LibraryEvent.ConfirmDelete)
        val state = vm.state.first { it.toast != null }

        assertEquals(1, (state.toast as LibraryToast.Deleted).chapterCount)
        assertEquals(null, state.delete)
    }

    @Test fun followedAndSecondaryCategory_areLoadedFromRoom() = runTest {
        env.db.categoryDao().upsertAll(listOf(CategoryEntity(1L, "En cours"), CategoryEntity(2L, "Favoris")))
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock", 1L)
        env.db.seriesCategoryDao().insertAll(listOf(SeriesCategoryEntity("blue-lock", 2L)))
        env.db.seriesSettingsDao().upsert(com.tmm.mobile.data.db.SeriesSettingsEntity("blue-lock", true))
        val vm = LibraryViewModel(env.container, initialCategoryOrder = 2L)
        val state = vm.state.first { it.category == "Favoris" && it.details["blue-lock"]?.followed == true }
        assertEquals(listOf("blue-lock"), state.copy(view = LibraryViewFilter.Followed).visibleSeries.map { it.seriesKey })
    }

    // Bouton "+" de la File : LibraryRoute(picking = true) ouvre l'ecran directement en mode selection.
    @Test fun initialPicking_startsInPickingMode() = runTest {
        seedSeriesWithOneChapterToSend("blue-lock", "Blue Lock")
        val vm = LibraryViewModel(env.container, initialPicking = true)
        // Attend la liste (calcul de LibraryCache sur IO) avant tearDown, comme les autres tests :
        // sinon ce calcul tombe sur la base fermee et l'exception echoue le test suivant.
        val state = vm.state.first { it.allSeries.isNotEmpty() }
        assertEquals(true, state.picking)
        assertEquals(emptySet<String>(), state.selected)
    }
}
