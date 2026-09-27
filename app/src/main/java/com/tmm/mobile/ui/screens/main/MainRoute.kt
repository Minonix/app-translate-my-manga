package com.tmm.mobile.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.CatalogRoute
import com.tmm.mobile.ui.nav.QueueRoute
import com.tmm.mobile.ui.nav.SeriesRoute
import com.tmm.mobile.ui.nav.SettingsRoute
import com.tmm.mobile.ui.nav.TransfersRoute
import com.tmm.mobile.ui.screens.empty.EmptyScreen
import com.tmm.mobile.ui.screens.empty.EmptyViewModel
import com.tmm.mobile.ui.screens.newtranslation.NewTranslationScreen
import com.tmm.mobile.ui.screens.newtranslation.NewTranslationViewModel
import kotlinx.coroutines.flow.combine

/**
 * PLAN_GEMINI.md E10.1 / 7.1 : branchement reel de l'Accueil. Affiche EmptyScreen tant qu'aucun
 * chapitre n'est telecharge et qu'aucune sauvegarde Mihon n'a ete importee, sinon MainScreen.
 */
@Composable
fun MainRoute(nav: NavController) {
    val container = (LocalContext.current.applicationContext as TmmApp).container

    val isEmpty by remember {
        combine(
            container.db.downloadedChapterDao().observeAll(),
            container.db.backupImportDao().observe(),
        ) { downloaded, backupImport -> downloaded.isEmpty() && backupImport == null }
    }.collectAsState(initial = false)

    if (isEmpty) {
        val vm: EmptyViewModel = viewModel(factory = viewModelFactory { initializer { EmptyViewModel() } })
        val state by vm.state.collectAsStateWithLifecycle()
        EmptyScreen(
            state = state,
            onEvent = vm::onEvent,
            onOpenCatalog = { nav.navigate(CatalogRoute) },
        )
        return
    }

    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()

    // PLAN_GEMINI.md 5.19 : sheet F (Nouvelle traduction) est un recouvrement, pas une route.
    var showNewTranslation by remember { mutableStateOf(false) }

    MainScreen(
        state = state,
        onEvent = vm::onEvent,
        onOpenSettings = { nav.navigate(SettingsRoute) },
        onOpenSync = { nav.navigate(TransfersRoute) },
        onOpenQueue = { nav.navigate(QueueRoute) },
        onOpenNewTranslation = { showNewTranslation = true },
        onOpenSeries = { seriesKey -> nav.navigate(SeriesRoute(seriesKey)) },
    )

    if (showNewTranslation) {
        // ponytail: NewTranslationViewModel reste provisoire (mock, pas de vraie creation de job) -
        // brancher container.jobRepository quand cet ecran sera traite (hors perimetre Accueil).
        val newTranslationVm: NewTranslationViewModel = viewModel(
            factory = viewModelFactory { initializer { NewTranslationViewModel(container) } },
        )
        val newTranslationState by newTranslationVm.state.collectAsStateWithLifecycle()
        NewTranslationScreen(
            state = newTranslationState,
            onEvent = newTranslationVm::onEvent,
            onClose = { showNewTranslation = false },
            onOpenQueue = {
                showNewTranslation = false
                nav.navigate(QueueRoute)
            },
        )
    }
}
