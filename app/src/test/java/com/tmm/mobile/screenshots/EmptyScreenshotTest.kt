package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.empty.EmptyScreen
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran Accueil vide (E4.11, Empty.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class EmptyScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun emptyScreen_default() = capture("EmptyScreen_default") {
        EmptyScreen(state = MockupData.empty, onEvent = {})
    }

    @Test
    fun emptyScreen_busy() = capture("EmptyScreen_busy") {
        EmptyScreen(
            state = MockupData.empty.copy(isBusy = true),
            onEvent = {},
        )
    }

    @Test
    fun emptyScreen_done() = capture("EmptyScreen_done") {
        EmptyScreen(
            state = MockupData.empty.copy(isDone = true),
            onEvent = {},
        )
    }
}
