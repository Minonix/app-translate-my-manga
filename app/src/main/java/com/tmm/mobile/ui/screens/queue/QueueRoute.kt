package com.tmm.mobile.ui.screens.queue

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.TranslationSettingsRoute

/**
 * Point d'entree navigation de l'ecran File (E10, PLAN_GEMINI.md 7.5). Construit le vrai
 * QueueViewModel via l'AppContainer de TmmApp (cf CatalogRoute.kt pour le meme patron).
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
    QueueScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTranslationSettings = { nav.navigate(TranslationSettingsRoute) },
        // ponytail: pas de NewTranslationRoute dans Routes.kt et DoneJob ne porte pas de seriesKey
        // (seulement mihonPath) - onOpenNewTranslation/onOpenSeries restent no-op ; upgrade quand
        // ces routes/champs existeront (cf rapport final).
    )
}
