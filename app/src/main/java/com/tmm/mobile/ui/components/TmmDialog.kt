package com.tmm.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

private val ScrimColor = TmmColors.Ink45

/** Variante centree de TmmSheet (pop/fade au lieu d'un slide) pour les alertes courtes. */
@Composable
fun TmmDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(tween(220), initialScale = .92f) + fadeIn(tween(220)),
                exit = scaleOut(tween(160), targetScale = .92f) + fadeOut(tween(160)),
            ) {
                Column(
                    modifier = modifier
                        .widthIn(max = 340.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TmmColors.paperRaised)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .semantics { paneTitle = title ?: "Boite de dialogue" }
                        .padding(20.dp),
                ) {
                    if (title != null) {
                        TmmText(text = title, style = TmmType.display(20, weight = 800), color = TmmColors.ink)
                    }
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmDialogPreview() {
    TmmTheme {
        TmmDialog(visible = true, onDismiss = {}, title = "Supprimer la serie ?") {
            TmmText(
                text = "Cette action est irreversible.",
                style = TmmType.sans(14),
                color = TmmColors.textMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            TmmButton(label = "Supprimer", onClick = {})
        }
    }
}
