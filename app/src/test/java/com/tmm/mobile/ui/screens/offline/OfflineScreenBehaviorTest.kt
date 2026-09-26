package com.tmm.mobile.ui.screens.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Tests de comportement de l'écran Hors ligne (E4.12, Offline.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(initial: OfflineState = MockupData.offline): () -> OfflineState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                OfflineScreen(
                    state = state,
                    onEvent = { event -> state = reduce(state, event) },
                )
            }
        }
        return { state }
    }

    private fun reduce(state: OfflineState, event: OfflineEvent): OfflineState = when (event) {
        OfflineEvent.Reconnect -> state.copy(isReconnecting = true)
    }

    @Test
    fun initialState_network_showsNetworkBannerAndElements() {
        setState()
        composeRule.onNodeWithText("Pas de connexion. ").assertExists()
        composeRule.onNodeWithText("Les envois partiront dès le retour du réseau.").assertExists()
        composeRule.onNodeWithText("TMM Mobile").assertExists()
        composeRule.onNodeWithText("État du PC inconnu").assertExists()
        composeRule.onNodeWithText("Accueil").assertExists()
        composeRule.onNodeWithText("En attente d'envoi").assertExists()
        composeRule.onNodeWithText("3 chapitres").assertExists()
        composeRule.onNodeWithText("Kagurabachi · Ch. 52 à 53").assertExists()
        composeRule.onNodeWithText("Blue Lock · Ch. 214").assertExists()
        composeRule.onAllNodesWithText("Attend le réseau").onFirst().assertExists()
        composeRule.onNodeWithText("Toujours disponible").assertExists()
        composeRule.onNodeWithText("La bibliothèque et les chapitres déjà rangés dans Mihon restent lisibles.").assertExists()
    }

    @Test
    fun causeToken_showsTokenBannerAndReconnectButton() {
        setState(MockupData.offline.copy(cause = OfflineCause.Token))
        composeRule.onNodeWithText("Accès à Google Drive expiré").assertExists()
        composeRule.onNodeWithText("Reconnecter").assertExists()
        composeRule.onAllNodesWithText("Attend la reconnexion à Drive").onFirst().assertExists()
        composeRule.onNodeWithText("Pas de connexion. ").assertDoesNotExist()
    }

    @Test
    fun clickReconnect_triggersReconnectingState() {
        val getState = setState(MockupData.offline.copy(cause = OfflineCause.Token))
        assertFalse(getState().isReconnecting)

        composeRule.onNodeWithText("Reconnecter").performClick()
        composeRule.waitForIdle()

        assertTrue(getState().isReconnecting)
        composeRule.onNodeWithText("Ouverture...").assertExists()
    }

    @Test
    fun reconnectingState_buttonShowsOpening() {
        setState(MockupData.offline.copy(cause = OfflineCause.Token, isReconnecting = true))
        composeRule.onNodeWithText("Ouverture...").assertExists()
    }
}
