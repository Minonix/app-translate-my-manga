package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.queue.QueueScreen
import com.tmm.mobile.ui.screens.queue.QueueTab
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran File (E4.5, Queue.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class QueueScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun queueScreen_active() = capture("QueueScreen_active") {
        QueueScreen(state = MockupData.queue, onEvent = {})
    }

    @Test
    fun queueScreen_done() = capture("QueueScreen_done") {
        QueueScreen(
            state = MockupData.queue.copy(activeTab = QueueTab.Done),
            onEvent = {},
        )
    }

    @Test
    fun queueScreen_fail() = capture("QueueScreen_fail") {
        QueueScreen(
            state = MockupData.queue.copy(activeTab = QueueTab.Fail),
            onEvent = {},
        )
    }

    @Test
    fun queueScreen_fail_retried() = capture("QueueScreen_fail_retried") {
        QueueScreen(
            state = MockupData.queue.copy(activeTab = QueueTab.Fail, isRetried = true),
            onEvent = {},
        )
    }
}
