package com.tmm.mobile.ui.screens.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Tests de comportement de l'écran Catalogue (E4.6, Catalog.dc.html).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var selectedCategory: String? = null
    private var libraryOpened = false

    private fun setState(initial: CatalogState): () -> CatalogState {
        var state by mutableStateOf(initial)
        val vm = CatalogViewModel(initial)
        composeRule.setContent {
            TmmTheme {
                CatalogScreen(
                    state = state,
                    onEvent = { event ->
                        vm.onEvent(event)
                        state = vm.state.value
                    },
                    onOpenLibrary = { libraryOpened = true },
                    onOpenCategory = { selectedCategory = it },
                )
            }
        }
        return { state }
    }

    @Test
    fun initialState_showsHeaderAndCard() {
        val getState = setState(MockupData.catalog)
        composeRule.onNodeWithText("Catalogue").assertExists()
        composeRule.onNodeWithText("Votre bibliothèque Mihon, séries et catégories").assertExists()
        composeRule.onNodeWithText("com.mihon_2026-09-26.tachibk").assertExists()
        composeRule.onNodeWithText("Importée aujourd'hui 03:00 · depuis Mihon/autobackup").assertExists()
        composeRule.onNodeWithText("142").assertExists()
        composeRule.onNodeWithText("38").assertExists()
        composeRule.onNodeWithText("7").assertExists()
        composeRule.onNodeWithText("En cours").assertExists()
        assertEquals(142, getState().totalSeries)
    }

    @Test
    fun reimport_updatesWhenAndCounts() {
        val getState = setState(MockupData.catalog)

        composeRule.onNodeWithText("Relire la dernière sauvegarde").performScrollTo().performClick()
        assertEquals("à l'instant", getState().importedWhen)
        assertEquals(143, getState().totalSeries)
        assertEquals(37, getState().absentSeries)
        composeRule.onNodeWithText("Importée à l'instant · depuis Mihon/autobackup").assertExists()
        composeRule.onNodeWithText("143").assertExists()
        composeRule.onNodeWithText("37").assertExists()
    }

    @Test
    fun busyState_showsProgressBarAndStepLabel() {
        val getState = setState(MockupData.catalog.copy(importStep = CatalogImportStep.ReadingSeries))
        assertTrue(getState().isBusy)
        composeRule.onNodeWithText("Lecture des 142 séries...").assertExists()
    }

    @Test
    fun openCategory_invokesCallback() {
        setState(MockupData.catalog)

        composeRule.onNodeWithContentDescription("Ouvrir la catégorie En cours").performScrollTo().performClick()
        assertEquals("En cours", selectedCategory)
    }

    @Test
    fun openAbsent_invokesLibraryCallback() {
        setState(MockupData.catalog)

        composeRule.onNodeWithText("non téléchargées").performScrollTo().performClick()
        assertTrue(libraryOpened)
    }
}
