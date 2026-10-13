package com.tmm.mobile.ui.screens.main

import androidx.compose.ui.graphics.Color

/** Trois états possibles du PC compagnon (Main.dc.html, prop pcState). */
enum class PcTone { Online, Seen, Offline }

/** Etat d'une action a deux temps (bouton -> spinner/barre -> pastille "fait"). */
enum class BusyState { Idle, Busy, Done }

/** Type d'un job de la file (Main.dc.html : libelle + couleurs derives du type au rendu). */
enum class MainJobType { Translation, Verification, Retranslation }

data class MainQueueJob(
    val title: String,
    val color: Color,
    val type: MainJobType,
    val progressFraction: Float,
    val page: Int? = null,
    val pages: Int? = null,
)

data class MainFollowedSeries(val seriesKey: String, val title: String)

data class MainRecentChapter(
    val title: String,
    val chapterLabel: String,
    val color: Color,
    val inMihon: Boolean,
    val onPc: Boolean,
    val seriesKey: String,
)

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu (regle 4.10). */
sealed interface MainToast {
    data class Received(val count: Int) : MainToast
}

data class MainState(
    val pcTone: PcTone = PcTone.Online,
    val pcSeenSinceLabel: String? = null,
    // PLAN_GEMINI.md 7.1 : masque si jamais rafraichi (last_refresh_at absent).
    val lastCheckLabel: String? = null,
    // PLAN_GEMINI.md 7.1 : bandeau "PC hors ligne depuis..." - masque sans heartbeat connu.
    val offlineSinceLabel: String? = null,
    val toSendCount: Int,
    // Nombre de series concernees, rendu « dans N series » par un pluriel Android.
    val toSendSeriesCount: Int,
    val toGetCount: Int,
    val toGetSummary: String,
    val getState: BusyState = BusyState.Idle,
    val getFraction: Float = 0f,
    val queueCount: Int,
    val queueParallelCount: Int,
    val queueMachineName: String,
    val queueNextCount: Int,
    val queueJobs: List<MainQueueJob>,
    val lastScanLabel: String,
    val recentChapters: List<MainRecentChapter>,
    val toast: MainToast? = null,
    val followedSeries: List<MainFollowedSeries> = emptyList(),
    val installTotal: Int = 0,
) {
    val isOffline: Boolean get() = pcTone == PcTone.Offline
}

sealed interface MainEvent {
    data object Refresh : MainEvent
    data object RefreshFinished : MainEvent
    data object FetchTranslated : MainEvent
    data object DismissToast : MainEvent
}
