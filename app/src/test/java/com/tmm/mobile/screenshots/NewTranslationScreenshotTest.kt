package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.newtranslation.NewTranslationPhase
import com.tmm.mobile.ui.screens.newtranslation.NewTranslationScreen
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de la feuille Nouvelle Traduction (E4.7, NewTranslation.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class NewTranslationScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun newTranslationScreen_step1() = capture("NewTranslationScreen_step1") {
        NewTranslationScreen(state = MockupData.newTranslation, onEvent = {})
    }

    @Test
    fun newTranslationScreen_step2() = capture("NewTranslationScreen_step2") {
        val series = MockupData.newTranslation.seriesList.first()
        NewTranslationScreen(
            state = MockupData.newTranslation.copy(
                selectedSeries = series,
                selectedChapters = series.chapters.toSet(),
            ),
            onEvent = {},
        )
    }

    @Test
    fun newTranslationScreen_done() = capture("NewTranslationScreen_done") {
        val series = MockupData.newTranslation.seriesList.first()
        NewTranslationScreen(
            state = MockupData.newTranslation.copy(
                selectedSeries = series,
                selectedChapters = series.chapters.toSet(),
                phase = NewTranslationPhase.Done,
            ),
            onEvent = {},
        )
    }
}
