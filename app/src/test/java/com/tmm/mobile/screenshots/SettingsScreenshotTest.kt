package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.settings.DriveRetention
import com.tmm.mobile.ui.screens.settings.SettingsScreen
import com.tmm.mobile.ui.screens.settings.SettingsToast
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran Réglages (E4.8, Settings.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun settingsScreen_default() = capture("SettingsScreen_default") {
        SettingsScreen(state = MockupData.settings, onEvent = {})
    }

    @Test
    fun settingsScreen_wifiOnly() = capture("SettingsScreen_wifiOnly") {
        SettingsScreen(
            state = MockupData.settings.copy(wifiOnly = true),
            onEvent = {},
        )
    }

    @Test
    fun settingsScreen_toast() = capture("SettingsScreen_toast") {
        SettingsScreen(
            state = MockupData.settings.copy(toast = SettingsToast.ChooseAccount),
            onEvent = {},
        )
    }

    @Test
    fun settingsScreen_offline() = capture("SettingsScreen_offline") {
        SettingsScreen(
            state = MockupData.settings.copy(isPcOnline = false),
            onEvent = {},
        )
    }
}
