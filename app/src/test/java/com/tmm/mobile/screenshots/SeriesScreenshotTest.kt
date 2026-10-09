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
 * Captures Roborazzi de l'écran Fiche série (E4.3, Series.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
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
    fun seriesScreen_seriesActionsSheet() = capture("SeriesScreen_seriesActionsSheet") {
        SeriesScreen(
            state = MockupData.seriesDetail.copy(sheet = SeriesSheet.SeriesActions),
            onEvent = {},
        )
    }

    @Test
    fun seriesScreen_confirmSheet() = capture("SeriesScreen_confirmSheet") {
        SeriesScreen(
            state = MockupData.seriesDetail.copy(sheet = SeriesSheet.Confirm(2)),
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
            state = MockupData.seriesDetail.copy(toast = SeriesToast.Sent(1)),
            onEvent = {},
        )
    }
}
