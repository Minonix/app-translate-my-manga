package com.tmm.mobile.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.tmm.mobile.TmmApp
import com.tmm.mobile.ui.nav.HomeRoute

// PLAN_GEMINI.md 7.10/E10.3 : cablage Activity Result (consentement Google, choix du dossier
// Mihon) autour d'OnboardingViewModel. La logique metier reste dans le ViewModel (testable sans
// Activity) ; cette Route ne fait que la plomberie SAF/Play Services.
@Composable
fun OnboardingRoute(nav: NavController) {
    val context = LocalContext.current
    val container = (context.applicationContext as TmmApp).container
    val vm: OnboardingViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingViewModel(container) } },
    )
    val state by vm.state.collectAsState()
    val pendingAuthIntent by vm.pendingAuthIntent.collectAsState()

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> vm.onGoogleAuthResult(result.data) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> vm.onFolderPicked(uri) }

    pendingAuthIntent?.let { request ->
        LaunchedEffect(request) { authLauncher.launch(request) }
    }

    OnboardingScreen(
        state = state,
        onEvent = { event ->
            when {
                event is OnboardingEvent.PrimaryAction && state.step == 1 && !state.isGoogleConnected ->
                    vm.startGoogleAuth()
                event is OnboardingEvent.PrimaryAction && state.step == 2 && !state.isMihonSelected ->
                    folderLauncher.launch(null)
                else -> vm.onEvent(event)
            }
        },
        onFinishOnboarding = {
            vm.finishOnboarding()
            nav.navigate(HomeRoute) {
                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        },
    )
}
