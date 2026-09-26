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
)

data class MainRecentChapter(
    val title: String,
    val chapterLabel: String,
    val color: Color,
    val inMihon: Boolean,
    val onPc: Boolean,
)

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu (regle 4.10). */
sealed interface MainToast {
    data class Sent(val count: Int) : MainToast
    data class Received(val count: Int) : MainToast
}

data class MainState(
    val pcTone: PcTone = PcTone.Online,
    val pcSeenSinceLabel: String? = null,
    val lastCheckLabel: String,
    val toSendCount: Int,
    val toSendSummary: String,
    val sendState: BusyState = BusyState.Idle,
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
) {
    val isOffline: Boolean get() = pcTone == PcTone.Offline
}

sealed interface MainEvent {
    data object Refresh : MainEvent
    data object RefreshFinished : MainEvent
    data object SendPending : MainEvent
    data object FetchTranslated : MainEvent
    data object DismissToast : MainEvent
}
