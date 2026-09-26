package com.tmm.mobile.ui.screens.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Tests de comportement de l'écran Fiche série (E4.3, Series.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeriesScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(initial: SeriesState): () -> SeriesState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                SeriesScreen(
                    state = state,
                    onEvent = { event -> state = reduce(state, event) },
                    onBack = {},
                    onOpenQueue = {},
                )
            }
        }
        return { state }
    }

    private fun reduce(state: SeriesState, event: SeriesEvent): SeriesState = when (event) {
        is SeriesEvent.ToggleChapter -> {
            val chapter = state.chapters.firstOrNull { c -> c.number == event.number }
            if (chapter == null || chapter.locked) {
                state
            } else {
                state.copy(selected = state.selected.toMutableSet().apply {
                    if (!add(event.number)) remove(event.number)
                })
            }
        }
        SeriesEvent.SelectTodo -> {
            val todo = state.chapters.filter { c -> c.kind == StatusTone.Send || c.kind == StatusTone.Get }.map { c -> c.number }
            state.copy(selected = todo.toSet())
        }
        SeriesEvent.ClearSelection -> state.copy(selected = emptySet())
        SeriesEvent.DismissGap -> state.copy(showGap = false)
        is SeriesEvent.ToggleStats -> state.copy(expandedStats = state.expandedStats.toMutableSet().apply {
            if (!add(event.number)) remove(event.number)
        })
        SeriesEvent.SendToPc -> state.copy(toast = SeriesToast.Sent(state.selectionToSend), sheet = SeriesSheet.None, selected = emptySet())
        SeriesEvent.FetchToMihon -> state.copy(toast = SeriesToast.Received(state.selectionToGet), sheet = SeriesSheet.None, selected = emptySet())
        SeriesEvent.VerifyPending -> state.copy(toast = SeriesToast.Verified(state.selectionToVerify), sheet = SeriesSheet.None, selected = emptySet())
        SeriesEvent.RequestRetranslateSelection -> if (state.selectionToVerify > 0) state.copy(sheet = SeriesSheet.Confirm(state.selectionToVerify)) else state
        SeriesEvent.OpenSeriesSheet -> state.copy(sheet = SeriesSheet.SeriesActions)
        SeriesEvent.CloseSheet -> state.copy(sheet = SeriesSheet.None)
        SeriesEvent.SeriesSendMissing -> state.copy(toast = SeriesToast.Sent(state.chapters.count { c -> c.kind == StatusTone.Send }), sheet = SeriesSheet.None, selected = emptySet())
        SeriesEvent.SeriesFetchMissing -> state.copy(toast = SeriesToast.Received(state.chapters.count { c -> c.kind == StatusTone.Get }), sheet = SeriesSheet.None, selected = emptySet())
        SeriesEvent.SeriesVerifyAll -> state.copy(toast = SeriesToast.Verified(state.frCount), sheet = SeriesSheet.None, selected = emptySet())
        SeriesEvent.SeriesRequestRetranslateAll -> state.copy(sheet = SeriesSheet.Confirm(state.frCount))
        SeriesEvent.ConfirmRetranslate -> {
            val count = (state.sheet as? SeriesSheet.Confirm)?.chapterCount ?: state.selectionToVerify
            state.copy(toast = SeriesToast.Retranslated(count), sheet = SeriesSheet.None, selected = emptySet())
        }
        SeriesEvent.DismissToast -> state.copy(toast = null)
    }

    @Test
    fun initialState_showsTitleAndCounts() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithText("Blue Lock").assertExists()
        composeRule.onNodeWithText("212").assertExists()
        composeRule.onNodeWithText("213").assertExists()
        composeRule.onNodeWithText("205").assertExists()
        assertEquals(3, getState().selectionCount)
    }

    @Test
    fun selectTodo_selectsSendAndGetChapters() {
        val getState = setState(MockupData.seriesDetail.copy(selected = emptySet()))
        composeRule.onNodeWithText("Tout ce qui reste à faire").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf(214, 212), getState().selected)
    }

    @Test
    fun clearSelection_clearsAllSelected() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithText("Désélectionner").performClick()
        composeRule.waitForIdle()
        assertTrue(getState().selected.isEmpty())
        assertFalse(getState().hasSelection)
    }

    @Test
    fun dismissGap_hidesAlert() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithText("Ignorer").performClick()
        composeRule.waitForIdle()
        assertFalse(getState().showGap)
    }

    @Test
    fun toggleStats_expandsChapterStats() {
        val getState = setState(MockupData.seriesDetail.copy(expandedStats = emptySet()))
        composeRule.onNodeWithContentDescription("Détails de traduction du chapitre 212").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(getState().expandedStats.contains(212))
        composeRule.onNodeWithText("22/22 pages").assertExists()
    }

    @Test
    fun sendAction_triggersToast() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onAllNodesWithText("Envoyer au PC (1)").onFirst().performClick()
        composeRule.waitForIdle()
        val toast = getState().toast
        assertTrue(toast is SeriesToast.Sent)
        assertEquals(1, (toast as SeriesToast.Sent).chapterCount)
    }

    @Test
    fun retranslateAction_opensConfirmSheetAndConfirmRetranslates() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onAllNodesWithText("Retraduire (2)").onFirst().performClick()
        composeRule.waitForIdle()
        assertTrue(getState().sheet is SeriesSheet.Confirm)
        assertEquals(2, (getState().sheet as SeriesSheet.Confirm).chapterCount)

        composeRule.onAllNodesWithText("Retraduire").onFirst().performClick()
        composeRule.waitForIdle()
        val toast = getState().toast
        assertTrue(toast is SeriesToast.Retranslated)
        assertEquals(2, (toast as SeriesToast.Retranslated).chapterCount)
    }

    @Test
    fun openSeriesSheet_showsAllSeriesOptions() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithText("Toute la série").performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesSheet.SeriesActions, getState().sheet)
        composeRule.onNodeWithText("Envoyer ce qui manque").assertExists()
        composeRule.onNodeWithText("Récupérer les traductions").assertExists()
        composeRule.onNodeWithText("Vérifier toute la série").assertExists()
        composeRule.onNodeWithText("Retraduire toute la série").assertExists()
    }
}
