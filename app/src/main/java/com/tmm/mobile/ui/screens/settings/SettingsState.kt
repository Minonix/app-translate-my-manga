package com.tmm.mobile.ui.screens.settings

enum class DriveRetention(val index: Int) {
    Never(0),
    Days30(1),
    AfterDownload(2);

    fun next(): DriveRetention = when (this) {
        Never -> Days30
        Days30 -> AfterDownload
        AfterDownload -> Never
    }
}

sealed interface SettingsToast {
    data object ChooseAccount : SettingsToast
    data object FolderPicker : SettingsToast
    data object LogsReady : SettingsToast
    data object Paired : SettingsToast
    data object PairFailed : SettingsToast
}

// Connexion directe (Tailscale) : etat affiche dans les Reglages.
enum class DirectStatus { Direct, Unreachable, NotPaired, Expired }

data class FollowedSeries(val key: String, val title: String)

data class SettingsState(
    val advancedExpanded: Boolean = false,
    // Sous-ecran « Connexion directe (Tailscale) » ouvert depuis Avance (maquette v2, ecran 8).
    val directOpen: Boolean = false,
    val followedExpanded: Boolean = false,
    val followedSeries: List<FollowedSeries> = emptyList(),
    val backupFileName: String = "",
    val importStep: BackupImportStep = BackupImportStep.Idle,
    val importError: Boolean = false,
    val languageSummary: String = "Détection automatique vers français",
    val engineSummary: String = "Google Gemini 2.5 Flash",
    val pcName: String = "PC-BUREAU",
    val pcVersionProtocol: String = "TMM-Win 2.4.1 · protocole v1",
    val isPcOnline: Boolean = true,
    // Maquette v2 (section 8) : ligne complete deja formatee ("Allumé · vu il y a 3 min", ou
    // "Aucun signal reçu" sans heartbeat).
    val pcLastSignalText: String = "Allumé · vu il y a 17 s",
    val googleAccount: String = "vous@gmail.com",
    val sharedFolder: String = "App/TranslateMyManga/",
    val driveRetention: DriveRetention = DriveRetention.Never,
    val mihonFolder: String = "/Documents/Mihon",
    val lastBackupInfo: String = "Aujourd'hui 03:00 · 142 séries",
    val pcTranslationSummary: String = "Gemini · japonais vers français · 4 en parallèle",
    val wifiOnly: Boolean = false,
    val logsInfo: String = "7 derniers jours · 240 Ko",
    val toast: SettingsToast? = null,
    val directStatus: DirectStatus = DirectStatus.NotPaired,
    // Adresse du PC : publiee par le PC via Drive (pc/remote.json) ou saisie a la main.
    val directUrl: String = "",
    val pairingCode: String = "",
    val pairing: Boolean = false,
)

sealed interface SettingsEvent {
    data object ToggleAdvanced : SettingsEvent
    data object OpenDirect : SettingsEvent
    data object CloseDirect : SettingsEvent
    data object ToggleFollowed : SettingsEvent
    data class Unfollow(val seriesKey: String) : SettingsEvent
    data object Reimport : SettingsEvent
    data object ChangeGoogleAccount : SettingsEvent
    data object CycleDriveRetention : SettingsEvent
    data object ChangeMihonFolder : SettingsEvent
    data class SetWifiOnly(val enabled: Boolean) : SettingsEvent
    data object ToggleWifiOnly : SettingsEvent
    data object ExportLogs : SettingsEvent
    data object DismissToast : SettingsEvent
    data class DirectUrlChanged(val url: String) : SettingsEvent
    data class PairingCodeChanged(val code: String) : SettingsEvent
    data object Pair : SettingsEvent
    data object ForgetPc : SettingsEvent
}
