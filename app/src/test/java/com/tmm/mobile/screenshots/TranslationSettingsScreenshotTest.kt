package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.translationsettings.ActiveSheet
import com.tmm.mobile.ui.screens.translationsettings.TranslationSettingsScreen
import com.tmm.mobile.ui.screens.translationsettings.TranslationSyncStatus
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran Réglages de traduction (E4.9, TranslationSettings.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class TranslationSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun translationSettings_default() = capture("TranslationSettings_default") {
        TranslationSettingsScreen(state = MockupData.translationSettings, onEvent = {})
    }

    @Test
    fun translationSettings_sending() = capture("TranslationSettings_sending") {
        TranslationSettingsScreen(
            state = MockupData.translationSettings.copy(syncStatus = TranslationSyncStatus.Sending),
            onEvent = {},
        )
    }

    @Test
    fun translationSettings_advanced() = capture("TranslationSettings_advanced") {
        TranslationSettingsScreen(
            state = MockupData.translationSettings.copy(isAdvancedExpanded = true),
            onEvent = {},
        )
    }

    @Test
    fun translationSettings_sheet() = capture("TranslationSettings_sheet") {
        TranslationSettingsScreen(
            state = MockupData.translationSettings.copy(activeSheet = ActiveSheet.TranslationProvider),
            onEvent = {},
        )
    }
}
