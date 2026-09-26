package com.tmm.mobile.ui.screens.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
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
 * Tests de comportement de l'écran File (E4.5, Queue.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QueueScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(initial: QueueState): () -> QueueState {
        var state by mutableStateOf(initial)
        val vm = QueueViewModel(initial)
        composeRule.setContent {
            TmmTheme {
                QueueScreen(
                    state = state,
                    onEvent = { event ->
                        vm.onEvent(event)
                        state = vm.state.value
                    },
                    onOpenNewTranslation = {},
                    onOpenTranslationSettings = {},
                    onOpenSeries = {},
                )
            }
        }
        return { state }
    }

    @Test
    fun initialState_showsHeaderAndActiveTab() {
        val getState = setState(MockupData.queue)
        composeRule.onNodeWithText("File").assertExists()
        composeRule.onNodeWithText("Traductions et vérifications envoyées au PC").assertExists()
        composeRule.onNodeWithText("Actives").assertExists()
        composeRule.onNodeWithText("Terminées").assertExists()
        composeRule.onNodeWithText("Échecs").assertExists()
        composeRule.onNodeWithText("Solo Leveling · Ch. 201").assertExists()
        composeRule.onNodeWithText("4/4").assertExists()
        assertEquals(QueueTab.Active, getState().activeTab)
    }

    @Test
    fun selectTab_changesDisplayedContent() {
        val getState = setState(MockupData.queue)

        // Select Terminées
        composeRule.onNodeWithText("Terminées").performClick()
        assertEquals(QueueTab.Done, getState().activeTab)
        composeRule.onNodeWithText("Frieren · Ch. 140").assertExists()

        // Select Échecs
        composeRule.onNodeWithText("Échecs").performClick()
        assertEquals(QueueTab.Fail, getState().activeTab)
        composeRule.onNodeWithText("Frieren · Ch. 139").assertExists()
        composeRule.onNodeWithText("Réessayer à partir de la page 9").assertExists()
    }

    @Test
    fun focusJob_switchesActiveJobDetails() {
        val getState = setState(MockupData.queue)

        composeRule.onNodeWithContentDescription("Voir le détail de Kagurabachi · Ch. 52").performScrollTo().performClick()
        assertEquals(1, getState().focusedJobIndex)
    }

    @Test
    fun removeWaitingJob_removesItemFromQueue() {
        val getState = setState(MockupData.queue)
        val initialWaitingCount = getState().waitingJobs.size

        composeRule.onNodeWithContentDescription("Retirer Kagurabachi · Ch. 53 de la file").performScrollTo().performClick()
        assertEquals(initialWaitingCount - 1, getState().waitingJobs.size)
        assertFalse(getState().waitingJobs.any { it.id == "k53" })
    }

    @Test
    fun verifyPage_addsJobToQueue() {
        val getState = setState(MockupData.queue.copy(activeTab = QueueTab.Done))

        composeRule.onAllNodesWithText("Vérifier la page")[0].performScrollTo().performClick()
        composeRule.onNodeWithText("Dans la file").assertExists()
        assertTrue(getState().waitingJobs.any { it.title.contains("p. 7") })
    }

    @Test
    fun retryFailedJob_marksRetriedAndAddsToQueue() {
        val getState = setState(MockupData.queue.copy(activeTab = QueueTab.Fail))

        composeRule.onNodeWithText("Réessayer à partir de la page 9").performScrollTo().performClick()
        assertTrue(getState().isRetried)
        assertEquals(0, getState().failCount)
        composeRule.onNodeWithText("Remis dans la file").assertExists()
        composeRule.onNodeWithText("Aucun échec en cours.").assertExists()
        assertTrue(getState().waitingJobs.any { it.id == "f139" })
    }
}
