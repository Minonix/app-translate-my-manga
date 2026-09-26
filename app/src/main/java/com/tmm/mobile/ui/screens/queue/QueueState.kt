package com.tmm.mobile.ui.screens.queue

import androidx.compose.ui.graphics.Color
import com.tmm.mobile.ui.theme.TmmColors

enum class QueueTab {
    Active, Done, Fail
}

enum class QueueJobType(val label: String, val bg: Color, val fg: Color) {
    Translation("Traduction", TmmColors.CC8391F, TmmColors.CFFFFFF),
    Verification("Vérification", TmmColors.C2F7D4F, TmmColors.CFFFFFF),
    Retranslation("Retraduction", TmmColors.CF4F1EA, TmmColors.C16161A),
}

data class ActiveJob(
    val title: String,
    val color: Color,
    val type: QueueJobType,
    val currentPage: Int,
    val totalPages: Int,
    val currentStepIndex: Int, // 0..4 (fetch, ocr, translate, lettering, verify)
    val extra: String,
) {
    val progressPercent: Int get() = if (totalPages > 0) ((currentPage.toFloat() / totalPages) * 100).toInt() else 0
}

data class WaitingJob(
    val id: String,
    val position: String,
    val title: String,
    val color: Color,
    val source: String,
    val isPhone: Boolean,
)

data class DoneJobAlert(
    val page: Int,
    val code: String, // source_copy, source_text, overflow, too_small
    val isChecked: Boolean = false,
)

data class DoneJob(
    val title: String,
    val whenText: String,
    val stateText: String,
    val stateColor: Color,
    val color: Color,
    val totalPages: Int,
    val translatedPages: Int,
    val totalBubbles: Int,
    val untranslatedBubbles: Int,
    val sfxCount: Int,
    val alerts: List<DoneJobAlert> = emptyList(),
    val isExpanded: Boolean = false,
)

data class FailedJob(
    val title: String,
    val color: Color,
    val page: Int,
    val message: String,
    val keptInfo: String,
)

data class QueueState(
    val activeTab: QueueTab = QueueTab.Active,
    val slotsCount: Int = 4,
    val slotsMax: Int = 4,
    val pcName: String = "PC-BUREAU",
    val focusedJobIndex: Int = 0,
    val activeJobs: List<ActiveJob> = emptyList(),
    val waitingJobs: List<WaitingJob> = emptyList(),
    val doneJobs: List<DoneJob> = emptyList(),
    val failedJob: FailedJob? = null,
    val isRetried: Boolean = false,
) {
    val activeCount: Int get() = activeJobs.size + waitingJobs.size
    val doneCount: Int get() = doneJobs.size
    val failCount: Int get() = if (failedJob != null && !isRetried) 1 else 0
}

sealed interface QueueEvent {
    data class SelectTab(val tab: QueueTab) : QueueEvent
    data class FocusJob(val index: Int) : QueueEvent
    data class RemoveWaitingJob(val id: String) : QueueEvent
    data class ToggleDoneJobDetails(val index: Int) : QueueEvent
    data class VerifyPage(val doneIndex: Int, val page: Int) : QueueEvent
    data object RetryFailedJob : QueueEvent
}
