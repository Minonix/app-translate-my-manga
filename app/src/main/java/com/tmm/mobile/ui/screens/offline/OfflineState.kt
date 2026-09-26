package com.tmm.mobile.ui.screens.offline

import androidx.compose.ui.graphics.Color
import com.tmm.mobile.ui.theme.TmmColors

enum class OfflineCause {
    Network,
    Token,
}

data class OfflinePendingItem(
    val title: String,
    val color: Color,
    val why: String? = null,
)

data class OfflineState(
    val cause: OfflineCause = OfflineCause.Network,
    val isReconnecting: Boolean = false,
    val pcStateText: String = "État du PC inconnu",
    val pendingCount: Int = 3,
    val pendingItems: List<OfflinePendingItem> = listOf(
        OfflinePendingItem("Kagurabachi · Ch. 52 à 53", TmmColors.CCFD8C6),
        OfflinePendingItem("Blue Lock · Ch. 214", TmmColors.CD8D2E4),
    ),
    val transferBadgeCount: Int = 20,
)

sealed interface OfflineEvent {
    data object Reconnect : OfflineEvent
}
