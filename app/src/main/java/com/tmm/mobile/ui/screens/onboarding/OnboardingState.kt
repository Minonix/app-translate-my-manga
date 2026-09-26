package com.tmm.mobile.ui.screens.onboarding

data class OnboardingSubfolder(
    val name: String,
    val role: String,
)

data class OnboardingState(
    val step: Int = 1,
    val isWorking: Boolean = false,
    val isGoogleConnected: Boolean = false,
    val googleAccount: String = "vous@gmail.com",
    val sharedFolderName: String = "TranslateMyManga/",
    val pcLastSeen: String = "2 min",
    val isMihonSelected: Boolean = false,
    val mihonFolderPath: String = "/Documents/Mihon",
    val subfolders: List<OnboardingSubfolder> = listOf(
        OnboardingSubfolder("downloads/", "Chapitres téléchargés, lus par l’app"),
        OnboardingSubfolder("local/", "Traductions rangées ici"),
        OnboardingSubfolder("autobackup/", "Sauvegardes, pour vos catégories"),
    ),
    val readProgress: Float = 0f,
    val backupFileName: String = "autobackup/com.mihon_2026-09-26.tachibk",
    val seriesCount: Int = 0,
    val categoriesCount: Int = 0,
    val downloadedCount: Int = 0,
    val isReadDone: Boolean = false,
)

sealed interface OnboardingEvent {
    data object PrimaryAction : OnboardingEvent
    data object PreviousStep : OnboardingEvent
    data object AdvanceSimulation : OnboardingEvent
}
