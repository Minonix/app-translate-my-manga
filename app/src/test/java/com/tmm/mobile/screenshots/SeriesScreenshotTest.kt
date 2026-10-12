package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.series.SeriesScreen
import com.tmm.mobile.ui.screens.series.SeriesSheet
import com.tmm.mobile.ui.screens.series.SeriesToast
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de la fiche série (maquette v2, écrans 3 à 5).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class SeriesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun seriesScreen_default() = capture("SeriesScreen_default") {
        SeriesScreen(state = MockupData.seriesDetail, onEvent = {})
    }

    @Test
    fun seriesScreen_selectionSheet() = capture("SeriesScreen_selectionSheet") {
        SeriesScreen(
            state = MockupData.seriesDetail.copy(selecting = true, sheet = SeriesSheet.More(allSeries = false)),
            onEvent = {},
        )
    }

    @Test
    fun seriesScreen_followSheet() = capture("SeriesScreen_followSheet") {
        SeriesScreen(
            state = MockupData.seriesDetail.copy(sheet = SeriesSheet.Follow(listOf(214))),
            onEvent = {},
        )
    }

    @Test
    fun seriesScreen_followed() = capture("SeriesScreen_followed") {
        SeriesScreen(
            state = MockupData.seriesDetail.copy(followed = true, sentOnFollow = listOf(214), toTranslateNumbers = emptyList()),
            onEvent = {},
        )
    }

    @Test
    fun seriesScreen_deleteSheet() = capture("SeriesScreen_deleteSheet") {
        SeriesScreen(
            state = MockupData.seriesDetail.copy(selectionToDelete = 2, selectionDeleteSkipped = 1, sheet = SeriesSheet.Delete(2, 1)),
            onEvent = {},
        )
    }

    @Test
    fun seriesScreen_toast() = capture("SeriesScreen_toast") {
        SeriesScreen(
            state = MockupData.seriesDetail.copy(toast = SeriesToast.Followed(1)),
            onEvent = {},
        )
    }
}
