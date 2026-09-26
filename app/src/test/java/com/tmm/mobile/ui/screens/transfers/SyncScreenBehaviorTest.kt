package com.tmm.mobile.ui.screens.transfers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Tests de comportement de l'écran Transferts (E4.4, Sync.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyncScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(initial: SyncState): () -> SyncState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                SyncScreen(
                    state = state,
                    onEvent = { event -> state = reduce(state, event) },
                    onOpenQueue = {},
                    onOpenLibrary = {},
                    onOpenMissingSeries = {},
                )
            }
        }
        return { state }
    }

    private fun reduce(state: SyncState, event: SyncEvent): SyncState = when (event) {
        SyncEvent.ToggleAutoTranslate -> state.copy(autoTranslate = !state.autoTranslate)
        SyncEvent.SendUp -> state.copy(
            sendState = SendState.Done(state.upCount),
            upCount = 0,
            liveTransfers = state.liveTransfers.filterNot { it.isUpload },
        )
        SyncEvent.GetDown -> state.copy(
            fetchState = FetchState.Done,
            downCount = 0,
            downSeries = state.downSeries.map { it.copy(isDone = true) },
            liveTransfers = state.liveTransfers.filter { it.isUpload },
        )
        SyncEvent.Rescan -> state.copy(scanning = false, scannedAgo = "à l’instant")
    }

    @Test
    fun initialState_showsHeaderAndDiagram() {
        val getState = setState(MockupData.sync)
        composeRule.onNodeWithText("Transferts").assertExists()
        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithText("8").assertExists()
        composeRule.onNodeWithText("PC-BUREAU").assertExists()
        assertEquals(12, getState().upCount)
        assertEquals(8, getState().downCount)
    }

    @Test
    fun toggleAutoTranslate_updatesStateAndHint() {
        val getState = setState(MockupData.sync)
        assertTrue(getState().autoTranslate)
        composeRule.onNodeWithText("Traduire dès l'arrivée sur le PC").performClick()
        composeRule.waitForIdle()
        assertFalse(getState().autoTranslate)
        composeRule.onNodeWithText("Ils seront seulement copiés sur le PC").assertExists()
    }

    @Test
    fun sendUp_marksDoneAndShowsDriveBanner() {
        val getState = setState(MockupData.sync)
        composeRule.onNodeWithText("Envoyer 12 chapitres").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(getState().sendState is SendState.Done)
        assertEquals(0, getState().upCount)
        composeRule.onNodeWithText("12 chapitres déposés sur Drive").assertExists()
        composeRule.onNodeWithText("Tout est parti").assertExists()
    }

    @Test
    fun getDown_marksItemsAsStored() {
        val getState = setState(MockupData.sync)
        composeRule.onNodeWithText("Télécharger 8 chapitres ici").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(FetchState.Done, getState().fetchState)
        assertEquals(0, getState().downCount)
        composeRule.onNodeWithText("Tout est dans Mihon").assertExists()
        composeRule.onNodeWithText("Récupéré").assertExists()
    }

    @Test
    fun rescan_updatesScannedAgoLabel() {
        val getState = setState(MockupData.sync)
        composeRule.onNodeWithText("Rescanner").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("à l’instant", getState().scannedAgo)
        composeRule.onNodeWithText("Mihon/downloads · scanné à l’instant").assertExists()
    }
}
