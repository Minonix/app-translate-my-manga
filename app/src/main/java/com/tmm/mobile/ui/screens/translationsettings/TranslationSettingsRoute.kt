package com.tmm.mobile.ui.screens.translationsettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp

/** Point d'entree navigation de l'ecran Reglages de traduction (E10, plan 7.9). */
@Composable
fun TranslationSettingsRoute(nav: NavController) {
    val container = (LocalContext.current.applicationContext as TmmApp).container
    val viewModel = viewModel<TranslationSettingsViewModel>(
        factory = viewModelFactory {
            initializer { TranslationSettingsViewModel(container) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    TranslationSettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateBack = { nav.popBackStack() },
    )
}
