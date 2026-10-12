package com.tmm.mobile.ui.nav

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tmm.mobile.Startup
import com.tmm.mobile.StartupResult
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.status.GlobalCounts
import com.tmm.mobile.transport.GoogleAuth
import com.tmm.mobile.ui.components.OfflineBanner
import com.tmm.mobile.ui.components.OfflineBannerState
import com.tmm.mobile.ui.components.TmmBottomBar
import com.tmm.mobile.ui.components.TmmBottomBarItem
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.screens.library.LibraryRoute as LibraryScreenRoute
import com.tmm.mobile.ui.screens.main.MainRoute as HomeScreenRoute
import com.tmm.mobile.ui.screens.onboarding.OnboardingRoute as OnboardingScreenRoute
import com.tmm.mobile.ui.screens.permission.PermissionRoute as PermissionScreenRoute
import com.tmm.mobile.ui.screens.queue.QueueRoute as QueueScreenRoute
import com.tmm.mobile.ui.screens.series.SeriesRoute as SeriesScreenRoute
import com.tmm.mobile.ui.screens.settings.SettingsRoute as SettingsScreenRoute
import com.tmm.mobile.ui.screens.translate.TranslateSheetHost
import com.tmm.mobile.ui.screens.translationsettings.TranslationSettingsRoute as TranslationSettingsScreenRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Maquette v2 : 4 onglets. Activite = File + Transferts ; Reglages devient un onglet.
private val tabRouteOf = listOf(HomeRoute::class, LibraryRoute::class, QueueRoute::class, SettingsRoute::class)
private val tabItems = listOf(
    TmmIconId.V2Home to "Accueil",
    TmmIconId.V2Grid to "Bibliothèque",
    TmmIconId.V2Pulse to "Activité",
    TmmIconId.V2Gear to "Réglages",
)

