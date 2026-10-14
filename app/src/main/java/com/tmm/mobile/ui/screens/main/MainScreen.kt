package com.tmm.mobile.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Accueil v2 (maquettes/v2-simplifiee/index.html). Ne rend que le contenu propre a l'ecran ;
 * la barre de navigation basse est commune aux 13 ecrans et sera posee par
 * TmmRoot en E10 (docs/PLAN_GEMINI.md, section E10).
 */
@Composable
fun MainScreen(
    state: MainState,
    onEvent: (MainEvent) -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenSeries: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenTranslate: () -> Unit = {},
    onManageFollowed: () -> Unit = {},
) {
    Box(modifier = modifier.background(TmmColors.paper)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MainHeader(state = state, onEvent = onEvent)
            if (state.isOffline && state.offlineSinceLabel != null) OfflineNotice(state.offlineSinceLabel)
            // Sections vides masquees : spacedBy n'espace que les enfants emis, donc pas de trou.
            // La carte "A faire" reste visible pendant une installation en cours (sinon elle
            // disparaitrait au milieu de la progression, toGetCount baissant a chaque installation) ;
            // l'etat Done ne revenant jamais a Idle, il ne compte pas.
            if (state.toSendCount > 0 || state.toGetCount > 0 || state.getState == BusyState.Busy) {
                TodoSection(state = state, onEvent = onEvent, onOpenTranslate = onOpenTranslate)
            }
            if (state.followedSeries.isNotEmpty()) FollowedSection(state.followedSeries, onManageFollowed, onOpenSeries)
            if (state.queueJobs.isNotEmpty() || state.queueCount > 0) OnPcNowSection(state = state, onOpenQueue = onOpenQueue)
            if (state.recentChapters.isNotEmpty()) RecentChaptersSection(state = state, onSeeAll = onOpenQueue, onOpenSeries = onOpenSeries)
        }
        val toastMessage = when (val toast = state.toast) {
            is MainToast.Received -> stringResource(R.string.home_toast_received_fmt, toast.count)
            is MainToast.SentOriginals -> pluralStringResource(R.plurals.home_v2_toast_sent_originals, toast.count, toast.count)
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
private fun MainHeader(state: MainState, onEvent: (MainEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(TmmColors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(text = stringResource(R.string.app_name_glyph), style = TmmType.display(20, weight = 800), color = TmmColors.surface)
                }
                TmmText(text = stringResource(R.string.home_v2_brand), style = TmmType.display(20, weight = 800))
            }
            PcStatusPill(state)
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TmmText(text = stringResource(R.string.home_title), style = TmmType.display(40, weight = 800))
                state.lastCheckLabel?.let { label ->
                    TmmText(
                        text = stringResource(R.string.home_v2_last_check, label),
                        style = TmmType.sans(13),
                        color = TmmColors.textMuted,
                    )
                }
            }
            // Le bouton rond seul ne se reconnaissait pas comme la synchronisation avec le PC : legende dessous.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RefreshButton(onRefresh = { onEvent(MainEvent.Refresh) }, onSpinComplete = { onEvent(MainEvent.RefreshFinished) })
                TmmText(text = stringResource(R.string.home_sync), style = TmmType.sans(12, weight = 600), color = TmmColors.textMuted)
            }
        }
    }
}

@Composable
private fun PcStatusPill(state: MainState) {
    val label = when (state.pcTone) {
        PcTone.Online -> stringResource(R.string.home_v2_pc_online)
        PcTone.Seen -> stringResource(R.string.home_pc_seen_fmt, state.pcSeenSinceLabel.orEmpty())
        PcTone.Offline -> stringResource(R.string.home_v2_pc_offline)
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
            TmmText(text = stringResource(R.string.home_v2_offline_bold, sinceLabel), style = TmmType.sans(13, weight = 600), color = TmmColors.info)
            TmmText(text = stringResource(R.string.home_offline_rest), style = TmmType.sans(13, lineHeight = 19f), color = TmmColors.info)
        }
    }
}

@Composable
private fun TodoSection(state: MainState, onEvent: (MainEvent) -> Unit, onOpenTranslate: () -> Unit) {
    val description = stringResource(R.string.home_v2_todo)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TmmColors.ink)
            .semantics { contentDescription = description }.padding(20.dp),
    ) {
        TmmText(stringResource(R.string.home_v2_todo).uppercase(Locale.FRENCH),
            style = TmmType.sans(12, weight = 600, letterSpacingEm = 0.1f), color = TmmColors.textOnInkMuted)
        if (state.toSendCount > 0) {
            TodoRow(state.toSendCount, stringResource(R.string.home_v2_translate_label),
                pluralStringResource(R.plurals.home_v2_to_send_series, state.toSendSeriesCount, state.toSendSeriesCount)) {
                InlineButton(stringResource(R.string.home_v2_translate), filled = true, onClick = onOpenTranslate)
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmText(stringResource(R.string.home_v2_send_originals_label), style = TmmType.sans(13),
                    color = TmmColors.textOnInkMuted, modifier = Modifier.weight(1f))
                InlineButton(stringResource(R.string.home_v2_send_originals), filled = false) {
                    if (state.sendOriginalsState != BusyState.Busy) onEvent(MainEvent.SendOriginals)
                }
            }
        }
        if (state.toGetCount > 0 || state.getState == BusyState.Busy) {
            if (state.toSendCount > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TmmColors.C34332E))
            TodoRow(state.toGetCount, stringResource(R.string.home_v2_install_label), state.toGetSummary) {
                if (state.getState == BusyState.Busy) ProgressPill(state.getFraction, state.installTotal)
                else InlineButton(stringResource(R.string.home_v2_install), filled = false) { onEvent(MainEvent.FetchTranslated) }
            }
        }
    }
}

