package com.tmm.mobile.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.ProgressBar
import com.tmm.mobile.ui.components.RefreshButton
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.TmmToast
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone
import com.tmm.mobile.ui.theme.pressScale
import java.util.Locale

/**
 * Ecran Accueil (Main.dc.html, E4.1). Ne rend que le contenu propre a l'ecran ;
 * la barre de navigation basse est commune aux 13 ecrans et sera posee par
 * TmmRoot en E10 (docs/PLAN_GEMINI.md, section E10).
 */
@Composable
fun MainScreen(
    state: MainState,
    onEvent: (MainEvent) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenNewTranslation: () -> Unit = {},
    onOpenSeries: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(TmmColors.paper)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MainHeader(state = state, onEvent = onEvent, onOpenSettings = onOpenSettings)
            if (state.isOffline && state.offlineSinceLabel != null) OfflineNotice(state.offlineSinceLabel)
            TodoSection(state = state, onEvent = onEvent, onOpenQueue = onOpenQueue)
            OnPcNowSection(state = state, onOpenQueue = onOpenQueue)
            QuickActionsGrid(onOpenNewTranslation = onOpenNewTranslation, onOpenSync = onOpenSync, lastScanLabel = state.lastScanLabel)
            RecentChaptersSection(state = state, onOpenSync = onOpenSync, onOpenSeries = onOpenSeries)
        }
        val toastMessage = when (val toast = state.toast) {
            is MainToast.Sent -> stringResource(R.string.home_toast_sent_fmt, toast.count)
            is MainToast.Received -> stringResource(R.string.home_toast_received_fmt, toast.count)
            null -> ""
        }
        TmmToast(
            visible = state.toast != null,
            message = toastMessage,
            onDismiss = { onEvent(MainEvent.DismissToast) },
            actionLabel = stringResource(R.string.home_toast_action),
            onAction = onOpenQueue,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun MainHeader(state: MainState, onEvent: (MainEvent) -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(TmmColors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(text = "訳", style = TmmType.display(20, weight = 800), color = TmmColors.surface)
                }
                TmmText(text = stringResource(R.string.app_name), style = TmmType.display(17, weight = 800))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PcStatusPill(state)
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .pressScale(interactionSource)
                        .clip(CircleShape)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(TmmIconId.Settings, contentDescription = stringResource(R.string.home_settings_cd))
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TmmText(text = stringResource(R.string.home_title), style = TmmType.display(36, weight = 800))
                state.lastCheckLabel?.let { label ->
                    TmmText(
                        text = stringResource(R.string.home_last_check_fmt, label),
                        style = TmmType.sans(13),
                        color = TmmColors.textMuted,
                    )
                }
            }
            RefreshButton(onRefresh = { onEvent(MainEvent.Refresh) }, onSpinComplete = { onEvent(MainEvent.RefreshFinished) })
        }
    }
}

@Composable
private fun PcStatusPill(state: MainState) {
    val label = when (state.pcTone) {
        PcTone.Online -> stringResource(R.string.home_pc_online)
        PcTone.Seen -> stringResource(R.string.home_pc_seen_fmt, state.pcSeenSinceLabel.orEmpty())
        PcTone.Offline -> stringResource(R.string.home_pc_offline)
    }
    val dotColor = when (state.pcTone) {
        PcTone.Online -> TmmColors.success
        PcTone.Seen -> TmmColors.C9C9486
        PcTone.Offline -> TmmColors.dangerText
    }
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TmmColors.surface)
            .border(1.dp, TmmColors.track, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        TmmText(text = label, style = TmmType.sans(13, weight = 600))
    }
}

@Composable
private fun OfflineNotice(sinceLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TmmColors.infoBg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TmmIcon(TmmIconId.Cloud, size = 18.dp, tint = TmmColors.info)
        Column {
            TmmText(text = stringResource(R.string.home_offline_bold, sinceLabel), style = TmmType.sans(13, weight = 600), color = TmmColors.info)
            TmmText(text = stringResource(R.string.home_offline_rest), style = TmmType.sans(13, lineHeight = 19f), color = TmmColors.info)
        }
    }
}

