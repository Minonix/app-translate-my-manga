package com.tmm.mobile.ui.screens.transfers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmCheckbox
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.core.LocalReducedMotion
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone

/**
 * Écran Transferts (Sync.dc.html, E4.4).
 * Affiche le schéma de transit (Téléphone ↔ Drive ↔ PC), la liste des transferts
 * en cours, le bloc À envoyer, le bloc À récupérer, la carte rescanner et l'alerte manquants.
 */
@Composable
fun SyncScreen(
    state: SyncState,
    onEvent: (SyncEvent) -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenMissingSeries: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        SyncHeader()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SyncHowItWorksCard(state = state, onOpenQueue = onOpenQueue)
            SyncLiveSection(state = state)
            SyncUpSection(state = state, onEvent = onEvent, onOpenQueue = onOpenQueue, onOpenLibrary = onOpenLibrary)
            SyncDownSection(state = state, onEvent = onEvent)
            SyncScanCard(state = state, onEvent = onEvent)
            if (state.missingCount > 0) {
                SyncMissingCard(missingCount = state.missingCount, onClick = onOpenMissingSeries)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SyncHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TmmText(
            text = stringResource(R.string.sync_title),
            style = TmmType.display(36, weight = 800),
            color = TmmColors.ink,
        )
        TmmText(
            text = stringResource(R.string.sync_subtitle),
            style = TmmType.sans(13, lineHeight = 19f),
            color = TmmColors.textMuted,
        )
    }
}

@Composable
private fun SyncHowItWorksCard(
    state: SyncState,
    onOpenQueue: () -> Unit,
) {
    val cardCd = stringResource(R.string.sync_how_it_works_cd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TmmColors.surface)
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .semantics { contentDescription = cardCd },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SyncTransitDiagram(pcName = state.pcName)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TmmColors.dangerBg)
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TmmText(
                        text = "${state.upCount}",
                        style = TmmType.display(22, weight = 800),
                        color = TmmColors.dangerText,
                    )
                    TmmText(
                        text = stringResource(R.string.sync_up_label),
                        style = TmmType.sans(12),
                        color = TmmColors.dangerText,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TmmColors.infoBg)
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TmmText(
                        text = "${state.downCount}",
                        style = TmmType.display(22, weight = 800),
                        color = TmmColors.info,
                    )
                    TmmText(
                        text = stringResource(R.string.sync_down_label),
                        style = TmmType.sans(12),
                        color = TmmColors.info,
                    )
                }
            }
        }
        val explainPart = stringResource(R.string.sync_explain_text)
        val queueLink = stringResource(R.string.sync_explain_queue_link)
        val annotated = buildAnnotatedString {
            append(explainPart)
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TmmColors.accent)) {
                append(queueLink)
            }
            append(".")
        }
        TmmText(
            text = annotated.text,
            style = TmmType.sans(12, lineHeight = 18f),
            color = TmmColors.textMuted,
            modifier = Modifier.clickable(onClick = onOpenQueue),
        )
    }
}

@Composable
private fun SyncTransitDiagram(pcName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransitNode(
            name = stringResource(R.string.series_legend_phone),
            icon = TmmIconId.PhoneDevice,
            bg = TmmColors.ink,
            fg = TmmColors.paper,
        )
        TransitLane(modifier = Modifier.weight(1f))
        TransitNode(
            name = stringResource(R.string.sync_cloud_label),
            icon = TmmIconId.CloudDevice,
            bg = TmmColors.CECE7DC,
            fg = TmmColors.ink,
        )
        TransitLane(modifier = Modifier.weight(1f))
        TransitNode(
            name = pcName,
            icon = TmmIconId.MonitorDevice,
            bg = TmmColors.ink,
            fg = TmmColors.paper,
        )
    }
}

@Composable
private fun TransitNode(
    name: String,
    icon: TmmIconId,
    bg: Color,
    fg: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(64.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(icon, size = 22.dp, tint = fg, strokeWidth = 1.8f)
        }
        TmmText(
            text = name,
            style = TmmType.sans(11, weight = 600),
            color = TmmColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransitLane(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val progress = if (reduced) 0.5f else {
        val transition = rememberInfiniteTransition(label = "transit")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "laneDot",
        )
        p
    }
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Track 1 : red forward
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TmmColors.dangerBg),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TmmColors.accent),
            )
        }
        // Track 2 : blue reverse
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TmmColors.infoBg),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TmmColors.info),
            )
        }
    }
}

