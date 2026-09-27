package com.tmm.mobile.ui.screens.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.HomeRoute
import com.tmm.mobile.ui.nav.SettingsRoute
import kotlinx.coroutines.launch

@Composable
fun PermissionRoute(nav: NavController) {
    val context = LocalContext.current
    val container = (context.applicationContext as TmmApp).container
    val vm: PermissionViewModel = viewModel(
        factory = viewModelFactory { initializer { PermissionViewModel(container) } },
    )
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> vm.onFolderPicked(uri) }

    PermissionScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                PermissionEvent.PickFolder -> folderLauncher.launch(null)
            }
        },
        onBackToHome = {
            scope.launch {
                vm.runStartupAndFinish()
                nav.navigate(HomeRoute) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        },
        onOpenSettings = {
            nav.navigate(SettingsRoute)
        },
    )
}
