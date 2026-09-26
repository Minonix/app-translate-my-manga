package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.permission.PermissionScreen
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran Accès Mihon perdu (E4.13, Permission.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class PermissionScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun permissionScreen_default() = capture("PermissionScreen_default") {
        PermissionScreen(state = MockupData.permission, onEvent = {})
    }

    @Test
    fun permissionScreen_busy() = capture("PermissionScreen_busy") {
        PermissionScreen(
            state = MockupData.permission.copy(isBusy = true),
            onEvent = {},
        )
    }

    @Test
    fun permissionScreen_fixed() = capture("PermissionScreen_fixed") {
        PermissionScreen(
            state = MockupData.permission.copy(isFixed = true),
            onEvent = {},
        )
    }
}
