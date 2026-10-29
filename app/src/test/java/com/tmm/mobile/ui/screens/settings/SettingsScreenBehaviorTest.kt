package com.tmm.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tmm.mobile.ui.mock.MockupData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import com.tmm.mobile.ui.theme.TmmTheme

/**
 * Tests comportementaux de l'ecran Reglages (maquette v2-simplifiee/index.html, section 8).
 * Reglages est un onglet : plus de bouton retour. Tout ce qui est rare vit derriere « Avancé ».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

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
                            is SettingsEvent.PcDirectUrlChanged -> state.copy(pcDirectUrl = event.url)
                            is SettingsEvent.PcPairingCodeChanged -> state.copy(pcPairingCode = event.code)
                            SettingsEvent.PairPc, SettingsEvent.ForgetBackupPc -> state
                            SettingsEvent.ChangeMihonFolder, SettingsEvent.ExportLogs,
                            SettingsEvent.Pair, SettingsEvent.ForgetPc -> state
                            SettingsEvent.ToggleAdvanced -> state.copy(advancedExpanded = !state.advancedExpanded)
                            SettingsEvent.OpenDirect -> state.copy(directOpen = true)
                            SettingsEvent.CloseDirect -> state.copy(directOpen = false)
                            SettingsEvent.ToggleFollowed -> state.copy(followedExpanded = !state.followedExpanded)
                            is SettingsEvent.Unfollow, SettingsEvent.Reimport -> state
                            SettingsEvent.CheckUpdate, SettingsEvent.InstallUpdate -> state
                            SettingsEvent.DecreaseConcurrentInstalls -> state.copy(maxConcurrentInstalls = state.maxConcurrentInstalls - 1)
                            SettingsEvent.IncreaseConcurrentInstalls -> state.copy(maxConcurrentInstalls = state.maxConcurrentInstalls + 1)
                            SettingsEvent.UnblockDownloads -> state.copy(toast = SettingsToast.Unblocked(2, 1))
                        }
                    },
                    onNavigateOnboarding = { onboardingNavigated = true },
                    onNavigateTranslationSettings = { translationSettingsNavigated = true },
                )
            }
        }
        return { state }
    }

    private fun openAdvanced() {
        composeRule.onNodeWithText("Avancé").performScrollTo().performClick()
    }

    @Test
    fun initialState_showsHeaderAndPcCard() {
        setState()
        composeRule.onNodeWithText("Réglages").assertExists()
        composeRule.onNodeWithText("PC-BUREAU").assertExists()
        composeRule.onNodeWithText("Allumé · vu il y a 17 s").assertExists()
        composeRule.onNodeWithText("Direct").assertDoesNotExist()
        composeRule.onNodeWithText("Via Drive").assertExists()
        // Aucun PC associe : le pied de carte n'annonce pas la connexion directe.
        composeRule.onNodeWithText("TMM-Win 2.4.1 · Google Drive, aucune connexion directe associée").assertExists()
        composeRule.onNodeWithText("TRADUCTION").assertExists()
        composeRule.onNodeWithText("SÉRIES SUIVIES").assertExists()
        composeRule.onNodeWithText("ENVOIS").assertExists()
        composeRule.onNodeWithText("MIHON").performScrollTo().assertExists()
        composeRule.onNodeWithText("COMPTE").performScrollTo().assertExists()
        composeRule.onNodeWithText("Avancé").performScrollTo().assertExists()
        composeRule.onNodeWithText("Connexion directe, Drive, réinstallation, journaux").assertExists()
        // Replie par defaut : rien de la section Avancé n'est compose.
        composeRule.onNodeWithText("Journaux").assertDoesNotExist()
    }

    // Mise a jour : une version plus recente sur GitHub s'installe depuis les Reglages.
    @Test
    fun updateAvailable_installButtonSendsInstall() {
        val release = com.tmm.mobile.core.AppRelease("9.0.0", "https://example.invalid/app.apk", 10, null)
        setState(MockupData.settings.copy(update = AppUpdateState.Available(release)))
        composeRule.onNodeWithText("Version 9.0.0 disponible").performScrollTo().assertExists()
        composeRule.onNodeWithText("Installer").performScrollTo().performClick()
        assertTrue(events.contains(SettingsEvent.InstallUpdate))
    }

    @Test
    fun followedSeries_countIsPluralizedAndExpands() {
        val getState = setState(
            MockupData.settings.copy(
                followedSeries = listOf(FollowedSeries("bl", "Blue Lock")),
            ),
        )
        composeRule.onNodeWithText("1 série").performClick()
        assertTrue(getState().followedExpanded)
        composeRule.onNodeWithText("Ne plus suivre").performScrollTo().performClick()
        assertTrue(events.contains(SettingsEvent.Unfollow("bl")))
    }

    @Test
    fun changeGoogleAccount_showsToast() {
        val getState = setState()
        // Ordre de la maquette v2 : Mihon puis Compte, donc le 2e « Changer » est celui du compte Google.
        composeRule.onNodeWithText("vous@gmail.com").performScrollTo()
        composeRule.onAllNodes(hasText("Changer"))[1].performClick()
        assertEquals(SettingsToast.ChooseAccount, getState().toast)
        composeRule.onNodeWithText("Choix du compte Google").assertExists()
    }

    @Test
    fun changeMihonFolder_emitsEvent() {
        setState()
        composeRule.onNodeWithText("Dossier Mihon").performScrollTo().assertExists()
        composeRule.onNodeWithText("/Documents/Mihon").assertExists()
        composeRule.onAllNodes(hasText("Changer"))[0].performClick()
        assertTrue(events.contains(SettingsEvent.ChangeMihonFolder))
    }

    @Test
    fun reimportBackup_emitsEvent() {
        setState()
        composeRule.onNodeWithText("Sauvegarde Mihon").performScrollTo().assertExists()
        composeRule.onNodeWithText("Relire").performClick()
        assertTrue(events.contains(SettingsEvent.Reimport))
    }

    @Test
    fun cycleDriveRetention_updatesState() {
        val getState = setState()
        openAdvanced()
        composeRule.onNodeWithText("Jamais supprimer").performScrollTo().assertExists()

        // Maquette v2 : la ligne entiere (chevron) fait defiler les choix, plus de bouton « Modifier ».
        composeRule.onNodeWithText("Conservation sur Drive").performScrollTo().performClick()
        assertEquals(DriveRetention.Days30, getState().driveRetention)
        composeRule.onNodeWithText("Après 30 jours").assertExists()

        composeRule.onNodeWithText("Conservation sur Drive").performClick()
        assertEquals(DriveRetention.AfterDownload, getState().driveRetention)
        composeRule.onNodeWithText("Après récupération").assertExists()

        composeRule.onNodeWithText("Conservation sur Drive").performClick()
        assertEquals(DriveRetention.Never, getState().driveRetention)
        composeRule.onNodeWithText("Jamais supprimer").assertExists()
    }

    @Test
    fun advanced_showsDriveFolderAndDirectConnection() {
        val getState = setState()
        openAdvanced()
        assertTrue(getState().advancedExpanded)
        composeRule.onNodeWithText("Connexion directe (Tailscale)").performScrollTo().assertExists()
        composeRule.onNodeWithText("Non associée").assertExists()
        composeRule.onNodeWithText("Dossier Drive").performScrollTo().assertExists()
        composeRule.onNodeWithText("App/TranslateMyManga/").assertExists()
        // R12 : plus d'action « Récupérer depuis Google Drive ».
        composeRule.onNodeWithText("Récupérer depuis Google Drive").assertDoesNotExist()
        composeRule.onNodeWithText("Récupérer").assertDoesNotExist()

        // Replier masque de nouveau la liste.
        openAdvanced()
        assertFalse(getState().advancedExpanded)
        composeRule.onNodeWithText("Dossier Drive").assertDoesNotExist()
    }

    @Test
    fun advanced_pairedPc_showsAddressAndForget() {
        val getState = setState(MockupData.settings.copy(directStatus = DirectStatus.Direct, directUrl = "100.64.0.12:8765"))
        composeRule.onNodeWithText("Direct").assertExists()
        composeRule.onNodeWithText("TMM-Win 2.4.1 · connexion directe Tailscale, Google Drive en secours").assertExists()
        openAdvanced()
        // La ligne resume l'etat et ouvre le sous-ecran ; « Oublier ce PC » n'est que dans ce dernier.
        composeRule.onNodeWithText("Oublier ce PC").assertDoesNotExist()
        composeRule.onNodeWithText("Associée · 100.64.0.12:8765").performScrollTo().performClick()
        assertTrue(getState().directOpen)
        composeRule.onNodeWithText("100.64.0.12:8765").assertExists()
        composeRule.onNodeWithText("Oublier ce PC").performClick()
        assertTrue(events.contains(SettingsEvent.ForgetPc))
    }

    @Test
    fun directPage_notPaired_showsPairingFieldsAndGoesBack() {
        val getState = setState(MockupData.settings.copy(directOpen = true))
        composeRule.onNodeWithText("Non associée").assertExists()
        // Deux blocs d'appairage : le Hub, puis le PC en secours.
        composeRule.onAllNodesWithText("Adresse du PC").assertCountEquals(2)
        composeRule.onAllNodesWithText("Code à 6 chiffres").assertCountEquals(2)
        composeRule.onNodeWithText("PC (secours)").assertExists()
        composeRule.onNodeWithText("Oublier ce PC").assertDoesNotExist()
        composeRule.onNodeWithText("Réglages").performClick()
        assertFalse(getState().directOpen)
        composeRule.onNodeWithText("PC-BUREAU").assertExists()
    }

    @Test
    fun navigateLinks_callCallbacks() {
        setState()

        composeRule.onNodeWithText("Tous les réglages du PC").performScrollTo().performClick()
        assertTrue(translationSettingsNavigated)

        openAdvanced()
        composeRule.onNodeWithText("Refaire l'installation").performScrollTo().performClick()
        assertTrue(onboardingNavigated)
    }

    @Test
    fun toggleWifiOnly_togglesStateAndDesc() {
        val getState = setState()
        composeRule.onNodeWithText("Envois aussi en données mobiles").performScrollTo().assertExists()
        assertFalse(getState().wifiOnly)

        // Avancé replie : le seul interrupteur de l'ecran est celui du Wi-Fi.
        composeRule.onNode(isToggleable()).performClick()
        assertTrue(getState().wifiOnly)
        composeRule.onNodeWithText("Les envois attendent le Wi-Fi").assertExists()

        composeRule.onNode(isToggleable()).performClick()
        assertFalse(getState().wifiOnly)
        composeRule.onNodeWithText("Envois aussi en données mobiles").assertExists()
    }

    @Test
    fun exportLogs_emitsEvent() {
        setState()
        openAdvanced()
        composeRule.onNodeWithText("Journaux").performScrollTo().assertExists()
        composeRule.onNodeWithText("Exporter").performScrollTo().performClick()
        assertTrue(events.contains(SettingsEvent.ExportLogs))
    }

    // Avancé : limite d'installations simultanees (− / +) et bouton de deblocage avec son bilan.
    @Test
    fun advanced_concurrentInstallsStepperAndUnblockReport() {
        val getState = setState()
        openAdvanced()
        composeRule.onNodeWithText("Installations simultanées").performScrollTo().assertExists()
        composeRule.onNodeWithText("10 chapitres installés à la fois").assertExists()
        composeRule.onNodeWithText("+").performScrollTo().performClick()
        assertEquals(11, getState().maxConcurrentInstalls)
        composeRule.onNodeWithText("11 chapitres installés à la fois").assertExists()
        composeRule.onNodeWithText("−").performClick()
        assertEquals(10, getState().maxConcurrentInstalls)

        composeRule.onNodeWithText("Débloquer les téléchargements").performScrollTo().assertExists()
        composeRule.onNodeWithText("Relancer").performScrollTo().performClick()
        assertTrue(events.contains(SettingsEvent.UnblockDownloads))
        composeRule.onNodeWithText("Relancés : 2 · déjà actifs : 1").assertExists()
    }
}
