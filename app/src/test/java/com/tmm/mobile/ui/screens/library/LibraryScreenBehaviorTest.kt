package com.tmm.mobile.ui.screens.library

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
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Verifications comportementales de l'ecran Bibliotheque (E4.2) : recherche,
 * filtres categorie/vue, mode selection, barre d'actions, feuille de
 * confirmation (garde selectionToVerify == 0) et toasts (Library.dc.html).
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
        is LibraryEvent.ToggleSelected -> state.copy(
            selected = state.selected.toMutableSet().apply {
                if (!add(event.title)) remove(event.title)
            },
        )
        LibraryEvent.ToggleSelectAll -> {
            val visibleTitles = state.visibleSeries.map { it.title }
            state.copy(selected = if (state.allVisibleSelected) emptySet() else visibleTitles.toSet())
        }
        LibraryEvent.SendToPc -> showToast(state, LibraryToast.Sent(state.selectionToSend))
        LibraryEvent.FetchToMihon -> showToast(state, LibraryToast.Received(state.selectionToGet))
        LibraryEvent.VerifyPending -> showToast(state, LibraryToast.Verified(state.selectionToVerify))
        LibraryEvent.RequestRetranslate -> if (state.selectionToVerify > 0) state.copy(confirmRetranslate = true) else state
        LibraryEvent.ConfirmRetranslate -> showToast(state, LibraryToast.Retranslated(state.selectionToVerify))
        LibraryEvent.DismissConfirm -> state.copy(confirmRetranslate = false)
        LibraryEvent.DismissToast -> state.copy(toast = null)
    }

    private fun showToast(state: LibraryState, toast: LibraryToast): LibraryState =
        state.copy(toast = toast, confirmRetranslate = false, picking = false, selected = emptySet())

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
        composeRule.onNodeWithText("Terminé").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Vinland Saga").assertExists()
        composeRule.onNodeWithText("Chainsaw Man").assertExists()
        composeRule.onNodeWithText("Blue Lock").assertDoesNotExist()
    }

    @Test
    fun viewFilter_absent_showsOnlyAbsentSeries() {
        setState(MockupData.library)
        composeRule.onNodeWithText("Non téléchargées").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dandadan").assertExists()
        composeRule.onNodeWithText("Blue Lock").assertDoesNotExist()
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
    fun selectionActionBar_showsNonApplicableWhenCountIsZero() {
        setState(MockupData.library.copy(picking = true, selected = setOf("Dandadan")))
        composeRule.onAllNodesWithText("Non applicable").onFirst().assertExists()
    }

    @Test
    fun requestRetranslate_withChapters_opensConfirmSheet() {
        val currentState = setState(MockupData.library.copy(picking = true, selected = setOf("Blue Lock")))
        composeRule.onNodeWithText("Retraduire").performClick()
        composeRule.waitForIdle()
        assertEquals(true, currentState().confirmRetranslate)
        composeRule.onNodeWithText("Retraduire 205 chapitres ?").assertExists()
    }

    @Test
    fun requestRetranslate_withoutChapters_doesNotOpenConfirmSheet() {
        val currentState = setState(MockupData.library.copy(picking = true, selected = setOf("Dandadan")))
        composeRule.onNodeWithText("Retraduire").performClick()
        composeRule.waitForIdle()
        assertEquals(false, currentState().confirmRetranslate)
    }

    @Test
    fun confirmSheet_cancelDismissesWithoutToast() {
        val currentState = setState(
            MockupData.library.copy(picking = true, selected = setOf("Blue Lock"), confirmRetranslate = true),
        )
        composeRule.onNodeWithText("Annuler").performClick()
        composeRule.waitForIdle()
        assertEquals(false, currentState().confirmRetranslate)
        assertEquals(null, currentState().toast)
    }

    @Test
    fun toast_sentShowsPluralizedCountAndAction() {
        setState(MockupData.library.copy(toast = LibraryToast.Sent(4)))
        composeRule.onNodeWithText("4 chapitres envoyés au PC.").assertExists()
        composeRule.onNodeWithText("Voir la file").assertExists()
    }

    @Test
    fun toast_receivedSingular() {
        setState(MockupData.library.copy(toast = LibraryToast.Received(1)))
        composeRule.onNodeWithText("1 traduction en téléchargement.").assertExists()
    }

    @Test
    fun toast_verifiedShowsPluralizedCount() {
        setState(MockupData.library.copy(toast = LibraryToast.Verified(4)))
        composeRule.onNodeWithText("4 chapitres ajoutés en vérification.").assertExists()
    }

    @Test
    fun toast_retranslatedShowsPluralizedCount() {
        setState(MockupData.library.copy(toast = LibraryToast.Retranslated(4)))
        composeRule.onNodeWithText("4 chapitres ajoutés en retraduction.").assertExists()
    }
}
