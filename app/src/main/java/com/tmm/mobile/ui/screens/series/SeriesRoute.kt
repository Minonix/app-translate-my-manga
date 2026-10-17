package com.tmm.mobile.ui.screens.series

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.ui.nav.QueueRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Point d'entree navigation de la Fiche serie (E10, plan 7.3). Construit le vrai SeriesViewModel
 * via l'AppContainer de TmmApp ; `seriesKey` vient de SeriesRoute (ui.nav.Routes).
 */
@Composable
fun SeriesRoute(nav: NavController, seriesKey: String) {
    val container = (LocalContext.current.applicationContext as TmmApp).container
    val vm: SeriesViewModel = viewModel(
        factory = viewModelFactory { initializer { SeriesViewModel(container, seriesKey) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Ligne dont on relie le fichier : survit a la recreation de l'activite pendant le selecteur.
    var linkKey by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(OpenChapterFile) { uri ->
        val key = linkKey ?: return@rememberLauncherForActivityResult
        linkKey = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tree = container.prefs.mihonTreeUri.first()?.let(Uri::parse)
            // Un fichier d'un autre fournisseur (Recents, Telechargements) n'a pas de docId de l'arbre Mihon.
            val docId = if (tree != null && uri.authority == tree.authority) DocumentsContract.getDocumentId(uri) else null
            if (docId == null) AppLog.d("Lien", "fichier hors arbre Mihon : fournisseur ${uri.authority}")
            vm.onEvent(SeriesEvent.FilePicked(key, docId))
        }
    }
    SeriesScreen(
        state = state,
        onEvent = vm::onEvent,
        onBack = { nav.popBackStack() },
        onOpenQueue = { nav.navigate(QueueRoute) },
        // Tirer pour recharger : relit les dossiers Mihon de cette serie, la synchro PC suit en arriere-plan.
        onRefresh = { container.startup.rescanSeries(seriesKey) },
        onLinkFile = { key ->
            linkKey = key
            scope.launch {
                val tree = container.prefs.mihonTreeUri.first()?.let(Uri::parse)
                val start = vm.linkStartDocId()
                picker.launch(if (tree != null && start != null) DocumentsContract.buildDocumentUriUsingTree(tree, start) else null)
            }
        },
    )
}

/** Selecteur de fichier ouvert dans le dossier `input` (EXTRA_INITIAL_URI, ignore par certains gestionnaires). */
private object OpenChapterFile : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .apply { if (input != null) putExtra(DocumentsContract.EXTRA_INITIAL_URI, input) }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}
