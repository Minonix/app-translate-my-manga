package com.tmm.mobile.ui.screens.queue

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.tmm.mobile.data.status.ErrorAction

/** Onglets de l'écran Activité (maquette v2, écran 7). */
enum class QueueTab { Active, Done, Fail }

/** Type de travail : « Traduction 42 % » en cours, « Correction des oublis » dans Ensuite. */
enum class QueueJobType { Translation, Verification, Retranslation }

/** Chapitre que le PC traduit en ce moment (heartbeat.activeJobIds). */
data class ActiveJob(
    val seriesKey: String,
    val coverUrl: String?,
    val title: String,
    val color: Color,
    val type: QueueJobType,
    val currentPage: Int,
    val totalPages: Int,
    /** Index dans Lecture, Traduction, Lettrage, Vérification. */
    val currentStepIndex: Int,
    val engine: String,
) {
    val percent: Int get() = if (totalPages > 0) Math.round(currentPage * 100f / totalPages) else 0
}

/** Transfert en cours : envoi vers le PC ou retour vers Mihon. */
data class TransferRow(
    val title: String,
    val jobId: String = "",
    val isUpload: Boolean,
    val doneBytes: Long,
    val totalBytes: Long,
) {
    val percent: Int get() = if (totalBytes > 0) ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

/** Chapitre de la section « Ensuite ». */
data class WaitingJob(
    val id: String,
    val seriesKey: String,
    val coverUrl: String?,
    val title: String,
    val color: Color,
    val type: QueueJobType,
    /** Parti tout seul grâce au suivi automatique de la série. */
    val auto: Boolean,
)

data class DoneJobAlert(
    val page: Int,
    val code: String,
    /** Une correction de cette page est déjà en cours. */
    val isChecked: Boolean,
)

data class DoneJob(
    val mihonPath: String,
    val seriesKey: String,
    val coverUrl: String?,
    val title: String,
    /** « Il y a 12 min », « Hier ». */
    val whenText: String,
    /** Dans Mihon, sinon prête à installer. */
    val installed: Boolean,
    val color: Color,
    val totalPages: Int,
    val translatedPages: Int,
    val totalBubbles: Int,
    val untranslatedBubbles: Int,
    val sfxCount: Int,
    val alerts: List<DoneJobAlert>,
    val isExpanded: Boolean,
)

data class FailedJob(
    val jobId: String,
    val seriesKey: String,
    val coverUrl: String?,
    val title: String,
    val color: Color,
    val page: Int?,
    /** Libelle du code d'erreur (ErrorCatalog), seul texte principal (docs/ERREURS_PROTOCOLE.md 1.3). */
    @StringRes val label: Int,
    /** « Gemini · les pages 1 à 11 sont conservées ». */
    val keptInfo: String,
    /** Quota du moteur atteint (provider_quota) : l'astuce « Secours LLM local » s'affiche. */
    val quota: Boolean = false,
    /** Message brut du PC ou du telephone, en detail secondaire seulement. */
    val detail: String? = null,
    /** Bouton propose selon le code (ErrorCatalog). */
    val action: ErrorAction = ErrorAction.RETRY,
    /** La relance reprend a la page d'echec (verify/retranslate) ; en translate tout le chapitre repart. */
    val resumeAtPage: Boolean = false,
)

data class QueueState(
    val activeTab: QueueTab = QueueTab.Active,
    val slotsCount: Int = 0,
    val slotsMax: Int = 4,
    val pcName: String = "PC",
    val focusedJobIndex: Int = 0,
    val activeJobs: List<ActiveJob> = emptyList(),
    val transfers: List<TransferRow> = emptyList(),
    val waitingJobs: List<WaitingJob> = emptyList(),
    val doneJobs: List<DoneJob> = emptyList(),
    val failedJobs: List<FailedJob> = emptyList(),
) {
    val activeCount: Int get() = activeJobs.size + transfers.size + waitingJobs.size
    val doneCount: Int get() = doneJobs.size
    val failCount: Int get() = failedJobs.size
    val focused: ActiveJob? get() = activeJobs.getOrNull(focusedJobIndex)
    val uploadCount: Int get() = transfers.count { it.isUpload }
}

sealed interface QueueEvent {
    data class SelectTab(val tab: QueueTab) : QueueEvent
    data class FocusJob(val index: Int) : QueueEvent
    data class RemoveWaitingJob(val id: String) : QueueEvent
    data class ToggleDoneJobDetails(val index: Int) : QueueEvent
    data class VerifyPage(val doneIndex: Int, val page: Int) : QueueEvent
    data class RetryFailedJob(val jobId: String) : QueueEvent

    /** « Tout annuler » sur « Ensuite » : chaque chapitre en attente, comme la croix de sa ligne. */
    data object CancelAllWaiting : QueueEvent

    /** Annule les envois vers le PC en cours (pas les retours vers Mihon). */
    data object CancelAllUploads : QueueEvent
}
