package com.tmm.mobile.ui.screens.transfers

import androidx.compose.ui.graphics.Color

data class LiveTransferItem(
    val title: String,
    val info: String,
    val progress: Float,
    val isUpload: Boolean,
)

data class SyncSeriesItem(
    val title: String,
    val chapters: String,
    val color: Color,
    val size: String,
)

data class SyncFetchItem(
    val title: String,
    val chapters: String,
    val color: Color,
    val size: String,
    val isDone: Boolean = false,
)

sealed interface SendState {
    data object Idle : SendState
    data class Busy(val progress: Float) : SendState
    data class Done(val chapterCount: Int) : SendState
}

sealed interface FetchState {
    data object Idle : FetchState
    data class Busy(val downloaded: Int, val total: Int) : FetchState
    data object Done : FetchState
}

data class SyncState(
    val pcName: String = "PC-BUREAU",
    val upCount: Int = 12,
    val downCount: Int = 8,
    val liveTransfers: List<LiveTransferItem> = emptyList(),
    val sendState: SendState = SendState.Idle,
    val fetchState: FetchState = FetchState.Idle,
    val upSeries: List<SyncSeriesItem> = emptyList(),
    val otherSeriesCount: Int = 3,
    val otherChaptersCount: Int = 8,
    val autoTranslate: Boolean = false,
    val downSeries: List<SyncFetchItem> = emptyList(),
    val scanning: Boolean = false,
    val scannedAgo: String = "il y a 4 min",
    val missingCount: Int = 2,
    // PLAN_GEMINI.md 7.4 : tap sur la carte manquants -> Fiche serie de la premiere serie concernee.
    val missingSeriesKey: String? = null,
)

sealed interface SyncEvent {
    data object ToggleAutoTranslate : SyncEvent
    data object SendUp : SyncEvent
    data object GetDown : SyncEvent
    data object Rescan : SyncEvent
}
