package com.tmm.mobile.ui.screens.translate

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Maquette v2, écran 6 : la feuille « Traduire » liste les séries ayant au moins un chapitre TO_SEND,
// précoche tout depuis l'Accueil, rien depuis Activité, et envoie translate = true ou false (point A).
@RunWith(RobolectricTestRunner::class)
class TranslateViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    private val vms = mutableListOf<TranslateViewModel>()

    // Le flux d'etat tourne sur Dispatchers.Default dans viewModelScope : l'arreter avant de fermer la base,
    // sinon une requete en vol leve « already-closed » pendant le test suivant (UncaughtExceptionsBeforeTest).
    @After
    fun tearDown() {
        runBlocking { vms.forEach { withTimeoutOrNull(5_000) { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        env.tearDown()
    }

    private fun newVm() = TranslateViewModel(env.container).also { vms += it }

    private fun series(key: String, title: String) = SeriesEntity(
        seriesKey = key, sourceId = "src", url = "url/$key", title = title, author = null,
        status = 0, thumbnailUrl = null, sourceName = "Mihon", dateAdded = 0L, mangaType = null,
    )

    private fun downloaded(seriesKey: String, chapterDir: String, number: Float, pages: Int) = DownloadedChapterEntity(
        mihonPath = "Mihon/$seriesKey/$chapterDir", sourceDir = "Mihon", seriesDir = seriesKey, chapterDir = chapterDir,
        documentUri = "doc://$seriesKey/$chapterDir", isCbz = true, sizeBytes = 1L, lastModified = 0L, pageCount = pages,
        seriesKey = seriesKey, chapterKey = null, number = number,
    )

    private suspend fun seed() {
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren"), series("one-piece", "One Piece")))
        // Frieren : 1 chapitre TO_SEND. One Piece : 2, donc en tête (tri décroissant).
        env.db.downloadedChapterDao().upsert(downloaded("frieren", "140", 140f, 20))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1121", 1121f, 19))
        env.db.downloadedChapterDao().upsert(downloaded("one-piece", "1120", 1120f, 18))
    }

    private suspend fun open(request: TranslateRequest): TranslateViewModel {
        seed()
        val vm = newVm()
        vm.onEvent(TranslateEvent.Open(request))
        vm.state.first { it.series.size == 2 }
        return vm
    }

    @Test
    fun state_listsSeriesWithToSendChapters_sortedByCountThenNumber() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        val state = vm.state.value

        assertEquals(listOf("One Piece", "Frieren"), state.series.map { it.title })
        assertEquals(listOf("1120", "1121"), state.series[0].chapters.map { it.numberLabel })
        assertEquals(3, state.totalCount)
        assertEquals(3, state.selectedCount)
    }

    @Test
    fun openFromActivity_checksNothing_thenToggleSeriesChecksIt() = runTest {
        val vm = open(TranslateRequest(preselectAll = false))
        assertEquals(0, vm.state.value.selectedCount)

        vm.onEvent(TranslateEvent.ToggleSeries("one-piece"))
        val state = vm.state.first { it.selectedCount == 2 }
        assertEquals(SeriesCheck.All, state.checkOf(state.series[0]))
        assertEquals(SeriesCheck.None, state.checkOf(state.series[1]))
    }

    @Test
    fun toggleChapter_makesSeriesPartial_andToggleSeriesCompletesIt() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))

        vm.onEvent(TranslateEvent.ToggleChapter("Mihon/one-piece/1120"))
        val partial = vm.state.first { it.selectedCount == 2 }
        assertEquals(SeriesCheck.Partial, partial.checkOf(partial.series[0]))

        vm.onEvent(TranslateEvent.ToggleSeries("one-piece"))
        val full = vm.state.first { it.selectedCount == 3 }
        assertEquals(SeriesCheck.All, full.checkOf(full.series[0]))
    }

    @Test
    fun updateQuery_isReadBackSynchronously_andResetOnOpen() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        // Chaque frappe doit se relire tout de suite, sans attendre le recalcul de `state`.
        "Garden".forEachIndexed { i, _ ->
            vm.onEvent(TranslateEvent.UpdateQuery("Garden".substring(0, i + 1)))
            assertEquals("Garden".substring(0, i + 1), vm.query.value)
        }
        vm.onEvent(TranslateEvent.Open(TranslateRequest(preselectAll = true)))
        assertEquals("", vm.query.value)
    }

    @Test
    fun seriesKeys_restrictsListToChosenSeries() = runTest {
        seed()
        val vm = newVm()
        vm.onEvent(TranslateEvent.Open(TranslateRequest(preselectAll = true, seriesKeys = setOf("frieren"))))
        val state = vm.state.first { it.series.isNotEmpty() }
        assertEquals(listOf("Frieren"), state.series.map { it.title })
    }

    @Test
    fun sendTranslate_createsJobsForSelectedChaptersOnly_withTranslateTrue() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        vm.onEvent(TranslateEvent.ToggleSeries("frieren"))
        vm.state.first { it.selectedCount == 2 }

        vm.onEvent(TranslateEvent.Send(translate = true))
        val done = vm.state.first { it.phase is TranslatePhase.Done }.phase as TranslatePhase.Done

        assertEquals(TranslatePhase.Done(2, translate = true), done)
        val jobs = env.db.jobDao().observeAll().first()
        assertEquals(2, jobs.size)
        assertTrue(jobs.all { it.seriesKey == "one-piece" && it.translate })
    }

    @Test
    fun sendCopy_createsJobsWithTranslateFalse() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))

        vm.onEvent(TranslateEvent.Send(translate = false))
        vm.state.first { it.phase is TranslatePhase.Done }

        val jobs = env.db.jobDao().observeAll().first()
        assertEquals(3, jobs.size)
        assertTrue(jobs.none { it.translate })
    }

    // Avant : un echec d'ecriture en base ramenait la feuille a Idle sans rien dire, l'utilisateur
    // croyait l'envoi parti. La feuille passe en Failed, garde la selection et permet de reessayer.
    @Test
    fun sendFailure_isShown_andRetryWorks() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        val sql = env.db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER refuse_job BEFORE INSERT ON job BEGIN SELECT RAISE(ABORT, 'disque plein'); END")
        vm.onEvent(TranslateEvent.Send(translate = true))
        val failed = vm.state.first { it.phase == TranslatePhase.Failed }
        assertEquals(3, failed.selectedCount)

        sql.execSQL("DROP TRIGGER refuse_job")
        vm.onEvent(TranslateEvent.Send(translate = true))
        assertEquals(TranslatePhase.Done(3, translate = true), vm.state.first { it.phase is TranslatePhase.Done }.phase)
    }

    // Garde Busy : rouvrir la feuille pendant un envoi ne doit pas repasser a Idle, sinon un second
    // « Traduire » partirait avant la fin du premier. La base est bloquee par une transaction tenue sur
    // un autre fil, pour que l'envoi reste en cours pendant la reouverture.
    @Test
    fun reopenWhileSending_keepsBusyPhase() = runTest {
        val vm = open(TranslateRequest(preselectAll = true))
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            env.db.runInTransaction {
                locked.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }.apply { start() }
        try {
            assertTrue(locked.await(10, TimeUnit.SECONDS))
            vm.onEvent(TranslateEvent.Send(translate = true))
            vm.state.first { it.phase == TranslatePhase.Busy }

            // Reouverture depuis Activite : rien de coche, mais la phase reste Busy.
            vm.onEvent(TranslateEvent.Open(TranslateRequest(preselectAll = false)))
            val reopened = vm.state.first { it.selectedCount == 0 }
            assertEquals(TranslatePhase.Busy, reopened.phase)
        } finally {
            release.countDown()
            holder.join(10_000)
        }
        assertEquals(TranslatePhase.Done(3, translate = true), vm.state.first { it.phase is TranslatePhase.Done }.phase)
    }

    // Meme serie chez deux scantrads (cas reel de The Garden of Secrets) : Rinko 29-31, Nyx 29-30.
    private suspend fun openDupes(request: TranslateRequest): TranslateViewModel {
        env.db.seriesDao().upsertAll(listOf(series("garden", "Garden")))
        listOf("Rinko Comics_Chapter 29_aaaaaa.cbz" to 29f, "Rinko Comics_Chapter 30_bbbbbb.cbz" to 30f,
            "Rinko Comics_Chapter 31_cccccc.cbz" to 31f, "Nyx Scans_Chapter 29_dddddd.cbz" to 29f,
            "Nyx Scans_Chapter 30_eeeeee.cbz" to 30f, "Extra_ffffff.cbz" to null)
            .forEach { (dir, n) -> env.db.downloadedChapterDao().upsert(downloaded("garden", dir, n ?: 0f, 10).copy(number = n)) }
        val vm = newVm()
        vm.onEvent(TranslateEvent.Open(request))
        vm.state.first { it.totalCount == 6 }
        return vm
    }

    private fun TranslateState.selectedDirs() =
        series.flatMap { it.chapters }.filter { it.mihonPath in selected }.map { it.mihonPath.substringAfterLast('/') }.toSet()

    @Test
    fun duplicates_onlyPreferredScanlatorPreselected_withScanlatorShown() = runTest {
        val vm = openDupes(TranslateRequest(preselectAll = true))
        val state = vm.state.value
        val garden = state.series.single()

        assertEquals(
            setOf("Rinko Comics_Chapter 29_aaaaaa.cbz", "Rinko Comics_Chapter 30_bbbbbb.cbz",
                "Rinko Comics_Chapter 31_cccccc.cbz", "Extra_ffffff.cbz"),
            state.selectedDirs(),
        )
        assertEquals(SeriesCheck.All, state.checkOf(garden))
        assertEquals(2, garden.duplicateCount)
        val nyx29 = garden.chapters.single { it.mihonPath.endsWith("Nyx Scans_Chapter 29_dddddd.cbz") }
        assertEquals("Nyx Scans", nyx29.scanlator)
        assertTrue(nyx29.duplicate)
        // Sans numero, pas de dedoublonnage.
        assertFalse(garden.chapters.single { it.mihonPath.endsWith("Extra_ffffff.cbz") }.duplicate)
    }

    @Test
    fun duplicates_toggleOtherCopy_thenToggleSeriesBackToDefault() = runTest {
        val vm = openDupes(TranslateRequest(preselectAll = true))

        vm.onEvent(TranslateEvent.ToggleChapter("Mihon/garden/Nyx Scans_Chapter 29_dddddd.cbz"))
        val both = vm.state.first { it.selectedCount == 5 }
        assertEquals(SeriesCheck.All, both.checkOf(both.series.single()))

        // Serie complete -> decochee, puis recochee : retour a une copie par numero.
        vm.onEvent(TranslateEvent.ToggleSeries("garden"))
        vm.state.first { it.selectedCount == 0 }
        vm.onEvent(TranslateEvent.ToggleSeries("garden"))
        assertEquals(4, vm.state.first { it.selectedCount == 4 }.selectedDirs().size)
        assertTrue(vm.state.value.selectedDirs().none { it.startsWith("Nyx") })
    }

    @Test
    fun duplicates_fromActivity_toggleSeriesSelectsOnePerNumber() = runTest {
        val vm = openDupes(TranslateRequest(preselectAll = false))
        vm.onEvent(TranslateEvent.ToggleSeries("garden"))
        val state = vm.state.first { it.selectedCount > 0 }
        assertEquals(4, state.selectedCount)
    }

    @Test
    fun duplicates_numberAlreadySent_otherCopyNotPreselected() = runTest {
        val vm = openDupes(TranslateRequest(preselectAll = true))
        vm.onEvent(TranslateEvent.Send(translate = true))
        vm.state.first { it.phase is TranslatePhase.Done }

        // Rinko 29-31 et Extra sont partis : Nyx 29-30 restent, decoches par defaut.
        vm.onEvent(TranslateEvent.Open(TranslateRequest(preselectAll = true)))
        val state = vm.state.first { it.totalCount == 2 }
        assertEquals(0, state.selectedCount)
        assertEquals(2, state.series.single().duplicateCount)

        // Cocher la serie quand meme : une copie par numero.
        vm.onEvent(TranslateEvent.ToggleSeries("garden"))
        assertEquals(2, vm.state.first { it.selectedCount > 0 }.selectedCount)
    }
}
