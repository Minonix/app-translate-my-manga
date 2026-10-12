package com.tmm.mobile.data.status

import androidx.annotation.StringRes
import com.tmm.mobile.R
import com.tmm.mobile.data.db.JobEntity

// docs/ERREURS_PROTOCOLE.md §1 : action proposee a l'utilisateur pour un code d'erreur.
enum class ErrorAction { RESEND, UPDATE_PC, RETRY, REDELIVER, RECONNECT_DRIVE, REPAIR, OPEN_PERMISSION, UPDATE_APP, NONE }

data class ErrorInfo(val code: String, @StringRes val label: Int, val action: ErrorAction)

/** Codes locaux au telephone (§1.2), stockes dans JobEntity.errorCode, jamais ecrits sur Drive. */
object LocalErrorCode {
    const val NETWORK = "network"
    const val AUTH = "auth"
    const val PAIRING = "pairing"
    const val DRIVE_QUOTA = "drive_quota"
    const val SOURCE_UNREADABLE = "source_unreadable"
    const val PERMISSION = "permission"
    const val PHONE_DISK_FULL = "phone_disk_full"
    const val DOWNLOAD = "download"
    const val RESULT_MISSING = "result_missing"
    const val INSTALL = "install"
    const val PC_STALLED = "pc_stalled"
    const val PROTOCOL_TOO_NEW = "protocol_too_new"
}

/**
 * Correspondance unique code -> (libelle, action) pour tous les codes de
 * `app/src/test/resources/protocol/error_codes.json`. `drive_quota` existe des deux cotes avec deux
 * actions differentes (PC : redeliver, telephone : retry), d'ou les tables separees.
 */
object ErrorCatalog {
    val pcJob: Map<String, ErrorInfo> = listOf(
        ErrorInfo("bad_request", R.string.error_pc_bad_request, ErrorAction.RESEND),
        ErrorInfo("protocol", R.string.error_pc_protocol, ErrorAction.UPDATE_PC),
        ErrorInfo("source_missing", R.string.error_pc_source_missing, ErrorAction.RESEND),
        ErrorInfo("integrity", R.string.error_pc_integrity, ErrorAction.RESEND),
        ErrorInfo("duplicate", R.string.error_pc_duplicate, ErrorAction.NONE),
        ErrorInfo("disk_full", R.string.error_pc_disk_full, ErrorAction.RETRY),
        ErrorInfo("model_missing", R.string.error_pc_model_missing, ErrorAction.NONE),
        ErrorInfo("api_key", R.string.error_pc_api_key, ErrorAction.RETRY),
        ErrorInfo("provider_quota", R.string.error_pc_provider_quota, ErrorAction.RETRY),
        ErrorInfo("engine_crash", R.string.error_pc_engine_crash, ErrorAction.RETRY),
        ErrorInfo("drive_quota", R.string.error_pc_drive_quota, ErrorAction.REDELIVER),
        ErrorInfo("job_lost", R.string.error_pc_job_lost, ErrorAction.RETRY),
        ErrorInfo("translation_failed", R.string.error_pc_translation_failed, ErrorAction.RETRY),
    ).associateBy { it.code }

    val phoneLocal: Map<String, ErrorInfo> = listOf(
        ErrorInfo(LocalErrorCode.NETWORK, R.string.error_local_network, ErrorAction.RETRY),
        ErrorInfo(LocalErrorCode.AUTH, R.string.error_local_auth, ErrorAction.RECONNECT_DRIVE),
        ErrorInfo(LocalErrorCode.PAIRING, R.string.error_local_pairing, ErrorAction.REPAIR),
        ErrorInfo(LocalErrorCode.DRIVE_QUOTA, R.string.error_local_drive_quota, ErrorAction.RETRY),
        ErrorInfo(LocalErrorCode.SOURCE_UNREADABLE, R.string.error_local_source_unreadable, ErrorAction.NONE),
        ErrorInfo(LocalErrorCode.PERMISSION, R.string.error_local_permission, ErrorAction.OPEN_PERMISSION),
        ErrorInfo(LocalErrorCode.PHONE_DISK_FULL, R.string.error_local_phone_disk_full, ErrorAction.RETRY),
        ErrorInfo(LocalErrorCode.DOWNLOAD, R.string.error_local_download, ErrorAction.REDELIVER),
        ErrorInfo(LocalErrorCode.RESULT_MISSING, R.string.error_local_result_missing, ErrorAction.REDELIVER),
        ErrorInfo(LocalErrorCode.INSTALL, R.string.error_local_install, ErrorAction.RETRY),
        ErrorInfo(LocalErrorCode.PC_STALLED, R.string.error_local_pc_stalled, ErrorAction.RETRY),
        ErrorInfo(LocalErrorCode.PROTOCOL_TOO_NEW, R.string.error_local_protocol_too_new, ErrorAction.UPDATE_APP),
    ).associateBy { it.code }

    /** Rejets de commande de reglages (§2, `lastCommandResult.code`) : libelle seul, pas d'action par job. */
    val settingsReject: Map<String, Int> = mapOf(
        "stale_version" to R.string.error_settings_stale_version,
        "invalid_value" to R.string.error_settings_invalid_value,
        "bad_request" to R.string.error_settings_bad_request,
    )

    fun unknown(code: String?): ErrorInfo = ErrorInfo(code.orEmpty(), R.string.error_unknown, ErrorAction.RETRY)

    fun forPc(code: String?): ErrorInfo = code?.let { pcJob[it] } ?: unknown(code)

    fun forLocal(code: String?): ErrorInfo = code?.let { phoneLocal[it] } ?: unknown(code)

    @StringRes
    fun settingsRejectLabel(code: String?): Int = code?.let { settingsReject[it] } ?: R.string.error_unknown

    /**
     * Erreur affichee pour un job en echec : un echec local (phoneState FAILED) prime, le PC n'ayant
     * alors pas pu avancer ; sinon le code publie par le PC (status.json, ou refus HTTP du canal
     * direct range dans pcErrorCode pour s'afficher pareil sur les deux canaux).
     */
    fun forJob(job: JobEntity): ErrorInfo = when {
        job.phoneState == "FAILED" && job.errorCode != null -> forLocal(job.errorCode)
        job.phoneState == "FAILED" && job.pcErrorCode != null -> forPc(job.pcErrorCode)
        job.pcState == "failed" -> forPc(job.pcErrorCode)
        // R8 : job SENT resté sans nouvelles du PC (errorCode pose par RefreshCoordinator).
        job.errorCode == LocalErrorCode.PC_STALLED -> forLocal(job.errorCode)
        else -> unknown(job.errorCode ?: job.pcErrorCode)
    }
}