@Composable
private fun TodoSection(state: MainState, onEvent: (MainEvent) -> Unit, onOpenQueue: () -> Unit) {
    val todoSectionCd = stringResource(R.string.home_todo_section_cd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TmmColors.ink)
            .semantics { contentDescription = todoSectionCd }
            .padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            TmmText(
                text = stringResource(R.string.home_todo_section_label).uppercase(Locale.FRENCH),
                style = TmmType.sans(12, weight = 600, letterSpacingEm = 0.06f),
                color = TmmColors.textOnInkMuted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenQueue),
            ) {
                TmmText(text = stringResource(R.string.home_details), style = TmmType.sans(13, weight = 600), color = TmmColors.paper)
                TmmIcon(TmmIconId.ChevronRight, size = 16.dp, tint = TmmColors.paper)
            }
        }
        TodoRow(
            count = state.toSendCount,
            icons = listOf(TmmIconId.Phone, TmmIconId.ArrowRight, TmmIconId.Monitor),
            label = stringResource(R.string.home_send_label),
            summary = state.toSendSummary,
        ) {
            when (state.sendState) {
                BusyState.Idle -> InlineButton(label = stringResource(R.string.home_send_button), filled = true) { onEvent(MainEvent.SendPending) }
                BusyState.Busy -> BusySpinnerPill(contentDescription = stringResource(R.string.home_send_in_progress_cd))
                BusyState.Done -> DonePill(label = stringResource(R.string.home_send_done), onClick = onOpenQueue)
            }
        }
        TodoRow(
            count = state.toGetCount,
            icons = listOf(TmmIconId.ArrowRight, TmmIconId.PhoneOutline),
            label = stringResource(R.string.home_get_label),
            summary = state.toGetSummary,
        ) {
            when (state.getState) {
                BusyState.Idle -> InlineButton(label = stringResource(R.string.home_get_button), filled = false) { onEvent(MainEvent.FetchTranslated) }
                BusyState.Busy -> ProgressPill(fraction = state.getFraction, total = state.toGetCount)
                BusyState.Done -> DonePill(label = stringResource(R.string.home_status_in_mihon), onClick = onOpenQueue)
            }
        }
    }
}

@Composable
private fun TodoRow(count: Int, icons: List<TmmIconId>, label: String, summary: String, action: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TmmColors.C2E2D29))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmText(text = count.toString(), style = TmmType.display(40, weight = 800), color = TmmColors.paper, modifier = Modifier.width(56.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                icons.forEach { TmmIcon(it, size = 15.dp, tint = TmmColors.textOnInkMuted) }
            }
            TmmText(text = label, style = TmmType.sans(14, weight = 600), color = TmmColors.paper)
            TmmText(text = summary, style = TmmType.sans(12), color = TmmColors.textOnInkMuted)
        }
        action()
    }
}

@Composable
private fun InlineButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) TmmColors.accent else TmmColors.ink
    val fg = if (filled) TmmColors.surface else TmmColors.paper
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (filled) Modifier.background(bg) else Modifier.border(1.dp, TmmColors.paper, RoundedCornerShape(10.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmText(text = label, style = TmmType.sans(14, weight = 600), color = fg)
    }
}

@Composable
private fun BusySpinnerPill(contentDescription: String) {
    Box(
        modifier = Modifier.height(40.dp).width(80.dp).clip(RoundedCornerShape(10.dp)).background(TmmColors.C2E2D29),
        contentAlignment = Alignment.Center,
    ) {
        TmmIcon(TmmIconId.SpinnerArc, size = 18.dp, tint = TmmColors.paper, contentDescription = contentDescription)
    }
}

@Composable
private fun ProgressPill(fraction: Float, total: Int) {
    Box(
        modifier = Modifier.height(40.dp).width(96.dp).clip(RoundedCornerShape(10.dp)).background(TmmColors.C2E2D29),
        contentAlignment = Alignment.Center,
    ) {
        TmmText(
            text = stringResource(R.string.home_get_progress_fmt, (fraction * total).toInt(), total),
            style = TmmType.mono(12),
            color = TmmColors.paper,
        )
    }
}

@Composable
private fun DonePill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmIcon(TmmIconId.CheckSimple, size = 16.dp, tint = TmmColors.C9BD3AE)
        TmmText(text = label, style = TmmType.sans(13, weight = 600), color = TmmColors.C9BD3AE)
    }
}

@Composable
private fun OnPcNowSection(state: MainState, onOpenQueue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TmmText(text = stringResource(R.string.home_now_on_pc_title), style = TmmType.display(19, weight = 800))
            TmmText(
                text = stringResource(R.string.home_queue_link_fmt, state.queueCount),
                style = TmmType.sans(13, weight = 600).copy(textDecoration = TextDecoration.Underline),
                color = TmmColors.accent,
                modifier = Modifier.clickable(onClick = onOpenQueue),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TmmColors.surface)
                .clickable(onClick = onOpenQueue)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TmmColors.success))
                TmmText(
                    text = stringResource(R.string.home_parallel_status_fmt, state.queueParallelCount, state.queueMachineName),
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                TmmText(text = stringResource(R.string.home_next_fmt, state.queueNextCount), style = TmmType.mono(12))
            }
            state.queueJobs.forEach { job -> JobRow(job) }
        }
    }
}

