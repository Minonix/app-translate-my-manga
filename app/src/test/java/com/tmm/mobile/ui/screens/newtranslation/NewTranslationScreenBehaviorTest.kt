package com.tmm.mobile.ui.screens.newtranslation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Tests de comportement de la feuille Nouvelle Traduction (E4.7, NewTranslation.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NewTranslationScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var closed = false
    private var queueOpened = false

    private fun setState(initial: NewTranslationState): () -> NewTranslationState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                NewTranslationScreen(
                    state = state,
                    onEvent = { event -> state = reduce(state, event) },
                    onClose = { closed = true },
                    onOpenQueue = { queueOpened = true },
                )
            }
        }
        return { state }
    }

    // Le ViewModel reel (E10) lit Room : ici on teste l'ecran seul, avec la meme logique de
    // selection que NewTranslationViewModel.onEvent (cf SeriesScreenBehaviorTest).
    private fun reduce(state: NewTranslationState, event: NewTranslationEvent): NewTranslationState = when (event) {
        is NewTranslationEvent.UpdateQuery -> state.copy(searchQuery = event.query)
        is NewTranslationEvent.SelectSeries -> {
            val series = state.seriesList.firstOrNull { it.seriesKey == event.seriesKey }
            state.copy(
                selectedSeriesKey = event.seriesKey,
                selectedChapters = series?.chapters?.map { it.mihonPath }?.toSet() ?: emptySet(),
                phase = NewTranslationPhase.Idle,
            )
        }
        NewTranslationEvent.BackToSeries ->
            state.copy(selectedSeriesKey = null, selectedChapters = emptySet(), phase = NewTranslationPhase.Idle)
        is NewTranslationEvent.ToggleChapter -> state.copy(
            selectedChapters = if (event.mihonPath in state.selectedChapters) {
                state.selectedChapters - event.mihonPath
            } else {
                state.selectedChapters + event.mihonPath
            },
        )
        NewTranslationEvent.ToggleAllChapters -> state.copy(
            selectedChapters = if (state.isAllChaptersSelected) {
                emptySet()
            } else {
                state.selectedSeries?.chapters?.map { it.mihonPath }?.toSet() ?: emptySet()
            },
        )
        NewTranslationEvent.ToggleTranslateOnArrival -> state.copy(translateOnArrival = !state.translateOnArrival)
        NewTranslationEvent.Send -> state.copy(phase = NewTranslationPhase.Done)
    }

    @Test
    fun initialStep1_showsHeaderAndSeriesList() {
        val getState = setState(MockupData.newTranslation)
        composeRule.onNodeWithText("Nouvelle traduction").assertExists()
        composeRule.onNodeWithText("ÉTAPE 1 SUR 2 · SÉRIE").assertExists()
        composeRule.onNodeWithText("Kagurabachi").assertExists()
        composeRule.onNodeWithText("Sakamoto Days").assertExists()
        composeRule.onNodeWithText("Choisissez une série").assertExists()
        assertFalse(getState().isStep2)
    }

    @Test
    fun searchSeries_filtersList() {
        setState(MockupData.newTranslation)

        composeRule.onNodeWithContentDescription("Série téléchargée dans Mihon").performTextInput("Kagura")
        composeRule.onNodeWithText("Kagurabachi").assertExists()
        composeRule.onNodeWithText("Sakamoto Days").assertDoesNotExist()
    }

    @Test
    fun selectSeries_movesToStep2WithAllChaptersSelected() {
        val getState = setState(MockupData.newTranslation)

        composeRule.onNodeWithText("Kagurabachi").performClick()
        assertTrue(getState().isStep2)
        assertEquals("Kagurabachi", getState().selectedSeries?.title)
        assertEquals(3, getState().selectedCount)
        composeRule.onNodeWithText("ÉTAPE 2 SUR 2 · CHAPITRES").assertExists()
        composeRule.onNodeWithText("Ch. 52").assertExists()
        composeRule.onNodeWithText("Envoyer 3 chapitres").assertExists()
    }

    @Test
    fun toggleChapters_updatesSelectionAndButtonLabel() {
        val series = MockupData.newTranslation.seriesList.first()
        val getState = setState(
            MockupData.newTranslation.copy(
                selectedSeriesKey = series.seriesKey,
                selectedChapters = series.chapters.map { it.mihonPath }.toSet(),
            )
        )

        // Initially all checked: button is "Tout décocher"
        composeRule.onNodeWithText("Tout décocher").performClick()
        assertEquals(0, getState().selectedCount)
        composeRule.onNodeWithText("Cochez des chapitres").assertExists()

        // Check all: button is "Tout cocher"
        composeRule.onNodeWithText("Tout cocher").performClick()
        assertEquals(3, getState().selectedCount)
        composeRule.onNodeWithText("Envoyer 3 chapitres").assertExists()

        // Toggle chapter 52 off
        composeRule.onNodeWithText("Ch. 52").performClick()
        assertEquals(2, getState().selectedCount)
        composeRule.onNodeWithText("Envoyer 2 chapitres").assertExists()
    }

    @Test
    fun toggleTranslateOnArrival_updatesHint() {
        val series = MockupData.newTranslation.seriesList.first()
        val getState = setState(
            MockupData.newTranslation.copy(
                selectedSeriesKey = series.seriesKey,
                selectedChapters = series.chapters.map { it.mihonPath }.toSet(),
            )
        )

        assertTrue(getState().translateOnArrival)
        composeRule.onNodeWithText("Retour automatique dans Mihon une fois traduit").assertExists()

        composeRule.onNodeWithText("Traduire à l'arrivée").performClick()
        assertFalse(getState().translateOnArrival)
        composeRule.onNodeWithText("Simple copie sur le PC, sans traduction").assertExists()
    }

    @Test
    fun send_updatesPhaseToDone() {
        val series = MockupData.newTranslation.seriesList.first()
        val getState = setState(
            MockupData.newTranslation.copy(
                selectedSeriesKey = series.seriesKey,
                selectedChapters = series.chapters.map { it.mihonPath }.toSet(),
            )
        )

        composeRule.onNodeWithText("Envoyer 3 chapitres").performClick()
        assertEquals(NewTranslationPhase.Done, getState().phase)
        composeRule.onNodeWithText("Ajouté à la file · Voir").assertExists()

        composeRule.onNodeWithText("Ajouté à la file · Voir").performClick()
        assertTrue(queueOpened)
    }

    @Test
    fun backToSeries_returnsToStep1() {
        val series = MockupData.newTranslation.seriesList.first()
        val getState = setState(
            MockupData.newTranslation.copy(
                selectedSeriesKey = series.seriesKey,
                selectedChapters = series.chapters.map { it.mihonPath }.toSet(),
            )
        )

        composeRule.onNodeWithContentDescription("Changer de série").performClick()
        assertFalse(getState().isStep2)
        composeRule.onNodeWithText("Nouvelle traduction").assertExists()
    }
}
