package com.tmm.mobile.ui.screens.library

import androidx.compose.runtime.CompositionLocalProvider
import com.tmm.mobile.ui.screens.translate.LocalOpenTranslate
import com.tmm.mobile.ui.screens.translate.TranslateRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.series.ActionCounts
import com.tmm.mobile.ui.screens.series.SeriesSheet
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Verifications comportementales de l'ecran Bibliotheque : recherche, filtres categorie/vue, mode
 * selection, feuille « Que faire ? » (comptes calcules par le ViewModel), feuille Supprimer et
 * toasts (maquette v2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(initial: LibraryState): () -> LibraryState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                LibraryScreen(state = state, onEvent = { event -> state = reduce(state, event) })
            }
        }
        return { state }
    }

    private fun reduce(state: LibraryState, event: LibraryEvent): LibraryState = when (event) {
        is LibraryEvent.QueryChanged -> state.copy(query = event.value)
        LibraryEvent.TogglePicking -> state.copy(picking = !state.picking, selected = emptySet())
        is LibraryEvent.SelectCategory -> state.copy(category = event.name)
        is LibraryEvent.SelectView -> state.copy(view = event.view)
        is LibraryEvent.SelectSort -> state.copy(sort = event.sort)
        LibraryEvent.OpenFilters -> state.copy(filtersOpen = true)
        LibraryEvent.CloseFilters -> state.copy(filtersOpen = false)
        is LibraryEvent.ToggleSelected -> state.copy(
            selected = state.selected.toMutableSet().apply {
                if (!add(event.seriesKey)) remove(event.seriesKey)
            },
        )
        LibraryEvent.ToggleSelectAll -> {
            val visibleKeys = state.visibleSeries.map { it.seriesKey }
            state.copy(selected = if (state.allVisibleSelected) emptySet() else visibleKeys.toSet())
        }
        LibraryEvent.ExpandCategories -> state.copy(categoriesExpanded = true)
        LibraryEvent.OpenMore -> if (state.selected.isEmpty()) state else state.copy(moreOpen = true, actions = state.actions ?: LibraryActions())
        LibraryEvent.CloseMore -> state.copy(moreOpen = false, actions = null)
        LibraryEvent.Install -> showToast(state, LibraryToast.Installing(state.actions?.counts?.install ?: 0))
        LibraryEvent.Verify -> showToast(state, LibraryToast.Verified(state.actions?.counts?.verify ?: 0))
        LibraryEvent.Retranslate -> showToast(state, LibraryToast.Retranslated(state.actions?.counts?.retranslate ?: 0))
        LibraryEvent.CopyToPc -> showToast(state, LibraryToast.Copied(state.actions?.counts?.copy ?: 0))
        LibraryEvent.Follow -> showToast(state, LibraryToast.Followed(state.actions?.toFollow ?: 0, 0))
        LibraryEvent.RequestDelete -> state.actions?.let { a ->
            state.copy(moreOpen = false, delete = SeriesSheet.Delete(a.deletable, a.deleteSkipped))
        } ?: state
        is LibraryEvent.ToggleDeleteTarget -> state
        LibraryEvent.ConfirmDelete -> showToast(state, LibraryToast.Deleted(state.delete?.chapterCount ?: 0))
        LibraryEvent.CloseDelete -> state.copy(delete = null, actions = null)
        LibraryEvent.DismissToast -> state.copy(toast = null)
    }

    private fun showToast(state: LibraryState, toast: LibraryToast): LibraryState =
        state.copy(toast = toast, moreOpen = false, actions = null, delete = null, picking = false, selected = emptySet())

    @Test
    fun search_filtersSeriesByTitle() {
        setState(MockupData.library)
        composeRule.onNodeWithText("Blue Lock").assertExists()
        composeRule.onNodeWithText("Frieren").assertExists()
        composeRule.onNodeWithContentDescription("Rechercher une série").performTextInput("Blue")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Blue Lock").assertExists()
        composeRule.onNodeWithText("Frieren").assertDoesNotExist()
    }

    @Test
    fun category_filtersToMatchingSeriesOnly() {
        setState(MockupData.library)
        composeRule.onNodeWithText("Catégorie : toutes ▾").performClick()
        composeRule.onNodeWithText("Terminé").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Vinland Saga").assertExists()
        composeRule.onNodeWithText("Chainsaw Man").assertExists()
        composeRule.onNodeWithText("Blue Lock").assertDoesNotExist()
    }

    @Test
    fun viewFilter_absent_showsOnlyAbsentSeries() {
        setState(MockupData.library)
        composeRule.onNodeWithText("Non téléchargées").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dandadan").assertExists()
        composeRule.onNodeWithText("Blue Lock").assertDoesNotExist()
    }

    @Test
    fun filterButton_opensSheetAndSortAppliesImmediately() {
        val currentState = setState(MockupData.library)
        composeRule.onNodeWithContentDescription("Trier et filtrer").performClick()
        composeRule.waitForIdle()
        assertEquals(true, currentState().filtersOpen)
        composeRule.onAllNodesWithText("Ajoutées récemment").onFirst().assertExists()
        composeRule.onNodeWithText("À traduire d'abord").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(LibrarySort.ToTranslateFirst, currentState().sort)
        assertEquals(listOf("Sakamoto Days", "Kagurabachi", "Blue Lock"), currentState().visibleSeries.take(3).map { it.title })
        // La feuille reste ouverte : les choix suivants s'enchainent sans la rouvrir.
        assertEquals(true, currentState().filtersOpen)
    }

    @Test
    fun sort_titleAndRecentlyAdded() {
        val dated = MockupData.library.copy(
            allSeries = MockupData.library.allSeries.mapIndexed { i, s -> s.copy(dateAdded = i.toLong()) },
        )
        assertEquals("Chainsaw Man", dated.visibleSeries.first().title)
        assertEquals(
            dated.allSeries.map { it.title }.sortedBy { it.lowercase() },
            dated.copy(sort = LibrarySort.Title).visibleSeries.map { it.title },
        )
    }

    @Test
    fun picking_toggleShowsFinishButtonAndSelectAllHint() {
        setState(MockupData.library)
        composeRule.onNodeWithText("Sélectionner").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Terminer").assertExists()
        composeRule.onNodeWithText("Touchez les séries à inclure").assertExists()
        composeRule.onNodeWithText("Tout cocher (9)").assertExists()
    }

    @Test
    fun selectAll_thenLabelBecomesDeselectAll() {
        val currentState = setState(MockupData.library.copy(picking = true))
        composeRule.onNodeWithText("Tout cocher (9)").performClick()
        composeRule.waitForIdle()
        assertEquals(9, currentState().selectionCount)
        composeRule.onNodeWithText("Tout décocher").assertExists()
    }

    @Test
    fun selectionBar_isTheSeriesBarWithoutV1Tiles() {
        setState(MockupData.library.copy(picking = true, selected = setOf("Blue Lock", "Kagurabachi")))
        composeRule.onNodeWithText("2 séries sélectionnées").assertExists()
        composeRule.onNodeWithText("Que faire ?").assertExists()
        composeRule.onNodeWithText("Envoyer").assertDoesNotExist()
        composeRule.onNodeWithText("Récupérer").assertDoesNotExist()
        composeRule.onNodeWithText("Vérifier").assertDoesNotExist()
    }

    @Test
    fun selectionBar_hiddenWithoutSelection() {
        setState(MockupData.library.copy(picking = true))
        composeRule.onNodeWithText("Que faire ?").assertDoesNotExist()
    }

    @Test
    fun selectThenTranslate_opensSharedSheetWithSeriesKeys() {
        var request: TranslateRequest? = null
        val series = MockupData.series.first().copy(seriesKey = "source/blue-lock", toTranslate = 1)
        var state by mutableStateOf(
            MockupData.library.copy(
                allSeries = listOf(series),
                picking = true,
                actions = LibraryActions(ActionCounts(translate = 1)),
            ),
        )
        composeRule.setContent {
            TmmTheme {
                CompositionLocalProvider(LocalOpenTranslate provides { request = it }) {
                    LibraryScreen(state = state, onEvent = { state = reduce(state, it) })
                }
            }
        }
        composeRule.onNodeWithText("Blue Lock").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("source/blue-lock"), state.selected)
        composeRule.onNodeWithText("Que faire ?").performClick()
        composeRule.waitForIdle()
        assertEquals(true, state.moreOpen)
        composeRule.onNodeWithText("Traduire 1 chapitre").performClick()
        composeRule.waitForIdle()
        assertEquals(TranslateRequest(true, setOf("source/blue-lock")), request)
        assertEquals(false, state.moreOpen)
    }

    @Test
    fun moreSheet_hidesZeroRowsAndInstallTriggersFetch() {
        // Rien a traduire, 1 a installer : le bouton rouge devient Installer.
        val currentState = setState(
            MockupData.library.copy(
                picking = true,
                selected = setOf("Frieren"),
                moreOpen = true,
                actions = LibraryActions(ActionCounts(install = 1, verify = 1)),
            ),
        )
        composeRule.onNodeWithText("Installer 1 chapitre").assertExists()
        composeRule.onNodeWithText("Traduire 1 chapitre").assertDoesNotExist()
        composeRule.onNodeWithText("Installer dans Mihon").assertDoesNotExist()
        composeRule.onNodeWithText("Retraduire de zéro").assertDoesNotExist()
        composeRule.onNodeWithText("Corriger les oublis").assertExists()
        composeRule.onNodeWithText("Installer 1 chapitre").performClick()
        composeRule.waitForIdle()
        assertEquals(LibraryToast.Installing(1), currentState().toast)
    }

    @Test
    fun moreSheet_waitsForCounts() {
        setState(MockupData.library.copy(picking = true, selected = setOf("Blue Lock"), moreOpen = true))
        composeRule.onNodeWithText("Corriger les oublis").assertDoesNotExist()
    }

    @Test
    fun moreSheet_followAndCopyRowsDispatch() {
        val currentState = setState(
            MockupData.library.copy(
                picking = true,
                selected = setOf("Blue Lock", "Frieren"),
                moreOpen = true,
                actions = LibraryActions(ActionCounts(copy = 3), toFollow = 2),
            ),
        )
        composeRule.onNodeWithText("Suivre ces séries").assertExists()
        composeRule.onNodeWithText("Copier sur le PC sans traduire").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(LibraryToast.Copied(3), currentState().toast)
    }

    @Test
    fun moreSheet_deleteOpensDeleteSheet() {
        val currentState = setState(
            MockupData.library.copy(
                picking = true,
                selected = setOf("Blue Lock"),
                moreOpen = true,
                actions = LibraryActions(deletable = 4, deleteSkipped = 1),
            ),
        )
        composeRule.onNodeWithText("Supprimer…").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesSheet.Delete(4, 1), currentState().delete)
        assertEquals(false, currentState().moreOpen)
        composeRule.onNodeWithText("1 chapitre ignoré : traitement en cours.").assertExists()
    }

    @Test
    fun followedChip_filtersAutomaticallyFollowedSeries() {
        setState(MockupData.library.copy(details = mapOf("Blue Lock" to LibrarySeriesDetails(followed = true))))
        composeRule.onNodeWithText("Suivies").performScrollTo().performClick()
        composeRule.onNodeWithText("Blue Lock").assertExists()
        composeRule.onNodeWithText("Frieren").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Série suivie automatiquement").assertExists()
    }

    @Test
    fun toast_copiedShowsPluralizedCountAndSeeAction() {
        setState(MockupData.library.copy(toast = LibraryToast.Copied(4)))
        composeRule.onNodeWithText("4 chapitres copiés sur le PC, sans traduction.").assertExists()
        composeRule.onNodeWithText("Voir").assertExists()
    }

    @Test
    fun toast_installingSingular() {
        setState(MockupData.library.copy(toast = LibraryToast.Installing(1)))
        composeRule.onNodeWithText("1 traduction en cours d'installation dans Mihon.").assertExists()
    }

    @Test
    fun toast_verifiedShowsPluralizedCount() {
        setState(MockupData.library.copy(toast = LibraryToast.Verified(4)))
        composeRule.onNodeWithText("4 chapitres partent en correction des oublis.").assertExists()
    }

    @Test
    fun toast_retranslatedShowsPluralizedCount() {
        setState(MockupData.library.copy(toast = LibraryToast.Retranslated(4)))
        composeRule.onNodeWithText("4 chapitres partent en retraduction.").assertExists()
    }

    @Test
    fun toast_followedNamesSeriesCount() {
        setState(MockupData.library.copy(toast = LibraryToast.Followed(2, 5)))
        composeRule.onNodeWithText("Suivi activé sur 2 séries. 5 chapitres partent au PC.").assertExists()
    }

    @Test
    fun toast_deletedHasNoSeeAction() {
        setState(MockupData.library.copy(toast = LibraryToast.Deleted(3)))
        composeRule.onNodeWithText("3 chapitres supprimés.").assertExists()
        composeRule.onNodeWithText("Voir").assertDoesNotExist()
    }
}
