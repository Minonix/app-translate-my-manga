package com.tmm.mobile.ui.screens.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.LibraryFilteredRoute
import com.tmm.mobile.ui.nav.LibraryRoute

/**
 * Point d'entree navigation de l'ecran Catalogue (E10, plan 7.6). Construit le vrai
 * CatalogViewModel via l'AppContainer de TmmApp (pas de framework de DI, cf AppContainer.kt).
 */
@Composable
fun CatalogRoute(nav: NavController) {
    val container = (LocalContext.current.applicationContext as TmmApp).container
    val viewModel = viewModel<CatalogViewModel>(
        factory = viewModelFactory {
            initializer { CatalogViewModel(container) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogScreen(
        state = state,
        onEvent = viewModel::onEvent,
        // plan 7.6 : la carte "non téléchargées" pointe vers la Bibliothèque non filtrée
        // (href="Library.dc.html" brut dans la maquette, sans filtre catégorie).
        onOpenLibrary = { nav.navigate(LibraryRoute) },
        // plan 7.6 : "appui -> LibraryFilteredRoute" sur une ligne de catégorie.
        onOpenCategory = { name ->
            val order = state.categories.firstOrNull { it.name == name }?.order ?: UNCATEGORIZED_ORDER
            nav.navigate(LibraryFilteredRoute(order))
        },
    )
}
