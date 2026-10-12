package com.tmm.mobile.ui.screens.translationsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
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
 * Tests comportementaux de l'ecran Reglages de traduction du PC (maquette v2-simplifiee/index.html,
 * section 9) : L'essentiel visible, Vitesse, Rendu et Lecture replies par defaut.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TranslationSettingsScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backNavigated = false

    private fun setState(initial: TranslationSettingsState = MockupData.translationSettings): () -> TranslationSettingsState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                TranslationSettingsScreen(
                    state = state,
                    // Le ViewModel reel (E10) passe par SettingsSync (Drive) : ici on teste l'ecran seul,
                    // avec le meme passage optimiste a Sending que TranslationSettingsViewModel.change().
                    onEvent = { event -> state = reduce(state, event) },
                    onNavigateBack = { backNavigated = true },
                )
            }
        }
        return { state }
    }

    private fun reduce(state: TranslationSettingsState, event: TranslationSettingsEvent): TranslationSettingsState {
        fun sent(next: TranslationSettingsState) = next.copy(activeSheet = null, syncStatus = TranslationSyncStatus.Sending)
        return when (event) {
            is TranslationSettingsEvent.SelectSourceLanguage -> sent(state.copy(sourceLanguage = event.value))
            is TranslationSettingsEvent.SelectTargetLanguage -> sent(state.copy(targetLanguage = event.value))
            is TranslationSettingsEvent.SelectTranslationProvider -> sent(state.copy(translationProvider = event.value))
            is TranslationSettingsEvent.SetAutoFailover -> sent(state.copy(autoFailoverToLocalLlm = event.value))
            is TranslationSettingsEvent.SetAutoVerify -> sent(state.copy(autoVerifyAfterTranslate = event.value))
            is TranslationSettingsEvent.SetChapterConcurrency -> sent(state.copy(chapterConcurrency = event.value))
            is TranslationSettingsEvent.SetConcurrency -> sent(state.copy(concurrency = event.value))
            is TranslationSettingsEvent.SetPileSize -> sent(state.copy(pileSize = event.value))
            is TranslationSettingsEvent.SetUseGpu -> sent(state.copy(useGpu = event.value))
            is TranslationSettingsEvent.SelectDetector -> sent(state.copy(detector = event.value))
            is TranslationSettingsEvent.SelectPreferredOcr -> sent(state.copy(preferredOcr = event.value))
            is TranslationSettingsEvent.SelectInpainter -> sent(state.copy(inpainter = event.value))
            is TranslationSettingsEvent.SelectInpaintingSize -> sent(state.copy(inpaintingSize = event.value))
            is TranslationSettingsEvent.SelectFont -> sent(state.copy(font = event.value))
            TranslationSettingsEvent.ToggleAdvanced -> state.copy(isAdvancedExpanded = !state.isAdvancedExpanded)
            TranslationSettingsEvent.ToggleSpeed -> state.copy(speedExpanded = !state.speedExpanded)
            TranslationSettingsEvent.ToggleRendering -> state.copy(renderingExpanded = !state.renderingExpanded)
            TranslationSettingsEvent.ToggleReading -> state.copy(readingExpanded = !state.readingExpanded)
            is TranslationSettingsEvent.OpenSheet -> state.copy(activeSheet = event.sheet)
            TranslationSettingsEvent.CloseSheet -> state.copy(activeSheet = null)
        }
    }

    @Test
    fun initialState_showsHeaderCardAndGroups() {
        setState()
        composeRule.onNodeWithText("Réglages").assertExists()
        composeRule.onNodeWithText("Traduction").assertExists()
        composeRule.onNodeWithText("Appliqué sur PC-BUREAU, sans redémarrage").assertExists()
        composeRule.onNodeWithText("L’ESSENTIEL").assertExists()
        composeRule.onNodeWithText("Langue d’origine").assertExists()
        composeRule.onNodeWithText("Détection automatique").assertExists()
        composeRule.onNodeWithText("Traduire en").assertExists()
        composeRule.onNodeWithText("Moteur").assertExists()
        composeRule.onNodeWithText("Corriger les oublis après chaque chapitre").assertExists()
        composeRule.onNodeWithText("Si le moteur échoue").assertExists()
        // Les trois groupes techniques sont replies, seul leur resume est visible.
        composeRule.onNodeWithText("4 chapitres à la fois · carte graphique").performScrollTo().assertExists()
        composeRule.onNodeWithText("Police Anime Ace · effacement LaMa Large").performScrollTo().assertExists()
        composeRule.onNodeWithText("Détecteur et OCR automatiques").performScrollTo().assertExists()
        composeRule.onNodeWithText("Chapitres à la fois").assertDoesNotExist()
        // Plus de groupe « Après traduction » vide.
        composeRule.onNodeWithText("APRÈS TRADUCTION").assertDoesNotExist()
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
        composeRule.onNodeWithText("Langue d’origine").performClick()
        assertEquals(ActiveSheet.SourceLanguage, getState().activeSheet)

        composeRule.onNodeWithText("Japonais").performClick()
        assertEquals(SourceLanguage.Japanese, getState().sourceLanguage)
        assertEquals(TranslationSyncStatus.Sending, getState().syncStatus)
        assertEquals(null, getState().activeSheet)
    }

    @Test
    fun toggleAutoVerify_updatesState() {
        val getState = setState()
        assertTrue(getState().autoVerifyAfterTranslate)

        // Groupes replies : [0] « Corriger les oublis », [1] « Si le moteur échoue ».
        composeRule.onAllNodes(isToggleable())[0].performClick()
        assertFalse(getState().autoVerifyAfterTranslate)
        assertEquals(TranslationSyncStatus.Sending, getState().syncStatus)
    }

    @Test
    fun toggleAutoFailover_updatesState() {
        val getState = setState()
        assertTrue(getState().autoFailoverToLocalLlm)

        composeRule.onAllNodes(isToggleable())[1].performClick()
        assertFalse(getState().autoFailoverToLocalLlm)
        assertEquals(TranslationSyncStatus.Sending, getState().syncStatus)
    }

    @Test
    fun segmentedConcurrency_updatesState() {
        val getState = setState()
        composeRule.onNodeWithText("Vitesse").performScrollTo().performClick()
        assertTrue(getState().speedExpanded)
        composeRule.onNodeWithText("Chapitres à la fois").performScrollTo().assertExists()
        composeRule.onNodeWithText("2 par chapitre · recommandé").performScrollTo().assertExists()
        composeRule.onNodeWithText("3 · plus de contexte si élevé").performScrollTo().assertExists()
        composeRule.onNodeWithText("Carte graphique").performScrollTo().assertExists()

        // Premier « 1 » de l'ecran : le segment du nombre de chapitres.
        composeRule.onNodeWithText("Chapitres à la fois").performScrollTo()
        composeRule.onAllNodes(hasText("1"))[0].performClick()
        assertEquals(1, getState().chapterConcurrency)
        composeRule.onNodeWithText("1 chapitre à la fois · carte graphique").performScrollTo().assertExists()
    }

    @Test
    fun pagesRows_openSheetsAndSelect() {
        val getState = setState()
        composeRule.onNodeWithText("Vitesse").performScrollTo().performClick()

        composeRule.onNodeWithText("Pages à la fois").performScrollTo().performClick()
        assertEquals(ActiveSheet.PagesAtOnce, getState().activeSheet)
        composeRule.onNodeWithText("Recommandé").assertExists()
        composeRule.onNodeWithText("4 par chapitre").performClick()
        assertEquals(4, getState().concurrency)
        assertEquals(null, getState().activeSheet)

        composeRule.onNodeWithText("Pages par envoi au moteur").performScrollTo().performClick()
        assertEquals(ActiveSheet.PagesPerBatch, getState().activeSheet)
        composeRule.onAllNodes(hasText("1")).onLast().performClick()
        assertEquals(1, getState().pileSize)
    }

    @Test
    fun toggleGroups_showsAndHidesRenderingAndReading() {
        val getState = setState()
        assertFalse(getState().renderingExpanded)
        assertFalse(getState().readingExpanded)

        composeRule.onNodeWithText("Rendu des pages").performScrollTo().performClick()
        assertTrue(getState().renderingExpanded)
        composeRule.onNodeWithText("Police").performScrollTo().assertExists()
        composeRule.onNodeWithText("Effacement du texte").performScrollTo().assertExists()
        composeRule.onNodeWithText("Taille d’effacement").performScrollTo().assertExists()

        composeRule.onNodeWithText("Lecture des bulles").performScrollTo().performClick()
        assertTrue(getState().readingExpanded)
        composeRule.onNodeWithText("Détecteur de bulles").performScrollTo().assertExists()
        composeRule.onNodeWithText("Reconnaissance du texte (OCR)").performScrollTo().assertExists()

        composeRule.onNodeWithText("Rendu des pages").performScrollTo().performClick()
        assertFalse(getState().renderingExpanded)
        composeRule.onNodeWithText("Effacement du texte").assertDoesNotExist()
    }
}
