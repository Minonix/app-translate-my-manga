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

data class SettingsState(
    val pcName: String = "PC-BUREAU",
    val pcVersionProtocol: String = "TMM-Win 2.4.1 · protocole v1",
    val isPcOnline: Boolean = true,
    // PLAN_GEMINI.md 7.8 : ligne complete deja formatee (relativeFr ou "Aucun signal recu" sans heartbeat).
    val pcLastSignalText: String = "Dernier signal il y a 12 s · le PC écrit sur Drive toutes les minutes",
    val googleAccount: String = "vous@gmail.com",
    val sharedFolder: String = "TranslateMyManga/",
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
