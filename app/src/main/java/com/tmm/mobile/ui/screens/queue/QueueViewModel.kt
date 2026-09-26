package com.tmm.mobile.ui.screens.queue

import androidx.lifecycle.ViewModel
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel synchrone / testable de l'écran File (E4.5, Queue.dc.html).
 */
class QueueViewModel(initialState: QueueState = MockupData.queue) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<QueueState> = _state

    fun onEvent(event: QueueEvent) {
        when (event) {
            is QueueEvent.SelectTab -> _state.update {
                it.copy(activeTab = event.tab)
            }
            is QueueEvent.FocusJob -> _state.update {
                it.copy(focusedJobIndex = event.index)
            }
            is QueueEvent.RemoveWaitingJob -> _state.update { current ->
                current.copy(
                    waitingJobs = current.waitingJobs.filterNot { it.id == event.id }
                )
            }
            is QueueEvent.ToggleDoneJobDetails -> _state.update { current ->
                val updated = current.doneJobs.mapIndexed { index, job ->
                    if (index == event.index) job.copy(isExpanded = !job.isExpanded) else job
                }
                current.copy(doneJobs = updated)
            }
            is QueueEvent.VerifyPage -> _state.update { current ->
                val targetJob = current.doneJobs.getOrNull(event.doneIndex) ?: return@update current
                val updatedJobs = current.doneJobs.mapIndexed { index, job ->
                    if (index == event.doneIndex) {
                        job.copy(
                            alerts = job.alerts.map { alert ->
                                if (alert.page == event.page) alert.copy(isChecked = true) else alert
                            }
                        )
                    } else job
                }
                val newWaitingItem = WaitingJob(
                    id = "c_${targetJob.title}_${event.page}",
                    position = String.format("%02d", current.waitingJobs.size + 5),
                    title = "${targetJob.title} · p. ${event.page}",
                    color = TmmColors.CECE7DC,
                    source = "Vérification · page ciblée",
                    isPhone = false,
                )
                current.copy(
                    doneJobs = updatedJobs,
                    waitingJobs = current.waitingJobs + newWaitingItem,
                )
            }
            is QueueEvent.RetryFailedJob -> _state.update { current ->
                val failed = current.failedJob
                val newWaitingList = if (failed != null && !current.isRetried) {
                    current.waitingJobs + WaitingJob(
                        id = "f139",
                        position = String.format("%02d", current.waitingJobs.size + 5),
                        title = failed.title,
                        color = failed.color,
                        source = "Reprise page ${failed.page}",
                        isPhone = false,
                    )
                } else current.waitingJobs
                current.copy(
                    isRetried = true,
                    waitingJobs = newWaitingList,
                )
            }
        }
    }
}
