package com.tmm.mobile.ui.screens.onboarding

data class OnboardingSubfolder(
    val name: String,
    val role: String,
    val found: Boolean = true,
)

data class OnboardingState(
    val step: Int = 1,
    val isWorking: Boolean = false,
    val isGoogleConnected: Boolean = false,
    // Vides tant que Drive ne les a pas confirmes (avant : valeurs de maquette affichees meme en echec).
    val googleAccount: String = "",
    val sharedFolderName: String = "",
    val pcLastSeen: String = "",
    val isMihonSelected: Boolean = false,
    val mihonFolderPath: String = "/Documents/Mihon",
    val subfolders: List<OnboardingSubfolder> = listOf(
        OnboardingSubfolder("downloads/", "Chapitres téléchargés, lus par l’app"),
        OnboardingSubfolder("local/", "Traductions rangées ici"),
        OnboardingSubfolder("autobackup/", "Sauvegardes, pour vos catégories"),
    ),
    val readProgress: Float = 0f,
    val hasBackup: Boolean = true,
    val backupFileName: String = "autobackup/com.mihon_2026-09-26.tachibk",
    val seriesCount: Int = 0,
    val categoriesCount: Int = 0,
    val downloadedCount: Int = 0,
    val isReadDone: Boolean = false,
    // PLAN_GEMINI.md 7.10 etape 3 : nombre de chapitres TO_SEND apres import ; 0 = message masque.
    // Valeur par defaut alignee sur l'ancien texte fige de la maquette pour ne pas casser les
    // captures Roborazzi de MockupData.onboarding (aucun champ ne change de rendu par defaut).
    val toSendCount: Int = 12,
)

sealed interface OnboardingEvent {
    data object PrimaryAction : OnboardingEvent
    data object PreviousStep : OnboardingEvent
}
