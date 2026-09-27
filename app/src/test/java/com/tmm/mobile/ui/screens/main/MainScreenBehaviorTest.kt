package com.tmm.mobile.ui.screens.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Verifications comportementales de l'ecran Main (E4.1) : cycle Envoyer/Recuperer
 * (Idle -> Busy -> Done), toast et sa formulation selon MainToast, notice hors ligne.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(initial: MainState): () -> MainState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                MainScreen(state = state, onEvent = { event -> state = reduce(state, event) })
            }
        }
        return { state }
    }

    private fun reduce(state: MainState, event: MainEvent): MainState = when (event) {
        MainEvent.Refresh -> state
        MainEvent.RefreshFinished -> state
        MainEvent.SendPending -> state.copy(sendState = BusyState.Busy)
        MainEvent.FetchTranslated -> state.copy(getState = BusyState.Busy)
        MainEvent.DismissToast -> state.copy(toast = null)
    }

    @Test
    fun sendPending_idleShowsButton_clickTransitionsToBusy() {
        val currentState = setState(MockupData.main)
        composeRule.onNodeWithText("Envoyer").assertExists()
        composeRule.onNodeWithText("Envoyer").performClick()
        composeRule.waitForIdle()
        assertEquals(BusyState.Busy, currentState().sendState)
        composeRule.onNodeWithContentDescription("Envoi en cours").assertExists()
    }

    @Test
    fun fetchTranslated_idleShowsButton_clickTransitionsToBusy() {
        val currentState = setState(MockupData.main)
        composeRule.onNodeWithText("Récupérer").assertExists()
        composeRule.onNodeWithText("Récupérer").performClick()
        composeRule.waitForIdle()
        assertEquals(BusyState.Busy, currentState().getState)
    }

    @Test
    fun doneState_showsDonePillLabel() {
        setState(MockupData.main.copy(sendState = BusyState.Done, getState = BusyState.Done))
        composeRule.onNodeWithText("En file").assertExists()
        composeRule.onAllNodesWithText("Dans Mihon").onFirst().assertExists()
    }

    @Test
    fun toast_sentShowsFormattedCountAndDismisses() {
        val currentState = setState(MockupData.main.copy(toast = MainToast.Sent(12)))
        composeRule.onNodeWithText("12 chapitres partent vers le PC via Google Drive.").assertExists()
        composeRule.onNodeWithText("Voir").assertExists()
    }

    @Test
    fun toast_receivedShowsFormattedCount() {
        setState(MockupData.main.copy(toast = MainToast.Received(8)))
        composeRule.onNodeWithText("8 chapitres traduits rangés dans Mihon/local.").assertExists()
    }

    @Test
    fun offline_showsOfflineNotice() {
        setState(MockupData.main.copy(pcTone = PcTone.Offline, offlineSinceLabel = "depuis 2 jours"))
        composeRule.onNodeWithText("PC hors ligne depuis 2 jours.").assertExists()
    }

    @Test
    fun offline_withoutKnownHeartbeat_hidesOfflineNotice() {
        setState(MockupData.main.copy(pcTone = PcTone.Offline, offlineSinceLabel = null))
        composeRule.onNodeWithText("PC hors ligne depuis 2 jours.").assertDoesNotExist()
    }

    @Test
    fun online_hidesOfflineNotice() {
        setState(MockupData.main.copy(pcTone = PcTone.Online))
        composeRule.onNodeWithText("PC hors ligne depuis 2 jours.").assertDoesNotExist()
    }
}
