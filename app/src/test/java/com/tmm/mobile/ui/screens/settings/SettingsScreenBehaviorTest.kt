package com.tmm.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
 * Tests comportementaux pour l'écran Réglages (Settings.dc.html, E4.8).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backNavigated = false
    private var catalogNavigated = false
    private var onboardingNavigated = false
    private var translationSettingsNavigated = false

    private fun setState(initial: SettingsState = MockupData.settings): () -> SettingsState {
        var state by mutableStateOf(initial)
        val vm = SettingsViewModel()
        composeRule.setContent {
            TmmTheme {
                SettingsScreen(
                    state = state,
                    onEvent = { event ->
                        vm.onEvent(event)
                        state = vm.state.value
                    },
                    onNavigateBack = { backNavigated = true },
                    onNavigateCatalog = { catalogNavigated = true },
                    onNavigateOnboarding = { onboardingNavigated = true },
                    onNavigateTranslationSettings = { translationSettingsNavigated = true },
                )
            }
        }
        return { state }
    }

    @Test
    fun initialState_showsHeaderAndPcCard() {
        setState()
        composeRule.onNodeWithText("Réglages").assertExists()
        composeRule.onNodeWithText("PC-BUREAU").assertExists()
        composeRule.onNodeWithText("TMM-Win 2.4.1 · protocole v1").assertExists()
        composeRule.onNodeWithText("En ligne").assertExists()
        composeRule.onNodeWithText("Dernier signal il y a 12 s · le PC écrit sur Drive toutes les minutes").assertExists()
        composeRule.onNodeWithText("GOOGLE DRIVE").assertExists()
        composeRule.onNodeWithText("MIHON").assertExists()
        composeRule.onNodeWithText("TRADUCTION").assertExists()
        composeRule.onNodeWithText("RÉSEAU").assertExists()
        composeRule.onNodeWithText("DIAGNOSTIC").assertExists()
    }

    @Test
    fun backButton_callsNavigateBack() {
        setState()
        composeRule.onNodeWithContentDescription("Retour à l'accueil").performClick()
        assertTrue(backNavigated)
    }

    @Test
    fun changeGoogleAccount_showsToast() {
        val getState = setState()
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("Changer"))[0].performClick()
        assertEquals(SettingsToast.ChooseAccount, getState().toast)
        composeRule.onNodeWithText("Choix du compte Google").assertExists()
    }

    @Test
    fun cycleDriveRetention_updatesState() {
        val getState = setState()
        composeRule.onNodeWithText("Jamais supprimer").assertExists()

        composeRule.onNodeWithText("Modifier").performClick()
        assertEquals(DriveRetention.Days30, getState().driveRetention)
        composeRule.onNodeWithText("Après 30 jours").assertExists()

        composeRule.onNodeWithText("Modifier").performClick()
        assertEquals(DriveRetention.AfterDownload, getState().driveRetention)
        composeRule.onNodeWithText("Après récupération").assertExists()

        composeRule.onNodeWithText("Modifier").performClick()
        assertEquals(DriveRetention.Never, getState().driveRetention)
        composeRule.onNodeWithText("Jamais supprimer").assertExists()
    }

    @Test
    fun changeMihonFolder_showsToast() {
        val getState = setState()
        // There are two "Changer" buttons: index 0 is Account, index 1 is Mihon folder
        // We find the Mihon row action button
        composeRule.onNodeWithText("Dossier Mihon").assertExists()
        composeRule.onNodeWithText("/Documents/Mihon").assertExists()
        // Click second "Changer"
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("Changer"))[1].performClick()
        assertEquals(SettingsToast.FolderPicker, getState().toast)
        composeRule.onNodeWithText("Sélecteur de dossier Android").assertExists()
    }

    @Test
    fun navigateLinks_callCallbacks() {
        setState()

        composeRule.onNodeWithText("Dernière sauvegarde lue").performScrollTo().performClick()
        assertTrue(catalogNavigated)

        composeRule.onNodeWithText("Refaire l'installation").performScrollTo().performClick()
        assertTrue(onboardingNavigated)

        composeRule.onNodeWithText("Réglages de traduction du PC").performScrollTo().performClick()
        assertTrue(translationSettingsNavigated)
    }

    @Test
    fun toggleWifiOnly_togglesStateAndDesc() {
        val getState = setState()
        composeRule.onNodeWithText("Envois aussi en données mobiles").performScrollTo().assertExists()
        assertFalse(getState().wifiOnly)

        // Click the switch
        composeRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        assertTrue(getState().wifiOnly)
        composeRule.onNodeWithText("Les envois attendent le Wi-Fi").assertExists()

        // Toggle back
        composeRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        assertFalse(getState().wifiOnly)
        composeRule.onNodeWithText("Envois aussi en données mobiles").assertExists()
    }

    @Test
    fun exportLogs_showsToast() {
        val getState = setState()
        composeRule.onNodeWithText("Exporter").performScrollTo().performClick()
        assertEquals(SettingsToast.LogsReady, getState().toast)
        composeRule.onNodeWithText("Journaux prêts à partager").assertExists()
    }
}
