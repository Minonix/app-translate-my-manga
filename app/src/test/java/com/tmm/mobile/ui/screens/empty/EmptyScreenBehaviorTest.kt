package com.tmm.mobile.ui.screens.empty

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * Tests de comportement de l'écran Accueil vide (E4.11, Empty.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var readBackupClicked = false

    private fun setState(initial: EmptyState = MockupData.empty): () -> EmptyState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                EmptyScreen(
                    state = state,
                    onEvent = { event -> state = reduce(state, event) },
                    onReadBackup = { readBackupClicked = true },
                )
            }
        }
        return { state }
    }

    private fun reduce(state: EmptyState, event: EmptyEvent): EmptyState = when (event) {
        EmptyEvent.ScanMihon -> state.copy(isBusy = true)
    }

    @Test
    fun initialState_showsElements() {
        setState()
        composeRule.onNodeWithText("Accueil").assertExists()
        composeRule.onNodeWithText("TMM Mobile").assertExists()
        composeRule.onNodeWithText("PC vu il y a 3 h").assertExists()
        composeRule.onNodeWithText("Aucun chapitre téléchargé").assertExists()
        composeRule.onNodeWithText("Téléchargez des chapitres dans Mihon, puis revenez ici : ils apparaîtront prêts à être envoyés au PC.").assertExists()
        composeRule.onNodeWithText("Scanner Mihon").assertExists()
        composeRule.onNodeWithText("Pas encore de sauvegarde Mihon").assertExists()
        composeRule.onNodeWithText("File vide").assertExists()
        composeRule.onNodeWithText("Rien ne se traduit sur le PC.").assertExists()
    }

    @Test
    fun clickReadBackup_invokesCallback() {
        setState()
        assertFalse(readBackupClicked)
        composeRule.onNodeWithText("J'ai une sauvegarde, la lire").performClick()
        assertTrue(readBackupClicked)
    }

    @Test
    fun clickScan_transitionsToBusy() {
        val getState = setState()
        assertFalse(getState().isBusy)
        assertFalse(getState().isDone)

        composeRule.onNodeWithText("Scanner Mihon").performClick()
        composeRule.waitForIdle()

        assertTrue(getState().isBusy)
        composeRule.onNodeWithText("Analyse de Mihon...").assertExists()
    }

    @Test
    fun doneState_displaysRetryLabel() {
        setState(EmptyState(isDone = true))
        composeRule.onNodeWithText("Toujours rien, réessayer").assertExists()
    }

    // Bug vu sur emulateur : au premier lancement, « PC vu il y a 3 h » s'affichait sans aucun PC vu.
    @Test
    fun defaultState_hidesFakePcPill() {
        setState(EmptyState())
        composeRule.onNodeWithText("PC vu il y a 3 h").assertDoesNotExist()
    }

    // Bug vu sur emulateur : « Scanner Mihon » / « réessayer » ne lançait aucun scan (simple délai).
    @Test
    fun viewModelScan_runsRealScan() {
        var scans = 0
        val vm = EmptyViewModel(scanMihon = { scans++ })
        vm.onEvent(EmptyEvent.ScanMihon)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, scans)
        assertTrue(vm.state.value.isDone)
        assertFalse(vm.state.value.isBusy)
    }
}
