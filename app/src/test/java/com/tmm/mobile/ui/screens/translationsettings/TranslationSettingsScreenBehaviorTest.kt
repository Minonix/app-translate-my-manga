package com.tmm.mobile.ui.screens.translationsettings

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
 * Tests comportementaux pour l'écran Réglages de traduction (TranslationSettings.dc.html, E4.9).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TranslationSettingsScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backNavigated = false

    private fun setState(initial: TranslationSettingsState = MockupData.translationSettings): () -> TranslationSettingsState {
        var state by mutableStateOf(initial)
        val vm = TranslationSettingsViewModel()
        composeRule.setContent {
            TmmTheme {
                TranslationSettingsScreen(
                    state = state,
                    onEvent = { event ->
                        vm.onEvent(event)
                        state = vm.state.value
                    },
                    onNavigateBack = { backNavigated = true },
                )
            }
        }
        return { state }
    }

    @Test
    fun initialState_showsHeaderCardAndGroups() {
        setState()
        composeRule.onNodeWithText("Traduction sur le PC").assertExists()
        composeRule.onNodeWithText("Les mêmes réglages que dans TMM-Win").assertExists()
        composeRule.onNodeWithText("Synchronisé avec PC-BUREAU").assertExists()
        composeRule.onNodeWithText("Pris en compte tout de suite, sans redémarrage").assertExists()
        composeRule.onNodeWithText("LANGUES").assertExists()
        composeRule.onNodeWithText("MOTEUR").assertExists()
        composeRule.onNodeWithText("APRÈS TRADUCTION").assertExists()
        composeRule.onNodeWithText("PERFORMANCE").assertExists()
    }

    @Test
    fun backButton_callsNavigateBack() {
        setState()
        composeRule.onNodeWithContentDescription("Retour aux réglages").performClick()
        assertTrue(backNavigated)
    }

    @Test
    fun openSheet_selectSourceLanguage_updatesState() {
        val getState = setState()
        composeRule.onNodeWithText("Langue source").performClick()
        assertEquals(ActiveSheet.SourceLanguage, getState().activeSheet)

        composeRule.onNodeWithText("Japonais").performClick()
        assertEquals(SourceLanguage.Japanese, getState().sourceLanguage)
        assertEquals(TranslationSyncStatus.Sending, getState().syncStatus)
        assertEquals(null, getState().activeSheet)
    }

    @Test
    fun toggleAutoFailover_updatesState() {
        val getState = setState()
        assertTrue(getState().autoFailoverToLocalLlm)

        composeRule.onAllNodes(androidx.compose.ui.test.isToggleable())[0].performClick()
        assertFalse(getState().autoFailoverToLocalLlm)
        assertEquals(TranslationSyncStatus.Sending, getState().syncStatus)
    }

    @Test
    fun toggleAutoVerify_updatesState() {
        val getState = setState()
        assertTrue(getState().autoVerifyAfterTranslate)

        composeRule.onAllNodes(androidx.compose.ui.test.isToggleable())[1].performClick()
        assertFalse(getState().autoVerifyAfterTranslate)
        assertEquals(TranslationSyncStatus.Sending, getState().syncStatus)
    }

    @Test
    fun segmentedConcurrency_updatesState() {
        val getState = setState()
        composeRule.onNodeWithText("4 en parallèle").assertExists()

        // Select 1 chapter concurrency
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("1"))[0].performClick()
        assertEquals(1, getState().chapterConcurrency)
        composeRule.onNodeWithText("Un à la fois").assertExists()
    }

    @Test
    fun toggleAdvanced_showsAndHidesAdvancedSection() {
        val getState = setState()
        assertFalse(getState().isAdvancedExpanded)

        composeRule.onNodeWithText("Vision avancée (5 réglages)").performScrollTo().performClick()
        assertTrue(getState().isAdvancedExpanded)
        composeRule.onNodeWithText("Détecteur de bulles").assertExists()
        composeRule.onNodeWithText("OCR").assertExists()
        composeRule.onNodeWithText("Effacement du texte").assertExists()

        composeRule.onNodeWithText("Masquer la vision avancée").performScrollTo().performClick()
        assertFalse(getState().isAdvancedExpanded)
    }
}
