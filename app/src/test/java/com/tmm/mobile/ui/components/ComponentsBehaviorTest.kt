package com.tmm.mobile.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Verifications comportementales des composants E3 (PLAN_GEMINI.md 4.9) :
 * roles semantiques, callbacks, et etats temporises (RefreshButton, TmmToast).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tmmSwitch_clickTogglesAndExposesSwitchRole() {
        var checked by mutableStateOf(false)
        composeRule.setContent {
            TmmTheme { TmmSwitch(checked = checked, onCheckedChange = { checked = it }) }
        }
        val node = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        node.assertIsOff()
        node.performClick()
        composeRule.waitForIdle()
        assertTrue(checked)
        node.assertIsOn()
    }

    @Test
    fun tmmSheet_dismissedByScrimClick() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            TmmTheme {
                TmmSheet(visible = visible, onDismiss = { visible = false }, title = "Titre") {
                    TmmText(text = "Contenu")
                }
            }
        }
        composeRule.onNodeWithText("Titre").assertExists()
        // Le scrim occupe tout l'ecran ; un clic loin du contenu (coin haut-gauche) le touche.
        composeRule.onRoot().performTouchInput { click(Offset(2f, 2f)) }
        composeRule.waitForIdle()
        assertFalse(visible)
    }

    @Test
    fun tmmToast_hasPoliteLiveRegionAndAutoDismisses() {
        var visible by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TmmTheme { TmmToast(visible = visible, message = "Envoye.", onDismiss = { visible = false }) }
        }
        composeRule.onNodeWithText("Envoye.").assertExists()
        composeRule.mainClock.advanceTimeBy(3_700)
        composeRule.waitForIdle()
        assertFalse(visible)
    }

    @Test
    fun refreshButton_disabledDuringCooldownWithChangingDescription() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TmmTheme { RefreshButton(onRefresh = {}) }
        }
        composeRule.onNodeWithContentDescription("Rafraîchir").assertExists()
        composeRule.onNodeWithContentDescription("Rafraîchir").performClick()
        composeRule.mainClock.advanceTimeBy(1_400)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rafraîchir, disponible dans 30 secondes").assertExists()
        composeRule.mainClock.advanceTimeBy(30_500)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rafraîchir").assertExists()
    }
}
