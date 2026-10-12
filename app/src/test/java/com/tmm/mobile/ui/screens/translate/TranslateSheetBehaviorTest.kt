package com.tmm.mobile.ui.screens.translate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Feuille « Traduire » (maquette v2, écran 6), affichée seule avec les données de démo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TranslateSheetBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<TranslateEvent>()
    private var dismissed = false

    private fun show(state: TranslateState = MockupData.translate) {
        composeRule.setContent {
            TmmTheme {
                TranslateSheet(visible = true, state = state, onEvent = { events += it }, onDismiss = { dismissed = true })
            }
        }
    }

    @Test
    fun showsCountsAndDescriptionsFromMockup() {
        show()
        composeRule.onNodeWithText("11 chapitres téléchargés, jamais traduits", ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ch. 7 à 11 · 5 ch.").assertIsDisplayed()
        composeRule.onNodeWithText("2 sur 3 cochés").assertIsDisplayed()
        composeRule.onNodeWithText("Ch. 214").assertIsDisplayed()
        composeRule.onNodeWithText("Ch. 118, 119").assertIsDisplayed()
        composeRule.onNodeWithText("Ch. 54").assertIsDisplayed()
        composeRule.onNodeWithText("Traduire 8 chapitres").assertIsDisplayed()
    }

    @Test
    fun translateButtonSendsTranslateTrue_andLinkSendsTranslateFalse() {
        show()
        composeRule.onNodeWithText("Traduire 8 chapitres").performClick()
        composeRule.onNodeWithContentDescription("copier sur le PC sans traduire").performClick()
        assertEquals(listOf(TranslateEvent.Send(true), TranslateEvent.Send(false)), events)
    }

    @Test
    fun rowsEmitToggleEvents_andCloseDismisses() {
        show()
        composeRule.onNodeWithContentDescription("Kaiju No. 8").performClick()
        composeRule.onNodeWithText("Ch. 54").performClick()
        composeRule.onNodeWithText("Blue Lock").performClick()
        composeRule.onNodeWithContentDescription("Fermer").performClick()
        assertEquals(
            listOf(
                TranslateEvent.ToggleSeries("kaiju"),
                TranslateEvent.ToggleChapter("Mihon/kagurabachi/54"),
                TranslateEvent.ToggleExpand("blue-lock"),
            ),
            events,
        )
        assertTrue(dismissed)
    }

    @Test
    fun emptySelection_disablesSend() {
        show(MockupData.translate.copy(selected = emptySet()))
        composeRule.onNodeWithText("Traduire 0 chapitres").assertIsNotEnabled()
    }
}
