package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.onboarding.OnboardingScreen
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran d'installation (E4.10, Onboarding.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class OnboardingScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun onboarding_step1() = capture("Onboarding_step1") {
        OnboardingScreen(state = MockupData.onboarding, onEvent = {})
    }

    @Test
    fun onboarding_step1_connected() = capture("Onboarding_step1_connected") {
        OnboardingScreen(
            state = MockupData.onboarding.copy(isGoogleConnected = true),
            onEvent = {},
        )
    }

    @Test
    fun onboarding_step2() = capture("Onboarding_step2") {
        OnboardingScreen(
            state = MockupData.onboarding.copy(step = 2, isMihonSelected = true),
            onEvent = {},
        )
    }

    @Test
    fun onboarding_step3() = capture("Onboarding_step3") {
        OnboardingScreen(
            state = MockupData.onboarding.copy(
                step = 3,
                readProgress = 1f,
                seriesCount = 142,
                categoriesCount = 7,
                downloadedCount = 104,
                isReadDone = true,
            ),
            onEvent = {},
        )
    }
}
