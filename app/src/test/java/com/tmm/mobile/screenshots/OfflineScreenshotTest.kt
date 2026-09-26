package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.offline.OfflineCause
import com.tmm.mobile.ui.screens.offline.OfflineScreen
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran Hors ligne (E4.12, Offline.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class OfflineScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun offlineScreen_network() = capture("OfflineScreen_network") {
        OfflineScreen(state = MockupData.offline, onEvent = {})
    }

    @Test
    fun offlineScreen_token() = capture("OfflineScreen_token") {
        OfflineScreen(
            state = MockupData.offline.copy(cause = OfflineCause.Token),
            onEvent = {},
        )
    }

    @Test
    fun offlineScreen_reconnecting() = capture("OfflineScreen_reconnecting") {
        OfflineScreen(
            state = MockupData.offline.copy(cause = OfflineCause.Token, isReconnecting = true),
            onEvent = {},
        )
    }
}