@Composable
private fun SyncLiveSection(state: SyncState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TmmText(
            text = stringResource(R.string.sync_now_title),
            style = TmmType.sans(12, weight = 600),
            color = TmmColors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp)),
        ) {
            if (state.liveTransfers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = stringResource(R.string.sync_no_live),
                        style = TmmType.sans(13),
                        color = TmmColors.textMuted,
                    )
                }
            } else {
                state.liveTransfers.forEachIndexed { index, item ->
                    LiveTransferRow(
                        item = item,
                        isLast = index == state.liveTransfers.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTransferRow(item: LiveTransferItem, isLast: Boolean) {
    val icBg = if (item.isUpload) TmmColors.dangerBg else TmmColors.infoBg
    val icFg = if (item.isUpload) TmmColors.accent else TmmColors.info
    val icon = if (item.isUpload) TmmIconId.Up else TmmIconId.Down
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(icBg),
                contentAlignment = Alignment.Center,
            ) {
                TmmIcon(icon, size = 14.dp, tint = icFg, strokeWidth = 2.2f)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TmmText(
                        text = item.title,
                        style = TmmType.sans(13, weight = 600),
                        color = TmmColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TmmText(
                        text = item.info,
                        style = TmmType.mono(13),
                        color = TmmColors.textMuted,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TmmColors.CECE7DC),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = item.progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(icFg),
                    )
                }
            }
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TmmColors.line),
            )
        }
    }
}

@Composable
private fun SyncUpSection(
    state: SyncState,
    onEvent: (SyncEvent) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val isDone = state.sendState is SendState.Done
    val isBusy = state.sendState is SendState.Busy
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
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TmmColors.dangerBg),
                contentAlignment = Alignment.Center,
            ) {
                TmmIcon(TmmIconId.Up, size = 20.dp, tint = TmmColors.accent, strokeWidth = 2.0f)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TmmText(
                    text = stringResource(R.string.sync_up_section_title),
                    style = TmmType.sans(12, weight = 600),
                    color = TmmColors.textMuted,
                )
                val titleText = if (isDone) {
                    stringResource(R.string.sync_up_title_done)
                } else {
                    stringResource(R.string.sync_up_title_fmt, state.upCount)
                }
                TmmText(text = titleText, style = TmmType.display(19, weight = 800), color = TmmColors.ink)
                TmmText(
                    text = stringResource(R.string.sync_up_section_subtitle),
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }
        }

        if (!isDone) {
            state.upSeries.forEach { series ->
                SyncSeriesRow(series = series)
            }
            if (state.otherSeriesCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TmmColors.line)
                        .clickable(onClick = onOpenLibrary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    TmmText(
                        text = stringResource(R.string.sync_more_series_fmt, state.otherSeriesCount, state.otherChaptersCount),
                        style = TmmType.sans(13, weight = 600),
                        color = TmmColors.accent,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TmmColors.line)
                    .background(TmmColors.paperRaised)
                    .clickable { onEvent(SyncEvent.ToggleAutoTranslate) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmCheckbox(
                    checked = state.autoTranslate,
                    onCheckedChange = { onEvent(SyncEvent.ToggleAutoTranslate) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TmmText(
                        text = stringResource(R.string.sync_auto_translate_title),
                        style = TmmType.sans(14, weight = 500),
                        color = TmmColors.ink,
                    )
                    val hint = if (state.autoTranslate) {
                        stringResource(R.string.sync_auto_translate_hint_on)
                    } else {
                        stringResource(R.string.sync_auto_translate_hint_off)
                    }
                    TmmText(text = hint, style = TmmType.sans(12), color = TmmColors.textMuted)
                }
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val btnText = if (isBusy) {
                    stringResource(R.string.sync_up_button_busy)
                } else {
                    stringResource(R.string.sync_up_button_fmt, state.upCount)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TmmColors.ink)
                        .clickable(enabled = !isBusy) { onEvent(SyncEvent.SendUp) },
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(text = btnText, style = TmmType.sans(15, weight = 600), color = TmmColors.paper)
                }
            }
        } else {
            val doneCount = (state.sendState as? SendState.Done)?.chapterCount ?: 12
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TmmColors.CE8F1EB)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmIcon(TmmIconId.CheckSimple, size = 18.dp, tint = TmmColors.success, strokeWidth = 2.4f)
                TmmText(
                    text = stringResource(R.string.sync_up_done_fmt, doneCount),
                    style = TmmType.sans(13, weight = 600),
                    color = TmmColors.success,
                    modifier = Modifier.weight(1f),
                )
                TmmText(
                    text = stringResource(R.string.sync_up_follow_action),
                    style = TmmType.sans(13, weight = 600),
                    color = TmmColors.success,
                    modifier = Modifier.clickable(onClick = onOpenQueue),
                )
            }
        }
    }
}

@Composable
private fun SyncSeriesRow(series: SyncSeriesItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TmmColors.line)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp, 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(series.color)
                .halftone(TmmColors.Ink28, 5.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            TmmText(text = series.title, style = TmmType.sans(14, weight = 600), color = TmmColors.ink)
            TmmText(text = series.chapters, style = TmmType.mono(12), color = TmmColors.textMuted)
        }
        TmmText(text = series.size, style = TmmType.mono(12), color = TmmColors.textMuted)
    }
}

