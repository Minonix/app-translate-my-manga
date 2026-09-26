package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.transfers.FetchState
import com.tmm.mobile.ui.screens.transfers.SendState
import com.tmm.mobile.ui.screens.transfers.SyncScreen
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran Transferts (E4.4, Sync.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class SyncScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun syncScreen_default() = capture("SyncScreen_default") {
        SyncScreen(state = MockupData.sync, onEvent = {})
    }

    @Test
    fun syncScreen_emptyLive() = capture("SyncScreen_emptyLive") {
        SyncScreen(
            state = MockupData.sync.copy(liveTransfers = emptyList()),
            onEvent = {},
        )
    }

    @Test
    fun syncScreen_upDone() = capture("SyncScreen_upDone") {
        SyncScreen(
            state = MockupData.sync.copy(
                sendState = SendState.Done(12),
                upCount = 0,
            ),
            onEvent = {},
        )
    }

    @Test
    fun syncScreen_downDone() = capture("SyncScreen_downDone") {
        SyncScreen(
            state = MockupData.sync.copy(
                fetchState = FetchState.Done,
                downCount = 0,
                downSeries = MockupData.sync.downSeries.map { it.copy(isDone = true) },
            ),
            onEvent = {},
        )
    }
}