// PLAN_GEMINI.md 5.19 : NavHost + barre + bandeaux ; Startup relance a chaque ON_START.
@Composable
fun TmmRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as TmmApp).container
    val navController = rememberNavController()
    var startupResult by remember { mutableStateOf<StartupResult?>(null) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hasNavigatedOnce = remember { mutableStateOf(false) }
    DisposableEffectStartup(container.startup) { result ->
        startupResult = result
        if (!hasNavigatedOnce.value) {
            hasNavigatedOnce.value = true
        } else {
            when (result) {
                StartupResult.ONBOARDING -> navController.navigateSingleTop(OnboardingRoute)
                StartupResult.PERMISSION_NEEDED -> navController.navigateSingleTop(PermissionRoute)
                StartupResult.DONE -> navController.navigateSingleTop(HomeRoute)
            }
        }
    }

    // Actualiser relance Startup hors ON_START : une permission Mihon perdue doit aussi y mener a l'ecran
    // Permission (navigateSingleTop evite le doublon avec le rappel ON_START ci-dessus).
    androidx.compose.runtime.LaunchedEffect(container.startup) {
        container.startup.results.collect { result ->
            if (result == StartupResult.PERMISSION_NEEDED && hasNavigatedOnce.value) {
                navController.navigateSingleTop(PermissionRoute)
            }
        }
    }

    val networkOnline by rememberNetworkOnline(context)
    val authExpired by container.prefs.authExpired.collectAsState(initial = false)
    val protocolTooNew by container.prefs.protocolTooNew.collectAsState(initial = false)
    var reconnecting by remember { mutableStateOf(false) }

    // Un refresh seul ne pouvait pas aboutir : sans consentement (nouvelle portee Drive, jeton revoque),
    // authorize() renvoie un ecran Google a afficher, ce qu'un worker ou un refresh ne sait pas faire.
    val googleAuth = remember { GoogleAuth(context) }
    fun refreshAfterConsent(granted: Boolean) {
        scope.launch {
            // Consentement accorde : le bandeau tombe meme si refresh() est en recharge (cooldown) ou
            // echoue pour une autre raison.
            if (granted) container.prefs.setAuthExpired(false)
            container.refreshCoordinator.refresh()
            reconnecting = false
        }
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        refreshAfterConsent(granted = it.resultCode == Activity.RESULT_OK)
    }

    val bannerState = when {
        authExpired -> OfflineBannerState.TokenExpired(reconnecting = reconnecting) {
            reconnecting = true
            scope.launch {
                val result = try {
                    googleAuth.authorize()
                } catch (e: Exception) {
                    AppLog.d("TmmRoot", "authorize() a echoue : ${e.message}")
                    null
                }
                val pending = result?.let { googleAuth.pendingIntent(it) }
                if (pending != null) {
                    consentLauncher.launch(IntentSenderRequest.Builder(pending).build())
                } else {
                    refreshAfterConsent(granted = result?.accessToken != null)
                }
            }
        }
        protocolTooNew -> OfflineBannerState.ProtocolTooNew
        !networkOnline -> OfflineBannerState.NoNetwork
        else -> null
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    // hasRoute compare via le serializer : KClass.qualifiedName est renomme par R8 en release
    // alors que la route garde le serialName d'origine (barre basse invisible en v1.0.2).
    val currentDestination = backStackEntry?.destination
    val selectedTab = when {
        currentDestination?.hasRoute<SeriesRoute>() == true -> 1
        currentDestination?.hasRoute<TranslationSettingsRoute>() == true -> 3
        else -> tabRouteOf.indexOfFirst { currentDestination?.hasRoute(it) == true }
    }
    val activityBadge by remember { GlobalCounts.activityBadgeCount(container.db) }.collectAsState(initial = 0)

    if (startupResult == null) return

    TranslateSheetHost(container) {
    Column(modifier = Modifier.fillMaxSize()) {
        bannerState?.let { OfflineBanner(it) }
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = startDestinationFor(startupResult!!)) {
                composable<OnboardingRoute> { OnboardingScreenRoute(navController) }
                composable<PermissionRoute> { PermissionScreenRoute(navController) }
                composable<HomeRoute> { HomeScreenRoute(navController) }
                composable<LibraryRoute> { entry ->
                    val route: LibraryRoute = entry.toRoute()
                    LibraryScreenRoute(navController, categoryOrder = null, picking = route.picking)
                }
                composable<QueueRoute> { QueueScreenRoute(navController) }
                composable<SeriesRoute> { entry ->
                    val route: SeriesRoute = entry.toRoute()
                    SeriesScreenRoute(navController, seriesKey = route.seriesKey)
                }
                composable<SettingsRoute> { SettingsScreenRoute(navController) }
                composable<TranslationSettingsRoute> { TranslationSettingsScreenRoute(navController) }
            }
        }
        if (selectedTab >= 0) {
            TmmBottomBar(
                items = tabItems.mapIndexed { index, (icon, label) ->
                    TmmBottomBarItem(icon, label, badgeCount = if (index == 2) activityBadge.takeIf { it > 0 } else null)
                },
                selectedIndex = selectedTab,
                onSelect = { index ->
                    val target = when (index) {
                        0 -> HomeRoute
                        1 -> LibraryRoute()
                        2 -> QueueRoute
                        else -> SettingsRoute
                    }
                    navController.navigate(target) {
                        popUpTo(HomeRoute) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
    }
}

private fun startDestinationFor(result: StartupResult) = when (result) {
    StartupResult.ONBOARDING -> OnboardingRoute
    StartupResult.PERMISSION_NEEDED -> PermissionRoute
    StartupResult.DONE -> HomeRoute
}

private fun NavHostController.navigateSingleTop(route: Any) {
    if (currentDestination?.hasRoute(route::class) == true) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun DisposableEffectStartup(startup: Startup, onResult: (StartupResult) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                scope.launch { onResult(startup.run()) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun rememberNetworkOnline(context: Context): androidx.compose.runtime.State<Boolean> {
    val flow = remember { networkOnlineFlow(context) }
    return flow.collectAsState(initial = true)
}

// ponytail: NetworkCapabilities.NET_CAPABILITY_VALIDATED seul, pas de distinction Wi-Fi/donnees ici
// (wifi_only est gere par les workers, pas par ce bandeau).
private fun networkOnlineFlow(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    // Un etat par reseau : avec plusieurs reseaux (Wi-Fi + donnees + VPN Tailscale), la perte ou la
    // revalidation de l'un d'eux faisait clignoter le bandeau "hors ligne" alors qu'un autre restait valide.
    val validated = mutableMapOf<Network, Boolean>()
    fun emit() = trySend(validated.values.any { it })
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(validated) { validated.getOrPut(network) { true }; emit() }
        }
        override fun onLost(network: Network) {
            synchronized(validated) { validated.remove(network); emit() }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            synchronized(validated) {
                validated[network] = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                emit()
            }
        }
    }
    val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
    cm.registerNetworkCallback(request, callback)
    trySend(cm.activeNetwork != null)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
