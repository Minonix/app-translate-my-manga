package com.tmm.mobile.ui.screens.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Tests de comportement de l'écran Activité (maquette v2, écran 7).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QueueScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<QueueEvent>()
    private var openedNew = 0
    private var openedSettings = 0

    private fun setState(initial: QueueState): () -> QueueState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                QueueScreen(
                    state = state,
                    // Onglet et focus sont locaux ; le reste passe par le ViewModel, ici simplement émis.
                    onEvent = { event ->
                        events += event
                        state = when (event) {
                            is QueueEvent.SelectTab -> state.copy(activeTab = event.tab)
                            is QueueEvent.FocusJob -> state.copy(focusedJobIndex = event.index)
                            else -> state
                        }
                    },
                    onOpenNewTranslation = { openedNew++ },
                    onOpenTranslationSettings = { openedSettings++ },
                )
            }
        }
        return { state }
    }

    @Test
    fun initialState_showsHeaderTabsAndFocusedJob() {
        val getState = setState(MockupData.queue)
        composeRule.onNodeWithText("Activité").assertExists()
        composeRule.onNodeWithText("Ce qui part, ce que traduit le PC, ce qui revient").assertExists()
        composeRule.onNodeWithText("En cours").assertExists()
        composeRule.onNodeWithText("Terminées").assertExists()
        composeRule.onNodeWithText("Échecs").assertExists()
        composeRule.onNodeWithText("PC-BUREAU · 4 chapitres à la fois").assertExists()
        composeRule.onNodeWithText("Solo Leveling · ch. 201").assertExists()
        composeRule.onNodeWithText("Page 16 sur 22 · Gemini").assertExists()
        composeRule.onNodeWithText("73 %").assertExists()
        composeRule.onNodeWithText("Lecture ✓ · Traduction ✓ · Lettrage en cours · Vérification").assertExists()
        assertEquals(QueueTab.Active, getState().activeTab)
    }

    @Test
    fun activeTab_showsTransfersAndNextSection() {
        setState(MockupData.queue)
        composeRule.onNodeWithText("Traduction 42 %").assertExists()
        composeRule.onNodeWithText("Envoi 98 %").assertExists()
        composeRule.onNodeWithText("Retour 3/4 Mo").assertExists()
        composeRule.onNodeWithText("Ensuite (3)").performScrollTo().assertExists()
        composeRule.onNodeWithText("suivi auto").assertExists()
        // Liste paresseuse : la ligne n'existe qu'une fois amenee a l'ecran.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Correction des oublis"))
        composeRule.onNodeWithText("Correction des oublis").assertExists()
    }

    @Test
    fun headerActions_openTranslateAndSettings() {
        setState(MockupData.queue)
        composeRule.onNodeWithContentDescription("Traduire des chapitres").performClick()
        composeRule.onNodeWithText("Régler").performClick()
        assertEquals(1, openedNew)
        assertEquals(1, openedSettings)
    }

    @Test
    fun selectTab_changesDisplayedContent() {
        val getState = setState(MockupData.queue)

        composeRule.onNodeWithText("Terminées").performClick()
        assertEquals(QueueTab.Done, getState().activeTab)
        composeRule.onNodeWithText("Frieren · ch. 139").assertExists()
        composeRule.onNodeWithText("Il y a 40 min · prête à installer").assertExists()

        composeRule.onNodeWithText("Échecs").performClick()
        assertEquals(QueueTab.Fail, getState().activeTab)
        composeRule.onNodeWithText("Chainsaw Man · ch. 171").assertExists()
        composeRule.onNodeWithText("Échec page 12 : quota du moteur atteint").assertExists()
        composeRule.onNodeWithText("Reprendre à la page 12").assertExists()
    }

    @Test
    fun focusJob_switchesFocusedCard() {
        val getState = setState(MockupData.queue)

        composeRule.onNodeWithContentDescription("Voir le détail de Kagurabachi · ch. 52").performScrollTo().performClick()
        assertEquals(1, getState().focusedJobIndex)
        composeRule.onNodeWithText("Page 8 sur 19 · Gemini").assertExists()
    }

    @Test
    fun removeWaitingJob_emitsRemoveEvent() {
        setState(MockupData.queue)

        composeRule.onNodeWithContentDescription("Retirer Kagurabachi · ch. 53").performScrollTo().performClick()
        assertTrue(events.contains(QueueEvent.RemoveWaitingJob("k53")))
    }

    @Test
    fun doneJob_togglesDetailsAndFixesPage() {
        setState(MockupData.queue.copy(activeTab = QueueTab.Done))

        composeRule.onNodeWithText("21/21 pages").assertExists()
        composeRule.onNodeWithText("176 bulles").assertExists()
        composeRule.onAllNodesWithText("Corriger la page")[0].performScrollTo().performClick()
        assertTrue(events.contains(QueueEvent.VerifyPage(0, 4)))

        composeRule.onNodeWithContentDescription("Détails de traduction, Blue Lock · ch. 212").performClick()
        assertTrue(events.contains(QueueEvent.ToggleDoneJobDetails(1)))
    }

    @Test
    fun checkedAlert_showsInCorrectionInsteadOfButton() {
        val done = MockupData.queue.doneJobs[0].let { it.copy(alerts = it.alerts.map { a -> a.copy(isChecked = true) }) }
        setState(MockupData.queue.copy(activeTab = QueueTab.Done, doneJobs = listOf(done)))

        composeRule.onAllNodesWithText("En correction")[0].assertExists()
        composeRule.onNodeWithText("Corriger la page").assertDoesNotExist()
    }

    @Test
    fun retryFailedJob_emitsRetryEventAndShowsQuotaTip() {
        setState(MockupData.queue.copy(activeTab = QueueTab.Fail))

        composeRule.onNodeWithText("Reprendre à la page 12").performScrollTo().performClick()
        assertTrue(events.contains(QueueEvent.RetryFailedJob("csm171")))
        composeRule.onNodeWithText(
            "Astuce : activez « Secours LLM local » dans Réglages, Traduction, pour basculer sur Ollama quand le quota est atteint.",
        ).assertExists()
    }

    @Test
    fun emptyLists_showEmptyMessages() {
        setState(QueueState(activeTab = QueueTab.Fail))
        composeRule.onNodeWithText("Aucun échec en cours.").assertExists()
        composeRule.onNodeWithText("Terminées").performClick()
        composeRule.onNodeWithText("Aucun chapitre terminé pour l'instant.").assertExists()
        composeRule.onNodeWithText("En cours").performClick()
        composeRule.onNodeWithText("Rien ne se traduit en ce moment.").assertExists()
    }

    @Test
    fun cancelButtons_emitCancelAllEvents() {
        setState(MockupData.queue)
        composeRule.onNodeWithText("Annuler les envois en cours (1)").performClick()
        composeRule.onNodeWithText("Tout annuler").performScrollTo().performClick()
        assertTrue(QueueEvent.CancelAllUploads in events)
        assertTrue(QueueEvent.CancelAllWaiting in events)
    }
}
