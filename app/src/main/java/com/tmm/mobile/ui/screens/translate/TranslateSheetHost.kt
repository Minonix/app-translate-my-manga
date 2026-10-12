package com.tmm.mobile.ui.screens.translate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tmm.mobile.R
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.ui.components.V2Toast
import com.tmm.mobile.ui.icons.TmmIconId

/**
 * Ouvre la feuille « Traduire » depuis n'importe quel écran (Accueil, Activité, Bibliothèque).
 * Hors de [TranslateSheetHost] (aperçus, tests d'un écran seul), l'appel ne fait rien.
 */
val LocalOpenTranslate = staticCompositionLocalOf<(TranslateRequest) -> Unit> { {} }

/** Héberge la feuille au-dessus de tout l'écran, barre basse comprise, comme le voile de la maquette. */
@Composable
fun TranslateSheetHost(container: AppContainer, content: @Composable () -> Unit) {
    val vm: TranslateViewModel = viewModel(factory = viewModelFactory { initializer { TranslateViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<TranslatePhase.Done?>(null) }

    LaunchedEffect(state.phase) {
        val phase = state.phase
        if (phase is TranslatePhase.Done && visible) {
            visible = false
            done = phase
        }
    }

    val open: (TranslateRequest) -> Unit = remember(vm) {
        { request ->
            vm.onEvent(TranslateEvent.Open(request))
            visible = true
        }
    }
    CompositionLocalProvider(LocalOpenTranslate provides open) {
        Box(Modifier.fillMaxSize()) {
            content()
            TranslateSheet(visible = visible, state = state, onEvent = vm::onEvent, onDismiss = { visible = false })
            val last = done
            V2Toast(
                visible = last != null,
                message = last?.let {
                    pluralStringResource(if (it.translate) R.plurals.series_toast_translated else R.plurals.series_toast_copied, it.count, it.count)
                }.orEmpty(),
                onDismiss = { done = null },
                icon = TmmIconId.V2Check,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}
