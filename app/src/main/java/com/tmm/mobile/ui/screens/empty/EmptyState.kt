package com.tmm.mobile.ui.screens.empty

data class EmptyState(
    val isBusy: Boolean = false,
    val isDone: Boolean = false,
    val pcSeenText: String = "PC vu il y a 3 h",
    val transferBadgeCount: Int = 20,
)

sealed interface EmptyEvent {
    data object ScanMihon : EmptyEvent
}
