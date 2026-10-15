package com.tmm.mobile.ui.screens.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.QueueRoute

/**
 * Point d'entree navigation de la Fiche serie (E10, plan 7.3). Construit le vrai SeriesViewModel
 * via l'AppContainer de TmmApp ; `seriesKey` vient de SeriesRoute (ui.nav.Routes).
 */
@Composable
fun SeriesRoute(nav: NavController, seriesKey: String) {
    val container = (LocalContext.current.applicationContext as TmmApp).container
    val vm: SeriesViewModel = viewModel(
        factory = viewModelFactory { initializer { SeriesViewModel(container, seriesKey) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    SeriesScreen(
        state = state,
        onEvent = vm::onEvent,
        onBack = { nav.popBackStack() },
        onOpenQueue = { nav.navigate(QueueRoute) },
        // Tirer pour recharger : rescan complet de Mihon (sans le cache par date de dossier).
        onRefresh = { container.startup.run(forceRescan = true) },
    )
}
