package com.tmm.mobile.ui.screens.series

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le geste « appui long puis glisser » envoie bien le debut, les chapitres survoles et la fin. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class SeriesDragGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressThenDrag_reportsEachChapterPassedOver() {
        val events = mutableListOf<SeriesEvent>()
        composeRule.setContent { TmmTheme { SeriesScreen(state = MockupData.seriesDetail, onEvent = { events += it }) } }

        val start = composeRule.onNodeWithText("Ch. 214").fetchSemanticsNode().boundsInRoot.center
        val end = composeRule.onNodeWithText("Ch. 213").fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(end)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(events.toString(), SeriesEvent.DragSelectStart("214"), events.firstOrNull { it is SeriesEvent.DragSelectStart })
        assertTrue(events.toString(), events.any { it == SeriesEvent.DragSelectTo("213") })
        assertEquals(SeriesEvent.DragSelectEnd, events.last())
    }

    @Test
    fun shortTap_doesNotStartADrag() {
        val events = mutableListOf<SeriesEvent>()
        composeRule.setContent { TmmTheme { SeriesScreen(state = MockupData.seriesDetail, onEvent = { events += it }) } }
        composeRule.onNodeWithText("Ch. 214").performTouchInput { click() }
        composeRule.waitForIdle()
        assertTrue(events.none { it is SeriesEvent.DragSelectStart })
    }
}
