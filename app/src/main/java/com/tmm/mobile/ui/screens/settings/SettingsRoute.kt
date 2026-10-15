package com.tmm.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
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
import com.tmm.mobile.ui.nav.OnboardingRoute
import com.tmm.mobile.ui.nav.TranslationSettingsRoute
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Confie l'APK a l'installateur d'Android. Sans l'autorisation « installer des applis inconnues », ouvre
 * d'abord son ecran : au retour, « Installer » relance l'installation sans retelecharger.
 */
private fun installApk(context: Context, apk: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

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
                SettingsEvent.InstallUpdate -> viewModel.downloadUpdate { apk -> installApk(context, apk) }
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
        onNavigateOnboarding = { nav.navigate(OnboardingRoute) },
        onNavigateTranslationSettings = { nav.navigate(TranslationSettingsRoute) },
    )
}
