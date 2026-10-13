package com.tmm.mobile.ui.screens.empty

data class EmptyState(
    val isBusy: Boolean = false,
    val isDone: Boolean = false,
    // Vide = pastille masquee : aucune vraie valeur n'est connue sur cet ecran (avant : texte de maquette).
    val pcSeenText: String = "",
    val transferBadgeCount: Int = 20,
)

sealed interface EmptyEvent {
    data object ScanMihon : EmptyEvent
}
