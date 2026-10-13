package com.tmm.mobile.ui.screens.empty

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone
import com.tmm.mobile.ui.theme.pressScale

/**
 * Ecran Accueil vide (Empty.dc.html, E4.11).
 * Presente quand aucun chapitre n'est telecharge sur le telephone.
 * La barre de navigation basse commune est posee par TmmRoot en E10.
 */
@Composable
fun EmptyScreen(
    state: EmptyState,
    onEvent: (EmptyEvent) -> Unit,
    onReadBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            EmptyHeader(state = state)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Section 1: Aucun chapitre téléchargé
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dashedBorder(color = TmmColors.CD5CDBC, width = 1.5.dp, cornerRadius = 18.dp)
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 128.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TmmColors.CECE7DC)
                            .halftone(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmIcon(
                            icon = TmmIconId.Pages,
                            tint = TmmColors.textMuted,
                            size = 36.dp,
                        )
                    }
                    TmmText(
                        text = stringResource(R.string.empty_no_chapters_title),
                        style = TmmType.display(22, weight = 800),
                        color = TmmColors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    TmmText(
                        text = stringResource(R.string.empty_no_chapters_desc),
                        style = TmmType.sans(14, weight = 400, lineHeight = 21f),
                        color = TmmColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                    val scanLabel = when {
                        state.isBusy -> stringResource(R.string.empty_btn_scanning)
                        state.isDone -> stringResource(R.string.empty_btn_still_nothing)
                        else -> stringResource(R.string.empty_btn_scan)
                    }
                    val rotation = if (state.isBusy) {
                        val transition = rememberInfiniteTransition(label = "spin")
                        transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "spinAngle",
                        ).value
                    } else 0f

                    val scanInteractionSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TmmColors.ink)
                            .pressScale(scanInteractionSource)
                            .clickable(
                                interactionSource = scanInteractionSource,
                                indication = null,
                                enabled = !state.isBusy,
                                onClick = { onEvent(EmptyEvent.ScanMihon) },
                            )
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.rotate(rotation)) {
                            TmmIcon(
                                icon = TmmIconId.Refresh,
                                tint = TmmColors.paper,
                                size = 18.dp,
                            )
                        }
                        TmmText(
                            text = scanLabel,
                            style = TmmType.sans(15, weight = 600),
                            color = TmmColors.paper,
                        )
                    }
                }

                // Section 2: Pas encore de sauvegarde Mihon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TmmColors.surface)
                        .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TmmColors.infoBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmIcon(
                            icon = TmmIconId.File,
                            tint = TmmColors.info,
                            size = 20.dp,
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        TmmText(
                            text = stringResource(R.string.empty_no_backup_title),
                            style = TmmType.sans(15, weight = 600),
                            color = TmmColors.ink,
                        )
                        TmmText(
                            text = stringResource(R.string.empty_no_backup_desc),
                            style = TmmType.sans(13, weight = 400, lineHeight = 19f),
                            color = TmmColors.textMuted,
                        )
                        TmmText(
                            text = stringResource(R.string.empty_read_backup_link),
                            style = TmmType.sans(13, weight = 600),
                            color = TmmColors.accent,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onReadBackup,
                                ),
                        )
                    }
                }

                // Section 3: File vide
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TmmColors.surface)
                        .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TmmColors.line),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmIcon(
                            icon = TmmIconId.List,
                            tint = TmmColors.textMuted,
                            size = 20.dp,
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        TmmText(
                            text = stringResource(R.string.empty_queue_empty_title),
                            style = TmmType.sans(15, weight = 600),
                            color = TmmColors.ink,
                        )
                        TmmText(
                            text = stringResource(R.string.empty_queue_empty_desc),
                            style = TmmType.sans(13, weight = 400),
                            color = TmmColors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHeader(state: EmptyState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(TmmColors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = stringResource(R.string.app_name_glyph),
                        style = TmmType.display(20, weight = 800),
                        color = TmmColors.surface,
                    )
                }
                TmmText(
                    text = stringResource(R.string.empty_app_name),
                    style = TmmType.display(17, weight = 800),
                    color = TmmColors.ink,
                )
            }
            if (state.pcSeenText.isNotBlank()) Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(TmmColors.surface)
                    .border(1.dp, TmmColors.track, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TmmColors.C9C9486)
                )
                TmmText(
                    text = state.pcSeenText,
                    style = TmmType.sans(13, weight = 600),
                    color = TmmColors.ink,
                )
            }
        }
        TmmText(
            text = stringResource(R.string.empty_title),
            style = TmmType.display(36, weight = 800, lineHeight = 36f),
            color = TmmColors.ink,
            modifier = Modifier.semantics { heading() },
        )
    }
}
