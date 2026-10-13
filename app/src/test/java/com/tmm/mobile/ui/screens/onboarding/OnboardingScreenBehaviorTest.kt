package com.tmm.mobile.ui.screens.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Tests comportementaux pour l'écran d'installation (Onboarding.dc.html, E4.10).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var finishCalled = false

    private fun setupScreen(initial: OnboardingState = MockupData.onboarding): Pair<() -> OnboardingState, (OnboardingState) -> Unit> {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                OnboardingScreen(
                    state = state,
                    onEvent = { event -> state = reduce(state, event) },
                    onFinishOnboarding = { finishCalled = true },
                )
            }
        }
        return Pair({ state }, { state = it })
    }

    // Reproduit uniquement les transitions d'etat pertinentes pour ces tests de rendu ; la vraie
    // logique (Google/SAF/import) vit dans OnboardingViewModel et est couverte par OnboardingViewModelTest.
    private fun reduce(state: OnboardingState, event: OnboardingEvent): OnboardingState = when (event) {
        OnboardingEvent.PrimaryAction -> when {
            state.step == 1 && !state.isGoogleConnected -> state.copy(isWorking = true)
            state.step == 1 && state.isGoogleConnected -> state.copy(step = 2)
            state.step == 2 && !state.isMihonSelected -> state.copy(isWorking = true)
            state.step == 2 && state.isMihonSelected -> state.copy(step = 3)
            else -> state
        }
        OnboardingEvent.PreviousStep -> state.copy(step = (state.step - 1).coerceAtLeast(1))
    }

    // Bug vu sur emulateur : Drive en echec, l'etape 1 affichait quand meme « vous@gmail.com »
    // et « Dossier TranslateMyManga/ trouvé · PC vu il y a 2 min ».
    @Test
    fun step1_connectedWithoutDriveInfo_showsNoFakeValues() {
        setupScreen(OnboardingState(isGoogleConnected = true))
        composeRule.onNodeWithText("Compte Google connecté").assertExists()
        composeRule.onNodeWithText("vous@gmail.com").assertDoesNotExist()
        composeRule.onNodeWithText("Dossier TranslateMyManga/ trouvé · PC vu il y a 2 min").assertDoesNotExist()
    }

    @Test
    fun step1_initialState_showsConnectButton() {
        setupScreen()
        composeRule.onNodeWithText("Relier le téléphone et le PC").assertExists()
        composeRule.onNodeWithText("Se connecter avec Google").assertExists()
        composeRule.onNodeWithText("1/3").assertExists()
        composeRule.onNodeWithText("Drive").assertExists()
    }

    @Test
    fun step1_connectGoogle_showsConnectedAccountAndContinue() {
        val (getState, updateState) = setupScreen()
        composeRule.onNodeWithText("Se connecter avec Google").performClick()
        assertTrue(getState().isWorking)

        updateState(getState().copy(isGoogleConnected = true, isWorking = false))

        composeRule.onNodeWithText("vous@gmail.com").assertExists()
        composeRule.onNodeWithText("Dossier TranslateMyManga/ trouvé · PC vu il y a 2 min").assertExists()
        composeRule.onNodeWithText("Continuer").assertExists()
    }

    @Test
    fun step1_continue_navigatesToStep2() {
        val (getState, _) = setupScreen(MockupData.onboarding.copy(isGoogleConnected = true))
        composeRule.onNodeWithText("Continuer").performClick()
        assertEquals(2, getState().step)
        composeRule.onNodeWithText("Où est le dossier Mihon ?").assertExists()
        composeRule.onNodeWithText("2/3").assertExists()
    }

    @Test
    fun step2_selectFolder_showsSubfoldersAndContinue() {
        val (getState, _) = setupScreen(MockupData.onboarding.copy(step = 2, isMihonSelected = true))
        composeRule.onNodeWithText("/Documents/Mihon").assertExists()
        composeRule.onNodeWithText("downloads/").assertExists()
        composeRule.onNodeWithText("local/").assertExists()
        composeRule.onNodeWithText("autobackup/").assertExists()
        composeRule.onNodeWithText("Continuer").assertExists()
    }

    @Test
    fun step2_previousStep_returnsToStep1() {
        val (getState, _) = setupScreen(MockupData.onboarding.copy(step = 2))
        composeRule.onNodeWithText("Étape précédente").performClick()
        assertEquals(1, getState().step)
        composeRule.onNodeWithText("Relier le téléphone et le PC").assertExists()
    }

    @Test
    fun step3_readingBackup_showsMetricsAndFinishButton() {
        setupScreen(
            MockupData.onboarding.copy(
                step = 3,
                readProgress = 1f,
                seriesCount = 142,
                categoriesCount = 7,
                downloadedCount = 104,
                isReadDone = true,
            )
        )
        composeRule.onNodeWithText("Votre bibliothèque").assertExists()
        composeRule.onNodeWithText("142").assertExists()
        composeRule.onNodeWithText("7").assertExists()
        composeRule.onNodeWithText("104").assertExists()
        composeRule.onNodeWithText("12 chapitres téléchargés dans Mihon ne sont pas encore sur le PC. Vous pourrez les envoyer depuis l'accueil.").assertExists()

        composeRule.onNodeWithText("Ouvrir TMM Mobile").performClick()
        assertTrue(finishCalled)
    }
}
