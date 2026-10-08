package com.tmm.mobile.ui.screens.library

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
import com.tmm.mobile.ui.nav.SeriesRoute

/**
 * Point d'entree navigation de l'ecran Bibliotheque (E10, plan 7.2). Construit le vrai
 * LibraryViewModel via l'AppContainer de TmmApp ; `categoryOrder` vient de LibraryFilteredRoute
 * (depuis le Catalogue) ou est null (onglet Bibliotheque de la barre basse).
 */
@Composable
fun LibraryRoute(nav: NavController, categoryOrder: Long?) {
    val container = (LocalContext.current.applicationContext as TmmApp).container
    val vm: LibraryViewModel = viewModel(
        factory = viewModelFactory { initializer { LibraryViewModel(container, categoryOrder) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onEvent = vm::onEvent,
        onOpenSeries = { seriesKey -> nav.navigate(SeriesRoute(seriesKey)) },
        onOpenQueue = { nav.navigate(QueueRoute) },
    )
}
