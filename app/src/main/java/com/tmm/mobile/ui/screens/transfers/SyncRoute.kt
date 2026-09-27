package com.tmm.mobile.ui.screens.transfers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.LibraryRoute
import com.tmm.mobile.ui.nav.QueueRoute
import com.tmm.mobile.ui.nav.SeriesRoute

/**
 * Point d'entree navigation de l'ecran Transferts (E10, PLAN_GEMINI.md 7.4). Construit le vrai
 * SyncViewModel via l'AppContainer de TmmApp (cf CatalogRoute.kt pour le meme patron).
 */
@Composable
fun SyncRoute(nav: NavController) {
    val container = (LocalContext.current.applicationContext as TmmApp).container
    val viewModel = viewModel<SyncViewModel>(
        factory = viewModelFactory {
            initializer { SyncViewModel(container) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    SyncScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenQueue = { nav.navigate(QueueRoute) },
        onOpenLibrary = { nav.navigate(LibraryRoute) },
        onOpenMissingSeries = {
            state.missingSeriesKey?.let { nav.navigate(SeriesRoute(it)) }
        },
    )
}
