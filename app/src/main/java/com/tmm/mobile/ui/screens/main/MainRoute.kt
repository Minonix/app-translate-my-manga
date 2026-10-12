package com.tmm.mobile.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.HomeRoute
import com.tmm.mobile.ui.nav.LibraryRoute
import com.tmm.mobile.ui.nav.SettingsRoute
import com.tmm.mobile.ui.nav.QueueRoute
import com.tmm.mobile.ui.nav.SeriesRoute
import com.tmm.mobile.ui.screens.empty.EmptyScreen
import com.tmm.mobile.ui.screens.empty.EmptyViewModel
import com.tmm.mobile.ui.screens.translate.LocalOpenTranslate
import com.tmm.mobile.ui.screens.translate.TranslateRequest
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
            // La relecture de la sauvegarde Mihon est dans Réglages (« Relire ») depuis la v2.
            onReadBackup = { nav.navigate(SettingsRoute) {
                popUpTo(HomeRoute) { saveState = true }; launchSingleTop = true; restoreState = true
            } },
        )
        return
    }

    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()

    // Maquette v2 : « Traduire » ouvre la feuille precochee au lieu d'envoyer sans confirmation (point A).
    val openTranslate = LocalOpenTranslate.current

    MainScreen(
        state = state,
        onEvent = vm::onEvent,
        onOpenQueue = { nav.navigate(QueueRoute) {
            popUpTo(HomeRoute) { saveState = true }; launchSingleTop = true; restoreState = true
        } },
        onOpenTranslate = { openTranslate(TranslateRequest(preselectAll = true)) },
        onManageFollowed = { nav.navigate(LibraryRoute()) {
            popUpTo(HomeRoute) { saveState = true }; launchSingleTop = true; restoreState = true
        } },
        onOpenSeries = { seriesKey -> nav.navigate(SeriesRoute(seriesKey)) },
    )
}
