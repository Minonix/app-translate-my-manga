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
    private val events = mutableListOf<SettingsEvent>()

    private fun setState(initial: SettingsState = MockupData.settings): () -> SettingsState {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            TmmTheme {
                SettingsScreen(
                    state = state,
                    // Le ViewModel reel (E10) ecrit dans AppPrefs et lance les selecteurs Android depuis
                    // SettingsRoute : ici on teste l'ecran seul, avec la meme logique d'etat.
                    onEvent = { event ->
                        events += event
                        state = when (event) {
                            SettingsEvent.ChangeGoogleAccount -> state.copy(toast = SettingsToast.ChooseAccount)
                            SettingsEvent.CycleDriveRetention -> state.copy(driveRetention = state.driveRetention.next())
                            is SettingsEvent.SetWifiOnly -> state.copy(wifiOnly = event.enabled)
                            SettingsEvent.ToggleWifiOnly -> state.copy(wifiOnly = !state.wifiOnly)
                            SettingsEvent.DismissToast -> state.copy(toast = null)
                            is SettingsEvent.DirectUrlChanged -> state.copy(directUrl = event.url)
                            is SettingsEvent.PairingCodeChanged -> state.copy(pairingCode = event.code)
                            SettingsEvent.ChangeMihonFolder, SettingsEvent.ExportLogs,
                            SettingsEvent.Pair, SettingsEvent.ForgetPc -> state
                        }
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
    fun changeMihonFolder_emitsEvent() {
        setState()
        // There are two "Changer" buttons: index 0 is Account, index 1 is Mihon folder
        // We find the Mihon row action button
        composeRule.onNodeWithText("Dossier Mihon").assertExists()
        composeRule.onNodeWithText("/Documents/Mihon").assertExists()
        // Click second "Changer"
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("Changer"))[1].performClick()
        assertTrue(events.contains(SettingsEvent.ChangeMihonFolder))
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
    fun exportLogs_emitsEvent() {
        setState()
        composeRule.onNodeWithText("Exporter").performScrollTo().performClick()
        assertTrue(events.contains(SettingsEvent.ExportLogs))
    }
}
