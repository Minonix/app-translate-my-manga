package com.tmm.mobile.ui.screens.permission

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Tests de comportement de l'écran Accès Mihon perdu (E4.13, Permission.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PermissionScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backHomeCalled = false
    private var settingsCalled = false

    private fun setState(initial: PermissionState = MockupData.permission): () -> PermissionState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                PermissionScreen(
                    state = state,
                    onEvent = { event -> state = reduce(state, event) },
                    onBackToHome = { backHomeCalled = true },
                    onOpenSettings = { settingsCalled = true },
                )
            }
        }
        return { state }
    }

    private fun reduce(state: PermissionState, event: PermissionEvent): PermissionState = when (event) {
        PermissionEvent.PickFolder -> state.copy(isBusy = true)
    }

    @Test
    fun initialState_showsElements() {
        setState()
        composeRule.onNodeWithText("L'app ne voit plus le dossier Mihon").assertExists()
        composeRule.onNodeWithText("Android a retiré l'autorisation, souvent après une mise à jour ou un déplacement du dossier. Rien n'a été supprimé.").assertExists()
        composeRule.onNodeWithText("DOSSIER ATTENDU").assertExists()
        composeRule.onNodeWithText("/Documents/Mihon").assertExists()
        composeRule.onNodeWithText("Il doit contenir downloads/, local/ et autobackup/.").assertExists()
        composeRule.onNodeWithText("Choisir à nouveau le dossier").assertExists()
        composeRule.onNodeWithText("Aide et réglages").assertExists()
    }

    @Test
    fun clickPickFolder_transitionsToBusy() {
        val getState = setState()
        assertFalse(getState().isBusy)

        composeRule.onNodeWithText("Choisir à nouveau le dossier").performClick()
        composeRule.waitForIdle()

        assertTrue(getState().isBusy)
        composeRule.onNodeWithText("Vérification...").assertExists()
    }

    @Test
    fun fixedState_showsRestoredStatusAndBackHomeButton() {
        setState(MockupData.permission.copy(isFixed = true))
        composeRule.onNodeWithText("Accès rétabli").assertExists()
        composeRule.onNodeWithText("Revenir à l'accueil").assertExists()

        assertFalse(backHomeCalled)
        composeRule.onNodeWithText("Revenir à l'accueil").performClick()
        assertTrue(backHomeCalled)
    }

    @Test
    fun clickSettings_opensSettings() {
        setState()
        assertFalse(settingsCalled)
        composeRule.onNodeWithText("Aide et réglages").performClick()
        assertTrue(settingsCalled)
    }
}