@Composable
private fun TodoRow(count: Int, label: String, summary: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        TmmText(count.toString(), style = TmmType.display(46, weight = 800), color = TmmColors.paper)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TmmText(label, style = TmmType.sans(16, weight = 600, lineHeight = 20f), color = TmmColors.paper)
            TmmText(summary, style = TmmType.sans(13), color = TmmColors.textOnInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (filled) Modifier.background(bg) else Modifier.border(1.dp, TmmColors.paper, RoundedCornerShape(14.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmText(text = label, style = TmmType.sans(14, weight = 600), color = fg)
    }
}

@Composable
private fun ProgressPill(fraction: Float, total: Int) {
    Box(
        modifier = Modifier.height(40.dp).width(96.dp).clip(RoundedCornerShape(10.dp)).background(TmmColors.C2E2D29),
        contentAlignment = Alignment.Center,
    ) {
        TmmText(
            text = stringResource(R.string.home_get_progress_fmt, (fraction * total).roundToInt(), total),
            style = TmmType.mono(12),
            color = TmmColors.paper,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowedSection(series: List<MainFollowedSeries>, onManage: () -> Unit, onOpenSeries: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TmmColors.CFBF9F4)
        .border(1.dp, TmmColors.line, RoundedCornerShape(22.dp)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(TmmColors.ink), contentAlignment = Alignment.Center) {
            TmmIcon(TmmIconId.V2Bolt, tint = TmmColors.surface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TmmText(pluralStringResource(R.plurals.home_v2_followed, series.size, series.size), style = TmmType.sans(16, weight = 600), modifier = Modifier.weight(1f))
                TmmText(stringResource(R.string.home_v2_manage), style = TmmType.sans(14, weight = 600), color = TmmColors.accent, modifier = Modifier.clickable(onClick = onManage))
            }
            TmmText(stringResource(R.string.home_v2_follow_description), style = TmmType.sans(13, lineHeight = 19f), color = TmmColors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                series.forEach { item ->
                    TmmText(item.title, style = TmmType.sans(13, weight = 600), modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(TmmColors.CE9E4D8).clickable { onOpenSeries(item.seriesKey) }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun OnPcNowSection(state: MainState, onOpenQueue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TmmText(stringResource(R.string.home_v2_pc_title), style = TmmType.display(22, weight = 800))
            TmmText(stringResource(R.string.home_v2_activity, state.queueCount), style = TmmType.sans(14, weight = 600), color = TmmColors.accent, modifier = Modifier.clickable(onClick = onOpenQueue))
        }
        if (state.queueJobs.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TmmColors.CFBF9F4)
            .border(1.dp, TmmColors.line, RoundedCornerShape(22.dp)).clickable(onClick = onOpenQueue).padding(horizontal = 16.dp, vertical = 6.dp)) {
            state.queueJobs.take(2).forEachIndexed { index, job ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TmmColors.CECE7DC))
                JobRow(job)
            }
        }
        TmmText(stringResource(R.string.home_v2_queue_summary, state.queueNextCount, state.queueParallelCount, state.queueMachineName), style = TmmType.sans(13), color = TmmColors.textMuted)
    }
}

@Composable
private fun JobRow(job: MainQueueJob) {
    val label = stringResource(when (job.type) {
        MainJobType.Translation -> R.string.home_job_type_translation
        MainJobType.Verification -> R.string.home_job_type_verification
        MainJobType.Retranslation -> R.string.home_job_type_retranslation
    })
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TmmText(job.title, style = TmmType.sans(15, weight = 600), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TmmText(stringResource(R.string.home_v2_percent, (job.progressFraction * 100).roundToInt()), style = TmmType.mono(13), color = TmmColors.textMuted)
        }
        ProgressBar(fraction = job.progressFraction, fillColor = TmmColors.accent)
        TmmText(if (job.page != null && job.pages != null) stringResource(R.string.home_v2_job_page, label, job.page, job.pages) else label,
            style = TmmType.sans(12), color = TmmColors.textMuted)
    }
}

@Composable
private fun RecentChaptersSection(state: MainState, onSeeAll: () -> Unit, onOpenSeries: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TmmText(text = stringResource(R.string.home_v2_recent_title), style = TmmType.display(22, weight = 800))
            TmmText(text = stringResource(R.string.home_see_all), style = TmmType.sans(14, weight = 600), color = TmmColors.accent, modifier = Modifier.clickable(onClick = onSeeAll))
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
    val status = stringResource(if (chapter.inMihon) R.string.home_v2_in_mihon else R.string.home_v2_ready)
    Column(Modifier.width(96.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(10.dp)).background(chapter.color).halftone())
        TmmText(chapter.title, style = TmmType.sans(13, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TmmText(stringResource(R.string.home_v2_recent_status, chapter.chapterLabel, status), style = TmmType.sans(12), color = TmmColors.textMuted)
    }
}
