package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.library.LibraryScreen
import com.tmm.mobile.ui.screens.library.LibraryToast
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi de l'ecran Bibliotheque (E4.2) : grille normale, mode
 * selection, etat vide, feuille de confirmation et toast (Library.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class LibraryScreenshotTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun libraryScreen_grid() = capture("LibraryScreen_grid") {
        LibraryScreen(state = MockupData.library, onEvent = {})
    }

    @Test
    fun libraryScreen_picking() = capture("LibraryScreen_picking") {
        LibraryScreen(
            state = MockupData.library.copy(picking = true, selected = setOf("Blue Lock", "Kagurabachi")),
            onEvent = {},
        )
    }

    @Test
    fun libraryScreen_empty() = capture("LibraryScreen_empty") {
        LibraryScreen(state = MockupData.library.copy(query = "zzz-introuvable"), onEvent = {})
    }

    @Test
    fun libraryScreen_confirmSheet() = capture("LibraryScreen_confirmSheet") {
        LibraryScreen(
            state = MockupData.library.copy(selected = setOf("Blue Lock", "Kagurabachi"), confirmRetranslate = true),
            onEvent = {},
        )
    }

    @Test
    fun libraryScreen_toast() = capture("LibraryScreen_toast") {
        LibraryScreen(state = MockupData.library.copy(toast = LibraryToast.Sent(4)), onEvent = {})
    }
}
