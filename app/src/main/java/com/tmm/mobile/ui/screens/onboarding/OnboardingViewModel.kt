package com.tmm.mobile.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(initial: OnboardingState = OnboardingState()) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var job: Job? = null

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.PrimaryAction -> handlePrimaryAction()
            OnboardingEvent.PreviousStep -> handlePreviousStep()
            OnboardingEvent.AdvanceSimulation -> handleAdvanceSimulation()
        }
    }

    private fun handlePrimaryAction() {
        val current = _state.value
        if (current.step == 1 && !current.isGoogleConnected) {
            _state.update { it.copy(isWorking = true) }
            job?.cancel()
            job = viewModelScope.launch {
                delay(1200)
                _state.update { it.copy(isWorking = false, isGoogleConnected = true) }
            }
            return
        }
        if (current.step == 1) {
            _state.update { it.copy(step = 2) }
            return
        }
        if (current.step == 2 && !current.isMihonSelected) {
            _state.update { it.copy(isWorking = true) }
            job?.cancel()
            job = viewModelScope.launch {
                delay(900)
                _state.update { it.copy(isWorking = false, isMihonSelected = true) }
            }
            return
        }
        if (current.step == 2) {
            _state.update { it.copy(step = 3) }
            startReadingBackup()
        }
    }

    private fun handlePreviousStep() {
        if (_state.value.isWorking) return
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }
    }

    private fun startReadingBackup() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(350)
            for (i in 1..20) {
                delay(60)
                val fraction = i / 20f
                _state.update {
                    it.copy(
                        readProgress = fraction,
                        seriesCount = (142 * fraction).toInt(),
                        categoriesCount = (7 * fraction).toInt(),
                        downloadedCount = (104 * fraction).toInt(),
                        isReadDone = i == 20,
                    )
                }
            }
        }
    }

    private fun handleAdvanceSimulation() {
        val current = _state.value
        if (current.step == 1 && !current.isGoogleConnected) {
            _state.update { it.copy(isGoogleConnected = true, isWorking = false) }
        } else if (current.step == 1) {
            _state.update { it.copy(step = 2) }
        } else if (current.step == 2 && !current.isMihonSelected) {
            _state.update { it.copy(isMihonSelected = true, isWorking = false) }
        } else if (current.step == 2) {
            _state.update {
                it.copy(
                    step = 3,
                    readProgress = 1f,
                    seriesCount = 142,
                    categoriesCount = 7,
                    downloadedCount = 104,
                    isReadDone = true,
                )
            }
        }
    }
}
