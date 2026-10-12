package com.tmm.mobile.ui.screens.library

import com.tmm.mobile.ui.components.PillTone
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockupData
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryV2StateTest {
    @Test fun statusPriority_failureBusyInstallTranslateAbsentOk() {
        val series = MockupData.series.first().copy(tone = StatusTone.Send)
        val details = LibrarySeriesDetails(failures = 1, busy = 1, toInstall = 1)
        assertEquals(PillTone.Fail, libraryPillTone(series, details))
        assertEquals(PillTone.Busy, libraryPillTone(series, details.copy(failures = 0)))
        assertEquals(PillTone.Get, libraryPillTone(series, details.copy(failures = 0, busy = 0)))
        assertEquals(PillTone.Todo, libraryPillTone(series, LibrarySeriesDetails()))
        assertEquals(PillTone.Absent, libraryPillTone(series.copy(toSend = 0, tone = StatusTone.Absent), LibrarySeriesDetails()))
        assertEquals(PillTone.Ok, libraryPillTone(series.copy(toSend = 0, tone = StatusTone.Ok), LibrarySeriesDetails()))
    }

    @Test fun secondaryCategory_followedSearchAndPickingCombine() {
        val series = MockupData.series.first().copy(seriesKey = "a", category = "En cours")
        val state = LibraryState(categories = emptyList(), allSeries = listOf(series), category = "Favoris",
            query = "blue", view = LibraryViewFilter.Followed, picking = true,
            details = mapOf("a" to LibrarySeriesDetails(categories = setOf("En cours", "Favoris"), followed = true)))
        assertEquals(listOf(series), state.visibleSeries)
        assertEquals(emptyList<Any>(), state.copy(query = "absent").visibleSeries)
        assertEquals(emptyList<Any>(), state.copy(details = mapOf("a" to LibrarySeriesDetails(categories = setOf("Favoris")))).visibleSeries)
    }

    @Test fun duplicateTitles_selectOnlyOneSource() {
        val first = MockupData.series.first().copy(seriesKey = "source-a")
        val second = first.copy(seriesKey = "source-b")
        val state = LibraryState(categories = emptyList(), allSeries = listOf(first, second), picking = true, selected = setOf("source-b"))
        assertEquals(1, state.selectionCount)
        assertEquals(false, state.allVisibleSelected)
    }
}
