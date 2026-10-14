package com.tmm.mobile.ui.screens.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.tmm.mobile.R
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Verifications comportementales de l'Accueil v2 (maquettes/v2-simplifiee/index.html, section
 * « 1 · Accueil ») : etat nominal, « Traduire » qui ouvre la feuille, « Installer » qui lance la
 * recuperation, liens de navigation, PC eteint / vu, etat vide, actualisation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainScreenBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // Valeurs de la maquette v2 (ecran 1), partagees avec MainScreenshotTest.
    private val mockup = MockupData.main

    private val empty = mockup.copy(
        toSendCount = 0, toSendSeriesCount = 0, toGetCount = 0, toGetSummary = "",
        queueCount = 0, queueParallelCount = 0, queueNextCount = 0, queueJobs = emptyList(),
        recentChapters = emptyList(), followedSeries = emptyList(),
    )

    private class Calls {
        val events = mutableListOf<MainEvent>()
        var translate = 0
        var queue = 0
        var manage = 0
        val series = mutableListOf<String>()
    }

    private fun setState(initial: MainState): Pair<() -> MainState, Calls> {
        var state by mutableStateOf(initial)
        val calls = Calls()
        composeRule.setContent {
            TmmTheme {
                MainScreen(
                    state = state,
                    onEvent = { event -> calls.events += event; state = reduce(state, event) },
                    onOpenQueue = { calls.queue++ },
                    onOpenSeries = { calls.series += it },
                    onOpenTranslate = { calls.translate++ },
                    onManageFollowed = { calls.manage++ },
                )
            }
        }
        return Pair({ state }, calls)
    }

    // Reducteur minimal : simule le debut d'une installation comme le ferait MainViewModel.
    private fun reduce(state: MainState, event: MainEvent): MainState = when (event) {
        MainEvent.FetchTranslated -> state.copy(getState = BusyState.Busy, getFraction = 0f, installTotal = state.toGetCount)
        MainEvent.DismissToast -> state.copy(toast = null)
        MainEvent.SendOriginals -> state.copy(toast = MainToast.SentOriginals(state.toSendCount))
        MainEvent.Refresh, MainEvent.RefreshFinished -> state
    }

    @Test
    fun initialState_showsMockupTexts() {
        setState(mockup)
        listOf(
            "TMM", "PC allumé", "Accueil", "Mihon vérifié il y a 4 min",
            "À FAIRE", "11", "chapitres\nà traduire", "dans 4 séries", "Traduire",
            "2", "traductions\nà installer", "Frieren, One Piece", "Installer",
            "3 séries suivies", "Gérer", "Leurs nouveaux chapitres partent en traduction à chaque ouverture de TMM.",
            "Sur le PC", "Activité (7)", "Solo Leveling · ch. 201", "73 %", "Traduction · page 16 sur 22",
            "Kagurabachi · ch. 52", "42 %", "Traduction · page 9 sur 21",
            "+ 5 en attente, 4 traduits en même temps sur PC-BUREAU",
            "Derniers traduits", "Tout voir", "ch. 140 · dans Mihon", "ch. 212 · dans Mihon", "ch. 1120 · prêt",
        ).forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithContentDescription("À faire").assertExists()
    }

    // Demande utilisateur : « Envoyer » part sans ouvrir la feuille Traduire, et annonce l'envoi sans traduction.
    @Test
    fun sendOriginals_sendsWithoutOpeningTranslateSheet() {
        val (_, calls) = setState(mockup)
        composeRule.onNodeWithText("Envoyer").performClick()
        composeRule.waitForIdle()
        assertEquals(0, calls.translate)
        assertEquals(listOf<MainEvent>(MainEvent.SendOriginals), calls.events)
        composeRule.onNodeWithText("envoyé", substring = true).assertExists()
    }

    @Test
    fun translate_opensTranslateSheetWithoutEvent() {
        val (_, calls) = setState(mockup)
        composeRule.onNodeWithText("Traduire").performClick()
        composeRule.waitForIdle()
        assertEquals(1, calls.translate)
        assertEquals(emptyList<MainEvent>(), calls.events)
    }

    @Test
    fun install_sendsFetchTranslatedAndShowsProgressPill() {
        val (currentState, calls) = setState(mockup)
        composeRule.onNodeWithText("Installer").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<MainEvent>(MainEvent.FetchTranslated), calls.events)
        assertEquals(BusyState.Busy, currentState().getState)
        composeRule.onNodeWithText("0 / 2").assertExists()
        composeRule.onNodeWithText("Installer").assertDoesNotExist()
    }

    @Test
    fun todoCard_staysVisibleWhileInstalling_evenWithZeroCounts() {
        setState(mockup.copy(toSendCount = 0, toGetCount = 0, getState = BusyState.Busy, installTotal = 2))
        composeRule.onNodeWithContentDescription("À faire").assertExists()
        composeRule.onNodeWithText("Traduire").assertDoesNotExist()
    }

    @Test
    fun navigationLinks_callTheirCallbacks() {
        val (_, calls) = setState(mockup)
        composeRule.onNodeWithText("Gérer").performClick()
        composeRule.onNodeWithText("Kagurabachi").performClick()
        composeRule.onNodeWithText("Activité (7)").performScrollTo().performClick()
        composeRule.onNodeWithText("Tout voir").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, calls.manage)
        assertEquals(2, calls.queue)
        assertEquals(listOf("kagurabachi"), calls.series)
    }

    @Test
    fun refresh_sendsRefreshEvent() {
        val (_, calls) = setState(mockup)
        // Horloge figee : l'icone qui tourne est une animation infinie.
        composeRule.mainClock.autoAdvance = false
        // Le bouton rond porte sa legende : sans elle, la synchronisation ne se trouvait pas.
        composeRule.onNodeWithText("Synchroniser").assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.common_refresh_cd)).performClick()
        assertEquals(listOf<MainEvent>(MainEvent.Refresh), calls.events)
    }

    @Test
    fun offline_showsPillAndNotice() {
        setState(mockup.copy(pcTone = PcTone.Offline, offlineSinceLabel = "depuis 2 jours"))
        composeRule.onNodeWithText("PC éteint").assertExists()
        composeRule.onNodeWithText("PC éteint depuis 2 jours.").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.home_offline_rest)).assertExists()
    }

    @Test
    fun offline_withoutKnownHeartbeat_hidesOfflineNotice() {
        setState(mockup.copy(pcTone = PcTone.Offline, offlineSinceLabel = null))
        composeRule.onNodeWithText("PC éteint").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.home_offline_rest)).assertDoesNotExist()
    }

    @Test
    fun online_hidesOfflineNotice() {
        setState(mockup)
        composeRule.onNodeWithText(context.getString(R.string.home_offline_rest)).assertDoesNotExist()
    }

    @Test
    fun seen_showsSinceLabel() {
        setState(mockup.copy(pcTone = PcTone.Seen, pcSeenSinceLabel = "12 min"))
        composeRule.onNodeWithText("PC vu il y a 12 min").assertExists()
    }

    @Test
    fun emptyHome_hidesEverySectionButHeader() {
        setState(empty.copy(lastCheckLabel = null))
        composeRule.onNodeWithText("Accueil").assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.common_refresh_cd)).assertExists()
        composeRule.onNodeWithText("Mihon vérifié", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("À faire").assertDoesNotExist()
        composeRule.onNodeWithText("Gérer").assertDoesNotExist()
        composeRule.onNodeWithText("Sur le PC").assertDoesNotExist()
        composeRule.onNodeWithText("Derniers traduits").assertDoesNotExist()
        composeRule.onNodeWithText("manquant partout", substring = true).assertDoesNotExist()
    }

    @Test
    fun toast_receivedShowsFormattedCount() {
        setState(mockup.copy(toast = MainToast.Received(8)))
        composeRule.onNodeWithText("8 chapitres traduits rangés dans Mihon/local.").assertExists()
    }
}
