package com.tmm.mobile.ui.screens.permission

data class PermissionState(
    val isBusy: Boolean = false,
    val isFixed: Boolean = false,
    val expectedPath: String = "/Documents/Mihon",
)

sealed interface PermissionEvent {
    data object PickFolder : PermissionEvent
}