private data class JobTypeStyle(
    val labelRes: Int,
    val badgeBg: androidx.compose.ui.graphics.Color,
    val badgeFg: androidx.compose.ui.graphics.Color,
    val barColor: androidx.compose.ui.graphics.Color,
)

private fun jobTypeStyle(type: MainJobType): JobTypeStyle = when (type) {
    MainJobType.Translation -> JobTypeStyle(R.string.home_job_type_translation, TmmColors.dangerBg, TmmColors.dangerText, TmmColors.ink)
    MainJobType.Verification -> JobTypeStyle(R.string.home_job_type_verification, TmmColors.infoBg, TmmColors.info, TmmColors.info)
    MainJobType.Retranslation -> JobTypeStyle(R.string.home_job_type_retranslation, TmmColors.line, TmmColors.textMuted, TmmColors.accent)
}

@Composable
private fun JobRow(job: MainQueueJob) {
    val style = jobTypeStyle(job.type)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(30.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(job.color).halftone())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TmmText(
                    text = job.title,
                    style = TmmType.sans(13, weight = 600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(style.badgeBg).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    TmmText(text = stringResource(style.labelRes), style = TmmType.sans(10, weight = 600), color = style.badgeFg)
                }
                TmmText(
                    text = "${(job.progressFraction * 100).toInt()}%",
                    style = TmmType.mono(11),
                    color = TmmColors.textMuted,
                    modifier = Modifier.width(34.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            ProgressBar(fraction = job.progressFraction, fillColor = style.barColor)
        }
    }
}

@Composable
private fun QuickActionsGrid(onOpenNewTranslation: () -> Unit, onOpenSync: () -> Unit, lastScanLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickActionCard(
            icon = TmmIconId.Translate,
            iconBg = TmmColors.dangerBg,
            iconTint = TmmColors.accent,
            title = stringResource(R.string.home_new_translation_title),
            subtitle = stringResource(R.string.home_new_translation_subtitle),
            onClick = onOpenNewTranslation,
            modifier = Modifier.weight(1f),
        )
        QuickActionCard(
            icon = TmmIconId.Sync,
            iconBg = TmmColors.infoBg,
            iconTint = TmmColors.info,
            title = stringResource(R.string.home_scan_mihon_title),
            subtitle = lastScanLabel,
            onClick = onOpenSync,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: TmmIconId,
    iconBg: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TmmColors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            TmmIcon(icon, size = 20.dp, tint = iconTint)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TmmText(text = title, style = TmmType.sans(14, weight = 600))
            TmmText(text = subtitle, style = TmmType.sans(12), color = TmmColors.textMuted)
        }
    }
}

@Composable
private fun RecentChaptersSection(state: MainState, onOpenSync: () -> Unit, onOpenSeries: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TmmText(text = stringResource(R.string.home_recent_title), style = TmmType.display(19, weight = 800))
            TmmText(text = stringResource(R.string.home_see_all), style = TmmType.sans(13, weight = 600), modifier = Modifier.clickable(onClick = onOpenSync))
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.recentChapters.forEach { chapter ->
                RecentChapterCard(chapter, onClick = { onOpenSeries(chapter.seriesKey) })
            }
        }
    }
}

@Composable
private fun RecentChapterCard(chapter: MainRecentChapter, onClick: () -> Unit) {
    val statusLabel = if (chapter.inMihon) stringResource(R.string.home_status_in_mihon) else stringResource(R.string.home_status_on_pc)
    val statusColor = if (chapter.inMihon) TmmColors.success else TmmColors.info
    Column(
        modifier = Modifier.width(112.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.height(140.dp).clip(RoundedCornerShape(8.dp)).background(chapter.color).halftone()) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TmmColors.ink)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                TmmText(text = "FR", style = TmmType.mono(11), color = TmmColors.paper)
            }
        }
        TmmText(text = chapter.title, style = TmmType.sans(13, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TmmText(text = chapter.chapterLabel, style = TmmType.mono(11), color = TmmColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TmmIcon(if (chapter.inMihon) TmmIconId.CheckSimple else TmmIconId.Download, size = 14.dp, tint = statusColor)
            TmmText(text = statusLabel, style = TmmType.sans(12, weight = 600), color = statusColor)
        }
    }
}
