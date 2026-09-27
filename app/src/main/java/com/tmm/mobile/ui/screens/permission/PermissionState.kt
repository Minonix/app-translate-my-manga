package com.tmm.mobile.ui.screens.permission

data class PermissionState(
    val isBusy: Boolean = false,
    val isFixed: Boolean = false,
    val expectedPath: String = "/Documents/Mihon",
    // PLAN_GEMINI.md 7.13 : "Ce dossier ne contient pas downloads/." (ajoute), affiche uniquement
    // apres un echec de verification ; pas de nouvel essai automatique.
    val errorMessage: String? = null,
)

sealed interface PermissionEvent {
    data object PickFolder : PermissionEvent
}
