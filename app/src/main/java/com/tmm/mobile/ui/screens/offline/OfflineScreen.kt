package com.tmm.mobile.ui.screens.offline

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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
import com.tmm.mobile.ui.theme.halftone
import com.tmm.mobile.ui.theme.pressScale

/**
 * Écran Hors ligne (Offline.dc.html, E4.12).
 * S'affiche en cas de perte de connectivité réseau ou d'expiration du jeton Google Drive.
 * La barre de navigation basse commune est posée par TmmRoot en E10.
 */
@Composable
fun OfflineScreen(
    state: OfflineState,
    onEvent: (OfflineEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Bannière selon la cause
            when (state.cause) {
                OfflineCause.Network -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TmmColors.ink)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TmmIcon(
                            icon = TmmIconId.WifiOff,
                            tint = TmmColors.paper,
                            size = 18.dp,
                        )
                        Row(modifier = Modifier.weight(1f)) {
                            TmmText(
                                text = stringResource(R.string.offline_banner_network_title) + " ",
                                style = TmmType.sans(13, weight = 600),
                                color = TmmColors.paper,
                            )
                            TmmText(
                                text = stringResource(R.string.offline_banner_network_desc),
                                style = TmmType.sans(13, weight = 400),
                                color = TmmColors.paper,
                            )
                        }
                    }
                }
                OfflineCause.Token -> {
                    val reconnectInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TmmColors.dangerBg)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TmmIcon(
                            icon = TmmIconId.Lock,
                            tint = TmmColors.dangerText,
                            size = 18.dp,
                        )
                        TmmText(
                            text = stringResource(R.string.offline_banner_token_title),
                            style = TmmType.sans(13, weight = 600),
                            color = TmmColors.dangerText,
                            modifier = Modifier.weight(1f),
                        )
                        val reLabel = if (state.isReconnecting) {
                            stringResource(R.string.offline_btn_opening)
                        } else {
                            stringResource(R.string.offline_btn_reconnect)
                        }
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(TmmColors.dangerText)
                                .pressScale(reconnectInteraction)
                                .clickable(
                                    interactionSource = reconnectInteraction,
                                    indication = null,
                                    enabled = !state.isReconnecting,
                                    onClick = { onEvent(OfflineEvent.Reconnect) },
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TmmText(
                                text = reLabel,
                                style = TmmType.sans(13, weight = 600),
                                color = TmmColors.surface,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                OfflineHeader(state = state)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Section 1: En attente d'envoi
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(TmmColors.surface)
                            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(18.dp)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TmmText(
                                text = stringResource(R.string.offline_pending_title),
                                style = TmmType.display(19, weight = 800),
                                color = TmmColors.ink,
                                modifier = Modifier.semantics { heading() },
                            )
                            TmmText(
                                text = pluralStringResource(R.plurals.offline_pending_count, state.pendingCount, state.pendingCount),
                                style = TmmType.mono(12, weight = 500),
                                color = TmmColors.textMuted,
                            )
                        }

                        val defaultWhy = if (state.cause == OfflineCause.Token) {
                            stringResource(R.string.offline_why_token)
                        } else {
                            stringResource(R.string.offline_why_network)
                        }

                        state.pendingItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(width = 0.5.dp, color = TmmColors.line)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 32.dp, height = 44.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(item.color)
                                        .halftone(),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    TmmText(
                                        text = item.title,
                                        style = TmmType.sans(14, weight = 600),
                                        color = TmmColors.ink,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        TmmIcon(
                                            icon = TmmIconId.Clock,
                                            tint = TmmColors.textMuted,
                                            size = 12.dp,
                                        )
                                        TmmText(
                                            text = item.why ?: defaultWhy,
                                            style = TmmType.sans(12, weight = 400),
                                            color = TmmColors.textMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Toujours disponible
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TmmText(
                            text = stringResource(R.string.offline_available_title),
                            style = TmmType.display(19, weight = 800),
                            color = TmmColors.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        TmmText(
                            text = stringResource(R.string.offline_available_desc),
                            style = TmmType.sans(13, weight = 400, lineHeight = 19f),
                            color = TmmColors.textMuted,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            listOf(
                                TmmColors.CE2C6BB,
                                TmmColors.CCFD8C6,
                                TmmColors.CD8D2E4,
                            ).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color)
                                        .halftone(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineHeader(state: OfflineState) {
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
            Row(
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
                        .border(1.5.dp, TmmColors.C9C9486, CircleShape)
                )
                TmmText(
                    text = state.pcStateText,
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
