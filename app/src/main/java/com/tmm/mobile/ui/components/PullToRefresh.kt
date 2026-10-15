package com.tmm.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType
import kotlinx.coroutines.launch

/**
 * Tirer vers le bas en haut de liste pour recharger (Bibliotheque, Fiche serie).
 * ponytail: pas de material3 (PullToRefreshBox) dans le projet ; geste maison sur le nested scroll
 * de la liste, indicateur texte. A remplacer si material3 entre un jour dans les dependances.
 */
@Composable
fun PullToRefresh(onRefresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }
    val connection = remember(thresholdPx) {
        object : NestedScrollConnection {
            // La liste ne consomme plus rien vers le bas : on est en haut, on compte le tirage.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) pulled += available.y
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) pulled = 0f
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled >= thresholdPx && !refreshing) {
                    refreshing = true
                    scope.launch {
                        try { onRefresh() } finally { refreshing = false }
                    }
                }
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    Box(modifier.nestedScroll(connection)) {
        content()
        if (refreshing || pulled > 0f) {
            TmmText(
                text = stringResource(
                    when {
                        refreshing -> R.string.pull_refreshing
                        pulled >= thresholdPx -> R.string.pull_release
                        else -> R.string.pull_hint
                    },
                ),
                style = TmmType.sans(13),
                color = TmmColors.V2.muted,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            )
        }
    }
}
