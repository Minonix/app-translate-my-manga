package com.tmm.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import kotlinx.coroutines.delay

private const val AutoDismissMs = 3600L

/** Toast bas d'ecran (Library.dc.html) : fond ink, auto-dismiss ~3.6s, action inline optionnelle. */
@Composable
fun TmmToast(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(AutoDismissMs)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TmmColors.ink)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmText(text = message, style = TmmType.sans(13), color = TmmColors.paper, modifier = Modifier.weight(1f))
            if (actionLabel != null) {
                TmmText(
                    text = actionLabel,
                    style = TmmType.sans(13, weight = 600),
                    color = TmmColors.accent,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable(onClick = onAction),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmToastPreview() {
    TmmTheme {
        TmmToast(
            visible = true,
            message = "Chapitre 12 envoye sur le PC.",
            onDismiss = {},
            actionLabel = "Annuler",
        )
    }
}
