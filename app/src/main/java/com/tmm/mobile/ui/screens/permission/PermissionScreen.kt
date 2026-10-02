package com.tmm.mobile.ui.screens.permission

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.pressScale

/**
 * Écran Accès Mihon perdu (Permission.dc.html, E4.13).
 * Bloque l'accès lorsque les autorisations Android vers le dossier Mihon sont révoquées.
 */
@Composable
fun PermissionScreen(
    state: PermissionState,
    onEvent: (PermissionEvent) -> Unit,
    onBackToHome: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Contenu principal centré
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TmmColors.dangerBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmIcon(
                            icon = TmmIconId.FolderDenied,
                            tint = TmmColors.accent,
                            size = 40.dp,
                            strokeWidth = 1.6f,
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TmmText(
                            text = stringResource(R.string.perm_title),
                            style = TmmType.display(30, weight = 800, lineHeight = 31.5f),
                            color = TmmColors.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        TmmText(
                            text = stringResource(R.string.perm_desc),
                            style = TmmType.sans(15, weight = 400, lineHeight = 22.5f),
                            color = TmmColors.textMuted,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(TmmColors.surface)
                            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TmmText(
                            text = stringResource(R.string.perm_expected_label).uppercase(),
                            style = TmmType.sans(12, weight = 600, letterSpacingEm = 0.06f),
                            color = TmmColors.textMuted,
                        )
                        TmmText(
                            text = state.expectedPath,
                            style = TmmType.mono(14, weight = 500),
                            color = TmmColors.ink,
                        )
                        // Noms de dossiers (« downloads/ »...) en mono, comme dans la maquette.
                        val desc = stringResource(R.string.perm_expected_desc)
                        val mono = TmmType.mono(13).fontFamily
                        TmmText(
                            text = buildAnnotatedString {
                                append(desc)
                                Regex("""\w+/""").findAll(desc).forEach {
                                    addStyle(SpanStyle(fontFamily = mono, color = TmmColors.ink), it.range.first, it.range.last + 1)
                                }
                            },
                            style = TmmType.sans(13, weight = 400, lineHeight = 19f),
                            color = TmmColors.textMuted,
                        )
                    }

                    if (state.isFixed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TmmColors.CE8F1EB)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TmmIcon(
                                icon = TmmIconId.CheckSimple,
                                tint = TmmColors.success,
                                size = 18.dp,
                                strokeWidth = 2.4f,
                            )
                            TmmText(
                                text = stringResource(R.string.perm_access_restored),
                                style = TmmType.sans(14, weight = 600),
                                color = TmmColors.success,
                            )
                        }
                    } else if (state.errorMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TmmColors.dangerBg)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TmmIcon(
                                icon = TmmIconId.FolderDenied,
                                tint = TmmColors.accent,
                                size = 18.dp,
                                strokeWidth = 2.4f,
                            )
                            TmmText(
                                text = stringResource(R.string.perm_error_no_downloads),
                                style = TmmType.sans(14, weight = 600),
                                color = TmmColors.accent,
                            )
                        }
                    }
                }
            }

            // Pied de page avec boutons d'action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.isFixed) {
                    val pickInteraction = remember { MutableInteractionSource() }
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TmmColors.ink)
                            .pressScale(pickInteraction)
                            .clickable(
                                interactionSource = pickInteraction,
                                indication = null,
                                enabled = !state.isBusy,
                                onClick = { onEvent(PermissionEvent.PickFolder) },
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isBusy) {
                            Box(modifier = Modifier.padding(end = 10.dp).rotate(rotation)) {
                                TmmIcon(
                                    icon = TmmIconId.SpinnerArc,
                                    tint = TmmColors.paper,
                                    size = 18.dp,
                                    strokeWidth = 2.4f,
                                )
                            }
                        }
                        TmmText(
                            text = if (state.isBusy) {
                                stringResource(R.string.perm_btn_verifying)
                            } else {
                                stringResource(R.string.perm_btn_pick)
                            },
                            style = TmmType.sans(16, weight = 600),
                            color = TmmColors.paper,
                        )
                    }
                } else {
                    val backInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TmmColors.accent)
                            .pressScale(backInteraction)
                            .clickable(
                                interactionSource = backInteraction,
                                indication = null,
                                onClick = onBackToHome,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmText(
                            text = stringResource(R.string.perm_btn_back_home),
                            style = TmmType.sans(16, weight = 600),
                            color = TmmColors.surface,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenSettings,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = stringResource(R.string.perm_link_settings),
                        style = TmmType.sans(14, weight = 500),
                        color = TmmColors.textMuted,
                    )
                }
            }
        }
    }
}
