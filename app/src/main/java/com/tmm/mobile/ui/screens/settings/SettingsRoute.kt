package com.tmm.mobile.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.journalExportFileName
import com.tmm.mobile.core.journalExportText
import com.tmm.mobile.ui.nav.CatalogRoute
import com.tmm.mobile.ui.nav.OnboardingRoute
import com.tmm.mobile.ui.nav.TranslationSettingsRoute
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Point d'entree navigation de l'ecran Reglages (E10, plan 7.8). Le selecteur de dossier Mihon
 * (OpenDocumentTree) et l'export des journaux (CreateDocument, E10.4) sont des I/O d'Activity :
 * ils vivent ici, pas dans SettingsViewModel (pur, testable sans Robolectric d'Activity).
 */
@Composable
fun SettingsRoute(nav: NavController) {
    val context = LocalContext.current
    val container = (context.applicationContext as TmmApp).container
    val viewModel = viewModel<SettingsViewModel>(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            scope.launch {
                container.prefs.setMihonTreeUri(uri.toString())
                container.startup.run()
            }
        }
    }

    val journalExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            val text = journalExportText(AppLog.entries(), container.clock.nowMillis())
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            viewModel.onLogsExported()
        }
    }

    SettingsScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                SettingsEvent.ChangeMihonFolder -> folderPicker.launch(null)
                SettingsEvent.ExportLogs -> {
                    val today = Instant.ofEpochMilli(container.clock.nowMillis())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    journalExportLauncher.launch(journalExportFileName(today))
                }
                else -> viewModel.onEvent(event)
            }
        },
        onNavigateBack = { nav.popBackStack() },
        onNavigateCatalog = { nav.navigate(CatalogRoute) },
        onNavigateOnboarding = { nav.navigate(OnboardingRoute) },
        onNavigateTranslationSettings = { nav.navigate(TranslationSettingsRoute) },
    )
}