@Composable
private fun SyncDownSection(
    state: SyncState,
    onEvent: (SyncEvent) -> Unit,
) {
    val isDone = state.fetchState is FetchState.Done
    val isBusy = state.fetchState is FetchState.Busy
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
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
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
                TmmIcon(TmmIconId.Down, size = 20.dp, tint = TmmColors.info, strokeWidth = 2.0f)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TmmText(
                    text = stringResource(R.string.sync_down_section_title),
                    style = TmmType.sans(12, weight = 600),
                    color = TmmColors.textMuted,
                )
                val titleText = if (isDone) {
                    stringResource(R.string.sync_down_title_done)
                } else {
                    stringResource(R.string.sync_down_title_fmt, state.downCount)
                }
                TmmText(text = titleText, style = TmmType.display(19, weight = 800), color = TmmColors.ink)
                TmmText(
                    text = stringResource(R.string.sync_down_section_subtitle),
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }
        }

        state.downSeries.forEach { fetchItem ->
            SyncFetchRow(item = fetchItem)
        }

        TmmText(
            text = stringResource(R.string.sync_down_info_text),
            style = TmmType.sans(12, lineHeight = 17f),
            color = TmmColors.textMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            val btnText = when {
                isBusy -> {
                    val busy = state.fetchState as FetchState.Busy
                    stringResource(R.string.sync_down_button_busy_fmt, busy.downloaded, busy.total)
                }
                isDone -> stringResource(R.string.sync_down_button_done)
                else -> stringResource(R.string.sync_down_button_fmt, state.downCount)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, TmmColors.ink, RoundedCornerShape(12.dp))
                    .background(TmmColors.surface)
                    .clickable(enabled = !isBusy && !isDone) { onEvent(SyncEvent.GetDown) },
                contentAlignment = Alignment.Center,
            ) {
                TmmText(text = btnText, style = TmmType.sans(15, weight = 600), color = TmmColors.ink)
            }
        }
    }
}

@Composable
private fun SyncFetchRow(item: SyncFetchItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TmmColors.line)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp, 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(item.color)
                .halftone(TmmColors.Ink28, 5.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TmmColors.accent)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                TmmText(text = stringResource(R.string.series_stat_fr_tag), style = TmmType.mono(8), color = TmmColors.surface)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            TmmText(text = item.title, style = TmmType.sans(14, weight = 600), color = TmmColors.ink)
            TmmText(text = item.chapters, style = TmmType.mono(12), color = TmmColors.textMuted)
        }
        if (item.isDone) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmIcon(TmmIconId.CheckSimple, size = 14.dp, tint = TmmColors.success, strokeWidth = 2.4f)
                TmmText(
                    text = stringResource(R.string.sync_down_status_stored),
                    style = TmmType.sans(12, weight = 600),
                    color = TmmColors.success,
                )
            }
        } else {
            TmmText(text = item.size, style = TmmType.mono(12), color = TmmColors.textMuted)
        }
    }
}

@Composable
private fun SyncScanCard(
    state: SyncState,
    onEvent: (SyncEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TmmText(
                text = stringResource(R.string.sync_scan_source_info),
                style = TmmType.sans(13, weight = 500),
                color = TmmColors.ink,
            )
            val scanText = if (state.scanning) {
                stringResource(R.string.sync_scan_scanning_fmt, 104)
            } else {
                stringResource(R.string.sync_scan_last_fmt, state.scannedAgo)
            }
            TmmText(text = scanText, style = TmmType.mono(11), color = TmmColors.textMuted)
        }
        val rotation = if (state.scanning) {
            val transition = rememberInfiniteTransition(label = "spin")
            val r by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "rescanRotate",
            )
            r
        } else 0f
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(TmmColors.paperRaised)
                .border(1.dp, TmmColors.track, RoundedCornerShape(9.dp))
                .clickable(enabled = !state.scanning) { onEvent(SyncEvent.Rescan) }
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.rotate(rotation)) {
                TmmIcon(TmmIconId.Refresh, size = 14.dp, tint = TmmColors.ink, strokeWidth = 2.0f)
            }
            TmmText(
                text = stringResource(R.string.sync_rescan_button),
                style = TmmType.sans(12, weight = 600),
                color = TmmColors.ink,
            )
        }
    }
}

@Composable
private fun SyncMissingCard(
    missingCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .dashedBorder(color = TmmColors.track, width = 1.dp, cornerRadius = 14.dp)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmIcon(TmmIconId.Warning, size = 18.dp, tint = TmmColors.accent, strokeWidth = 2.0f)
        TmmText(
            text = pluralStringResource(R.plurals.sync_missing_count, missingCount, missingCount),
            style = TmmType.sans(14, weight = 500),
            color = TmmColors.ink,
            modifier = Modifier.weight(1f),
        )
        TmmIcon(TmmIconId.ChevronRight, size = 16.dp, tint = TmmColors.ink, strokeWidth = 2.0f)
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncScreenPreview() {
    TmmTheme {
        SyncScreen(state = MockupData.sync, onEvent = {})
    }
}
