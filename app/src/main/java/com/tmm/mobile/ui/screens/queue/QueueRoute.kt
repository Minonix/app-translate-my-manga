package com.tmm.mobile.ui.screens.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.TranslationSettingsRoute
import com.tmm.mobile.ui.screens.translate.LocalOpenTranslate
import com.tmm.mobile.ui.screens.translate.TranslateRequest

/**
 * Point d'entrée navigation de l'écran Activité (maquette v2, écran 7). Construit le vrai
 * QueueViewModel via l'AppContainer de TmmApp.
 */
@Composable
fun QueueRoute(nav: NavController) {
    val container = (LocalContext.current.applicationContext as TmmApp).container
    val viewModel = viewModel<QueueViewModel>(
        factory = viewModelFactory {
            initializer { QueueViewModel(container) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Maquette v2 : le + ouvre la feuille Traduire, rien de coche.
    val openTranslate = LocalOpenTranslate.current
    QueueScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTranslationSettings = { nav.navigate(TranslationSettingsRoute) },
        onOpenNewTranslation = { openTranslate(TranslateRequest(preselectAll = false)) },
    )
}

