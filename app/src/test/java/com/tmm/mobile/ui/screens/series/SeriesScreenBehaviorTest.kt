package com.tmm.mobile.ui.screens.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Tests de comportement de la fiche série (maquette v2, écrans 3 à 5).
 * Le ViewModel est remplacé par un réducteur local : seuls le rendu et les événements émis sont testés ici.
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

    private fun toggle(set: Set<String>, key: String): Set<String> = if (key in set) set - key else set + key

    private fun reduce(state: SeriesState, event: SeriesEvent): SeriesState = when (event) {
        SeriesEvent.StartSelection -> state.copy(selecting = true)
        SeriesEvent.EndSelection -> state.copy(selecting = false, selected = emptySet())
        is SeriesEvent.ToggleChapter -> {
            val row = state.rows.firstOrNull { it.key == event.key }
            if (row == null || row.locked || row.kind == ChapterKind.NotDownloaded) {
                state
            } else {
                state.copy(selected = toggle(state.selected, event.key))
            }
        }
        is SeriesEvent.ToggleStats -> state.copy(expandedStats = toggle(state.expandedStats, event.key))
        SeriesEvent.DismissGap -> state.copy(showGap = false)
        SeriesEvent.TranslateAll -> state.copy(toast = SeriesToast.Translated(state.toTranslateNumbers.size))
        SeriesEvent.OpenSeriesMore -> state.copy(sheet = SeriesSheet.More(allSeries = true))
        SeriesEvent.OpenSelectionMore -> state.copy(sheet = SeriesSheet.More(allSeries = false))
        SeriesEvent.CloseSheet -> state.copy(sheet = SeriesSheet.None)
        SeriesEvent.Translate -> state.copy(toast = SeriesToast.Translated(1), sheet = SeriesSheet.None)
        SeriesEvent.Install -> state.copy(toast = SeriesToast.Installing(1), sheet = SeriesSheet.None)
        SeriesEvent.Verify -> state.copy(toast = SeriesToast.Verified(1), sheet = SeriesSheet.None)
        SeriesEvent.Retranslate -> state.copy(toast = SeriesToast.Retranslated(1), sheet = SeriesSheet.None)
        SeriesEvent.CopyToPc -> state.copy(toast = SeriesToast.Copied(1), sheet = SeriesSheet.None)
        SeriesEvent.RequestDelete -> state.copy(sheet = SeriesSheet.Delete(state.selectionToDelete, state.selectionDeleteSkipped))
        is SeriesEvent.ToggleDeleteTarget -> (state.sheet as? SeriesSheet.Delete)?.let { sheet ->
            state.copy(
                sheet = when (event.target) {
                    DeleteTarget.LocalFr -> sheet.copy(localFr = !sheet.localFr)
                    DeleteTarget.Mihon -> sheet.copy(mihon = !sheet.mihon)
                    DeleteTarget.Pc -> sheet.copy(pc = !sheet.pc)
                },
            )
        } ?: state
        SeriesEvent.ConfirmDelete -> (state.sheet as? SeriesSheet.Delete)?.takeIf { it.anyChecked }?.let { sheet ->
            state.copy(toast = SeriesToast.Deleted(sheet.chapterCount), sheet = SeriesSheet.None, selected = emptySet())
        } ?: state
        is SeriesEvent.SetFollow ->
            if (event.follow) state.copy(sheet = SeriesSheet.Follow(state.toTranslateNumbers)) else state.copy(followed = false)
        SeriesEvent.ConfirmFollow -> state.copy(
            followed = true,
            sheet = SeriesSheet.None,
            toast = SeriesToast.Followed((state.sheet as? SeriesSheet.Follow)?.numbers?.size ?: 0),
        )
        SeriesEvent.UndoFollow -> state.copy(followed = false, toast = null)
        SeriesEvent.DismissToast -> state.copy(toast = null)
    }

    @Test
    fun initialState_showsProgressAndBigButton() {
        setState(MockupData.seriesDetail)
        composeRule.onNodeWithText("Blue Lock").assertExists()
        composeRule.onNodeWithText("205 chapitres en VF sur 214").assertExists()
        composeRule.onNodeWithText("Traduire le ch. 214").assertExists()
        // Liste paresseuse : la ligne n'existe qu'une fois amenee a l'ecran.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Ch. 1"))
        composeRule.onNodeWithText("Ch. 1").assertExists()
    }

    @Test
    fun bigButton_translatesAll() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithText("Traduire le ch. 214").performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesToast.Translated(1), getState().toast)
    }

    @Test
    fun followSwitch_opensSheet_thenConfirmFollows() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithContentDescription("Suivre cette série").performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesSheet.Follow(listOf(214)), getState().sheet)
        composeRule.onNodeWithText("1 chapitre déjà téléchargé partira aussi", substring = true).assertExists()

        composeRule.onNodeWithText("Suivre Blue Lock").performClick()
        composeRule.waitForIdle()
        assertTrue(getState().followed)
        assertEquals(SeriesToast.Followed(1), getState().toast)
        composeRule.onNodeWithText("Série suivie").assertExists()
        composeRule.onNodeWithText("Blue Lock est suivie. 1 chapitre part au PC.").assertExists()
    }

    @Test
    fun dismissGap_hidesAlert() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithText("Ignorer").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(getState().showGap)
    }

    @Test
    fun toggleStats_expandsChapterStats() {
        val getState = setState(MockupData.seriesDetail.copy(expandedStats = emptySet()))
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Détails de traduction du chapitre 211"))
        composeRule.onNodeWithContentDescription("Détails de traduction du chapitre 211").performClick()
        composeRule.waitForIdle()
        assertTrue("211" in getState().expandedStats)
        composeRule.onNodeWithText("22/22 pages").assertExists()
    }

    @Test
    fun selection_skipsLockedRows_thenOpensSheet() {
        val getState = setState(MockupData.seriesDetail.copy(selected = emptySet()))
        composeRule.onNodeWithText("Sélectionner").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(getState().selecting)

        composeRule.onNodeWithText("Ch. 214").performScrollTo().performClick()
        composeRule.onNodeWithText("Ch. 213").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("214"), getState().selected)

        composeRule.onNodeWithText("Que faire ?").performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesSheet.More(allSeries = false), getState().sheet)
        composeRule.onNodeWithText("Corriger les oublis").assertExists()
        composeRule.onNodeWithText("Le ch. 213 est en cours de traduction : il n'est pas sélectionnable.").assertExists()
    }

    @Test
    fun moreButton_opensAllSeriesSheet_copyWithoutTranslation() {
        val getState = setState(MockupData.seriesDetail)
        composeRule.onNodeWithContentDescription("Actions sur toute la série").performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesSheet.More(allSeries = true), getState().sheet)
        composeRule.onNodeWithText("Copier sur le PC sans traduire").performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesToast.Copied(1), getState().toast)
    }

    @Test
    fun deleteSheet_confirmBlockedWithoutChoice_thenDeletes() {
        val getState = setState(
            MockupData.seriesDetail.copy(selectionToDelete = 2, selectionDeleteSkipped = 1, sheet = SeriesSheet.Delete(2, 1)),
        )
        composeRule.onNodeWithText("1 chapitre ignoré : traitement en cours.").assertExists()

        // Seule « Traduction sur le téléphone » est cochée par défaut : la décocher bloque la confirmation.
        composeRule.onNodeWithText("Traduction sur le téléphone").performClick()
        composeRule.waitForIdle()
        assertFalse((getState().sheet as SeriesSheet.Delete).anyChecked)
        composeRule.onAllNodesWithText("Supprimer").onFirst().performClick()
        composeRule.waitForIdle()
        assertTrue(getState().sheet is SeriesSheet.Delete)

        composeRule.onNodeWithText("Fichiers sur le PC (original et traduction)").performClick()
        composeRule.waitForIdle()
        assertTrue((getState().sheet as SeriesSheet.Delete).pc)
        composeRule.onAllNodesWithText("Supprimer").onFirst().performClick()
        composeRule.waitForIdle()
        assertEquals(SeriesToast.Deleted(2), getState().toast)
    }
}
