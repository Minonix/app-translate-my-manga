package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.catalog.CatalogImportStep
import com.tmm.mobile.ui.screens.catalog.CatalogScreen
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'écran Catalogue (E4.6, Catalog.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class CatalogScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun catalogScreen_default() = capture("CatalogScreen_default") {
        CatalogScreen(state = MockupData.catalog, onEvent = {})
    }

    @Test
    fun catalogScreen_busy() = capture("CatalogScreen_busy") {
        CatalogScreen(
            state = MockupData.catalog.copy(importStep = CatalogImportStep.ReadingSeries),
            onEvent = {},
        )
    }

    @Test
    fun catalogScreen_reimported() = capture("CatalogScreen_reimported") {
        CatalogScreen(
            state = MockupData.catalog.copy(
                importedWhen = "à l'instant",
                totalSeries = 143,
                absentSeries = 37,
            ),
            onEvent = {},
        )
    }
}
