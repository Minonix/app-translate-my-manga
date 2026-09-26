package com.tmm.mobile.ui.screens.catalog

import androidx.lifecycle.ViewModel
import com.tmm.mobile.ui.mock.MockupData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel synchrone / testable de l'écran Catalogue (E4.6, Catalog.dc.html).
 */
class CatalogViewModel(initialState: CatalogState = MockupData.catalog) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<CatalogState> = _state

    fun onEvent(event: CatalogEvent) {
        when (event) {
            CatalogEvent.Reimport -> _state.update {
                it.copy(
                    importedWhen = "à l'instant",
                    totalSeries = 143,
                    absentSeries = 37,
                    importStep = CatalogImportStep.Idle,
                )
            }
            is CatalogEvent.SetImportStep -> _state.update {
                it.copy(importStep = event.step)
            }
        }
    }
}
