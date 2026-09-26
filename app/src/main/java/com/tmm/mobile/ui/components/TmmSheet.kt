package com.tmm.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/**
 * Feuille modale du bas (pattern confirmation de Library.dc.html) : voile plein-ecran
 * cliquable pour fermer, contenu qui glisse depuis le bas, coins hauts arrondis 24dp.
 */
@Composable
fun TmmSheet(
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
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(280)) { it },
                exit = slideOutVertically(tween(220)) { it },
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(TmmColors.paperRaised)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .semantics {
                            paneTitle = title ?: "Feuille"
                        }
                        .padding(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(TmmColors.track),
                    )
                    if (title != null) {
                        TmmText(
                            text = title,
                            style = TmmType.display(24, weight = 800),
                            color = TmmColors.ink,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmSheetPreview() {
    TmmTheme {
        TmmSheet(visible = true, onDismiss = {}, title = "Retraduire ce chapitre ?") {
            TmmText(
                text = "Le fichier existant sera remplace.",
                style = TmmType.sans(14),
                color = TmmColors.textMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            TmmButton(label = "Confirmer", onClick = {})
        }
    }
}
