package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.main.MainScreen
import com.tmm.mobile.ui.screens.main.PcTone
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'ecran Main (E4.1) : etat nominal et etat hors ligne
 * (Main.dc.html, pcState=offline).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class MainScreenshotTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun mainScreen_online() = capture("MainScreen_online") {
        MainScreen(state = MockupData.main, onEvent = {})
    }

    @Test
    fun mainScreen_offline() = capture("MainScreen_offline") {
        MainScreen(state = MockupData.main.copy(pcTone = PcTone.Offline), onEvent = {})
    }
}
