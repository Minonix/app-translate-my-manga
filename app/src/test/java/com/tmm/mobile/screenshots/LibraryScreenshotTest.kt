package com.tmm.mobile.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.screens.library.LibraryScreen
import com.tmm.mobile.ui.screens.library.LibraryToast
import com.tmm.mobile.ui.screens.library.LibraryActions
import com.tmm.mobile.ui.screens.series.ActionCounts
import com.tmm.mobile.ui.screens.series.SeriesSheet
import com.tmm.mobile.ui.screens.library.LibrarySeriesDetails
import com.tmm.mobile.ui.screens.library.LibraryViewFilter
import com.tmm.mobile.ui.components.StatusTone
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
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class LibraryScreenshotTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val library = MockupData.library.copy(
        allSeries = MockupData.series.map { series -> when (series.title) {
            "Blue Lock" -> series.copy(toGet = 0, onPhone = 214)
            "Frieren" -> series.copy(frChapters = 139, onPhone = 140)
            "One Piece" -> series.copy(frChapters = 1122, toGet = 0, tone = StatusTone.Ok, onPhone = 1122)
            "Vinland Saga" -> series.copy(title = "Vagabond", seriesKey = "Vagabond", frChapters = 327, totalChapters = 327, onPhone = 327)
            "Sakamoto Days" -> series.copy(frChapters = 6, totalChapters = 11, toSend = 5, onPhone = 11)
            "Chainsaw Man" -> series.copy(frChapters = 170, totalChapters = 171, onPhone = 171)
            else -> series.copy(onPhone = if (series.tone == StatusTone.Absent) 0 else series.totalChapters)
        } },
        details = mapOf(
            "Blue Lock" to LibrarySeriesDetails(followed = true),
            "Kagurabachi" to LibrarySeriesDetails(followed = true),
            "One Piece" to LibrarySeriesDetails(followed = true),
            "Chainsaw Man" to LibrarySeriesDetails(failures = 1),
        ),
    )

    @Test
    fun libraryScreen_followed() = capture("LibraryV2_followed") {
        LibraryScreen(state = library.copy(view = LibraryViewFilter.Followed), onEvent = {})
    }

    @Test
    fun libraryScreen_grid() = capture("LibraryV2_grid") {
        LibraryScreen(state = library, onEvent = {})
    }

    @Test
    fun libraryScreen_picking() = capture("LibraryV2_picking") {
        LibraryScreen(
            state = library.copy(picking = true, selected = setOf("Blue Lock", "Kagurabachi")),
            onEvent = {},
        )
    }

    @Test
    fun libraryScreen_empty() = capture("LibraryV2_empty") {
        LibraryScreen(state = library.copy(query = "zzz-introuvable"), onEvent = {})
    }

    @Test
    fun libraryScreen_deleteSheet() = capture("LibraryV2_deleteSheet") {
        LibraryScreen(
            state = library.copy(picking = true, selected = setOf("Blue Lock", "Kagurabachi"), delete = SeriesSheet.Delete(12, 1)),
            onEvent = {},
        )
    }

    @Test
    fun libraryScreen_filtersSheet() = capture("LibraryV2_filtersSheet") {
        LibraryScreen(state = library.copy(filtersOpen = true), onEvent = {})
    }

    @Test
    fun libraryScreen_moreSheet() = capture("LibraryV2_moreSheet") {
        LibraryScreen(
            state = library.copy(picking = true, selected = setOf("Blue Lock", "Frieren"),
                moreOpen = true,
                actions = LibraryActions(ActionCounts(translate = 7, install = 1, verify = 1, retranslate = 1, copy = 7), deletable = 12, toFollow = 2),
            ),
            onEvent = {},
        )
    }

    @Test
    fun libraryScreen_toast()= capture("LibraryV2_toast") {
        LibraryScreen(state = library.copy(toast = LibraryToast.Copied(4)), onEvent = {})
    }
}
