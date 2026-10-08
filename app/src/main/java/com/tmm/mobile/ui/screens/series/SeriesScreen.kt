package com.tmm.mobile.ui.screens.series

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.components.TmmCheckbox
import com.tmm.mobile.ui.components.TmmSheet
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.TmmToast
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone
import com.tmm.mobile.ui.theme.pressScale

/**
 * Ecran Detail d'une serie (Series.dc.html, E4.3). Contenu propre a l'ecran seulement.
 */
@Composable
fun SeriesScreen(
    state: SeriesState,
    onEvent: (SeriesEvent) -> Unit,
    onBack: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(TmmColors.paper)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SeriesHeader(onBack = onBack, onOpenSeries = { onEvent(SeriesEvent.OpenSeriesSheet) })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SeriesHero(state = state)
                SeriesLocationStats(state = state)
                if (state.showGap && state.gapChapter != null) {
                    SeriesGapAlert(gapNumber = state.gapChapter!!.number, onDismiss = { onEvent(SeriesEvent.DismissGap) })
                }
                SeriesChaptersSection(state = state, onEvent = onEvent)
            }
            if (state.hasSelection) {
                SeriesSelectionActionBar(state = state, onEvent = onEvent)
            }
        }
        SeriesSheetContent(state = state, onEvent = onEvent)
        val toastMessage = when (val toast = state.toast) {
            is SeriesToast.Sent -> pluralStringResource(R.plurals.series_toast_sent, toast.chapterCount, toast.chapterCount)
            is SeriesToast.Received -> pluralStringResource(R.plurals.series_toast_received, toast.chapterCount, toast.chapterCount)
            is SeriesToast.Verified -> pluralStringResource(R.plurals.series_toast_verified, toast.chapterCount, toast.chapterCount)
            is SeriesToast.Translated -> pluralStringResource(R.plurals.series_toast_translated, toast.chapterCount, toast.chapterCount)
            is SeriesToast.Retranslated -> pluralStringResource(R.plurals.series_toast_retranslated, toast.chapterCount, toast.chapterCount)
            null -> ""
        }
        TmmToast(
            visible = state.toast != null,
            message = toastMessage,
            onDismiss = { onEvent(SeriesEvent.DismissToast) },
            actionLabel = stringResource(R.string.library_toast_action),
            onAction = onOpenQueue,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SeriesHeader(onBack: () -> Unit, onOpenSeries: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backCd = stringResource(R.string.series_back_cd)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = backCd },
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(TmmIconId.ChevronLeft, size = 22.dp, tint = TmmColors.ink)
        }
        TmmText(text = stringResource(R.string.library_title), style = TmmType.sans(13), color = TmmColors.textMuted)
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.track, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenSeries)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmIcon(TmmIconId.MoreDots, size = 16.dp, tint = TmmColors.ink)
            TmmText(text = stringResource(R.string.series_sheet_title), style = TmmType.sans(13, weight = 600), color = TmmColors.ink)
        }
    }
}

@Composable
private fun SeriesHero(state: SeriesState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp, 130.dp)
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(state.coverColor)
                .halftone(TmmColors.Ink28, 5.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(TmmColors.ink)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                TmmText(text = state.categoryLabel, style = TmmType.sans(12, weight = 600), color = TmmColors.paper)
            }
            TmmText(text = state.title, style = TmmType.display(32, weight = 800), color = TmmColors.ink)
            TmmText(text = state.formatLabel, style = TmmType.sans(13), color = TmmColors.textMuted)
        }
    }
}

@Composable
private fun SeriesLocationStats(state: SeriesState) {
    val cd = stringResource(R.string.series_location_stats_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cd },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LocationCard(
            icon = TmmIconId.PhoneOutline,
            title = stringResource(R.string.series_legend_phone),
            count = state.onPhoneCount,
            tint = TmmColors.info,
            modifier = Modifier.weight(1f),
        )
        LocationCard(
            icon = TmmIconId.Monitor,
            title = stringResource(R.string.series_stat_pc_original),
            count = state.onPcCount,
            tint = TmmColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        LocationCardFr(
            count = state.frCount,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LocationCard(icon: TmmIconId, title: String, count: Int, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(TmmColors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TmmIcon(icon, size = 14.dp, tint = tint)
            TmmText(text = title, style = TmmType.sans(12, weight = 600), color = tint)
        }
        TmmText(text = "$count", style = TmmType.display(24, weight = 800), color = TmmColors.ink)
    }
}

@Composable
private fun LocationCardFr(count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(TmmColors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(TmmColors.accent)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                TmmText(text = stringResource(R.string.series_stat_fr_tag), style = TmmType.mono(9), color = TmmColors.surface)
            }
            TmmText(text = stringResource(R.string.series_stat_translated_label), style = TmmType.sans(12, weight = 600), color = TmmColors.accent)
        }
        TmmText(text = "$count", style = TmmType.display(24, weight = 800), color = TmmColors.ink)
    }
}

@Composable
private fun SeriesGapAlert(gapNumber: Int, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TmmColors.dangerBg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TmmIcon(TmmIconId.Warning, size = 18.dp, tint = TmmColors.dangerText)
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(stringResource(R.string.series_gap_bold_fmt, gapNumber))
            }
            append(" ")
            append(stringResource(R.string.series_gap_rest))
        }
        TmmText(
            text = annotated,
            style = TmmType.sans(13, lineHeight = 19f),
            color = TmmColors.dangerText,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, TmmColors.dangerText, RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            TmmText(text = stringResource(R.string.series_gap_dismiss), style = TmmType.sans(12, weight = 600), color = TmmColors.dangerText)
        }
    }
}

@Composable
private fun SeriesChaptersSection(state: SeriesState, onEvent: (SeriesEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            TmmText(text = stringResource(R.string.series_chapters_title), style = TmmType.display(19, weight = 800))
            TmmText(
                text = stringResource(R.string.series_select_todo_button),
                style = TmmType.sans(13, weight = 600),
                color = TmmColors.accent,
                modifier = Modifier.clickable { onEvent(SeriesEvent.SelectTodo) },
            )
        }
        SeriesLegendRow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp)),
        ) {
            val visibleChapters = state.chapters.filter { state.showGap || it.kind != StatusTone.Gap }
            visibleChapters.forEachIndexed { index, chapter ->
                SeriesChapterRow(
                    chapter = chapter,
                    selected = chapter.number in state.selected,
                    statsExpanded = chapter.number in state.expandedStats,
                    onToggle = { onEvent(SeriesEvent.ToggleChapter(chapter.number)) },
                    onToggleStats = { onEvent(SeriesEvent.ToggleStats(chapter.number)) },
                    isLast = index == visibleChapters.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun SeriesLegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = TmmColors.info, label = stringResource(R.string.series_legend_phone))
        LegendItem(color = TmmColors.textMuted, label = stringResource(R.string.series_legend_pc))
        LegendItem(color = TmmColors.accent, label = stringResource(R.string.series_legend_fr))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .dashedBorder(color = TmmColors.CB9B0A0, width = 1.dp, cornerRadius = 2.dp),
            )
            TmmText(text = stringResource(R.string.series_legend_absent), style = TmmType.sans(11), color = TmmColors.textMuted)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        TmmText(text = label, style = TmmType.sans(11), color = TmmColors.textMuted)
    }
}

@Composable
private fun SeriesChapterRow(
    chapter: SeriesChapter,
    selected: Boolean,
    statsExpanded: Boolean,
    onToggle: () -> Unit,
    onToggleStats: () -> Unit,
    isLast: Boolean,
) {
    val rowBg = if (selected) TmmColors.paperRaised else TmmColors.surface
    val ariaLabel = stringResource(R.string.series_chapter_aria_fmt, chapter.number, chapter.statusLabel)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .then(if (!isLast) Modifier.border(0.dp, Color.Transparent).drawBehindBottomLine(TmmColors.line) else Modifier)
            .clickable(enabled = !chapter.locked, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmCheckbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = !chapter.locked,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TmmText(
                    text = stringResource(R.string.series_chapter_number_fmt, chapter.number),
                    style = TmmType.mono(14),
                    color = TmmColors.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(chapter.statusColor),
                    )
                    TmmText(text = chapter.statusLabel, style = TmmType.sans(12, weight = 600), color = chapter.statusColor)
                }
                if (chapter.translationProgress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TmmColors.line),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(chapter.translationProgress)
                                .height(4.dp)
                                .background(TmmColors.ink),
                        )
                    }
                }
            }
            if (chapter.stats != null) {
                val statsCd = stringResource(R.string.series_chapter_stats_cd_fmt, chapter.number)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (statsExpanded) TmmColors.line else Color.Transparent)
                        .clickable(onClick = onToggleStats)
                        .semantics { contentDescription = statsCd },
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(TmmIconId.BarChart, size = 16.dp, tint = TmmColors.textMuted)
                }
            }
            ChapterPresenceBars(onPhone = chapter.onPhone, onPc = chapter.onPc, onFr = chapter.onFr)
        }
        if (chapter.stats != null && statsExpanded) {
            ChapterStatsChips(stats = chapter.stats)
        }
    }
}

@Composable
private fun ChapterPresenceBars(onPhone: Boolean, onPc: Boolean, onFr: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PresenceBar(present = onPhone, color = TmmColors.info)
        PresenceBar(present = onPc, color = TmmColors.textMuted)
        PresenceBar(present = onFr, color = TmmColors.accent)
    }
}

@Composable
private fun PresenceBar(present: Boolean, color: Color) {
    if (present) {
        Box(
            modifier = Modifier
                .size(10.dp, 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    } else {
        Box(
            modifier = Modifier
                .size(10.dp, 22.dp)
                .dashedBorder(color = TmmColors.CB9B0A0, width = 1.dp, cornerRadius = 3.dp),
        )
    }
}

// FlowRow comme le flex-wrap de la maquette : en Row, les dernieres puces etaient ecrasees
// (« 12 » coupe sur deux lignes) des qu'un chapitre avait des bulles non traduites.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterStatsChips(stats: SeriesChapterStats) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val warnPages = stats.pagesTranslated < stats.pagesTotal
        StatChip(
            text = stringResource(R.string.series_chip_pages_fmt, stats.pagesTranslated, stats.pagesTotal),
            bg = if (warnPages) TmmColors.dangerBg else TmmColors.line,
            fg = if (warnPages) TmmColors.dangerText else TmmColors.ink,
        )
        StatChip(
            text = stringResource(R.string.series_chip_bubbles_translated_fmt, stats.bubblesTranslated),
            bg = TmmColors.line,
            fg = TmmColors.ink,
        )
        if (stats.bubblesUntranslated > 0) {
            StatChip(
                text = stringResource(R.string.series_chip_bubbles_untranslated_fmt, stats.bubblesUntranslated),
                bg = TmmColors.dangerBg,
                fg = TmmColors.dangerText,
            )
        }
        StatChip(
            text = stringResource(R.string.series_chip_sfx_fmt, stats.sfxKept),
            bg = TmmColors.line,
            fg = TmmColors.ink,
        )
        if (stats.verifiedDate != null) {
            StatChip(
                text = stringResource(R.string.series_chip_verified_fmt, stats.verifiedDate),
                bg = TmmColors.CE8F1EB,
                fg = TmmColors.success,
            )
        } else {
            StatChip(
                text = stringResource(R.string.series_chip_never_verified),
                bg = TmmColors.line,
                fg = TmmColors.textMuted,
            )
        }
    }
}

@Composable
private fun StatChip(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        TmmText(text = text, style = TmmType.mono(10), color = fg)
    }
}

@Composable
private fun SeriesSelectionActionBar(state: SeriesState, onEvent: (SeriesEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TmmColors.paperRaised)
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(0.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmText(
                text = pluralStringResource(R.plurals.series_chapters_selected, state.selectionCount, state.selectionCount),
                style = TmmType.sans(13, weight = 600),
            )
            TmmText(
                text = stringResource(R.string.series_deselect_all),
                style = TmmType.sans(13),
                color = TmmColors.textMuted,
                modifier = Modifier.clickable { onEvent(SeriesEvent.ClearSelection) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeriesActionButton(
                label = stringResource(R.string.library_action_send_label),
                count = state.selectionToSend,
                hint = stringResource(R.string.series_action_hint_send),
                icon = TmmIconId.Up,
                primary = true,
                onClick = { onEvent(SeriesEvent.SendToPc) },
                modifier = Modifier.weight(1f),
            )
            SeriesActionButton(
                label = stringResource(R.string.library_action_get_label),
                count = state.selectionToGet,
                hint = stringResource(R.string.series_action_hint_get),
                icon = TmmIconId.Down,
                primary = false,
                onClick = { onEvent(SeriesEvent.FetchToMihon) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeriesActionButton(
                label = stringResource(R.string.library_action_verify_label),
                count = state.selectionToVerify,
                hint = stringResource(R.string.series_action_hint_verify),
                icon = TmmIconId.ShieldCheck,
                primary = false,
                onClick = { onEvent(SeriesEvent.VerifyPending) },
                modifier = Modifier.weight(1f),
            )
            // "Retraduire" si une cible a deja une traduction, sinon "Traduire" (planTranslation).
            SeriesActionButton(
                label = stringResource(if (state.selectionRetranslates) R.string.library_action_retranslate_label else R.string.library_action_translate_label),
                count = state.selectionToTranslate,
                hint = stringResource(if (state.selectionRetranslates) R.string.series_action_hint_retranslate else R.string.series_action_hint_translate),
                icon = TmmIconId.Refresh,
                primary = false,
                onClick = { onEvent(SeriesEvent.RequestRetranslateSelection) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SeriesActionButton(
    label: String,
    count: Int,
    hint: String,
    icon: TmmIconId,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = count > 0
    val bg = if (primary && enabled) TmmColors.accent else TmmColors.surface
    val fg = if (primary && enabled) TmmColors.surface else TmmColors.ink
    val border = if (primary && enabled) TmmColors.accent else TmmColors.track
    val icBg = if (primary && enabled) TmmColors.White18 else TmmColors.paper
    val displayLabel = if (count > 0) "$label ($count)" else label
    val displayHint = if (count > 0) hint else stringResource(R.string.common_not_applicable)
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(icBg),
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(icon, size = 16.dp, tint = fg)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            TmmText(text = displayLabel, style = TmmType.sans(14, weight = 600), color = fg)
            TmmText(text = displayHint, style = TmmType.sans(11), color = fg.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SeriesSheetContent(state: SeriesState, onEvent: (SeriesEvent) -> Unit) {
    when (val sheet = state.sheet) {
        SeriesSheet.None -> {}
        SeriesSheet.SeriesActions -> {
            TmmSheet(
                visible = true,
                onDismiss = { onEvent(SeriesEvent.CloseSheet) },
                title = stringResource(R.string.series_sheet_title),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TmmColors.surface)
                        .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp)),
                ) {
                    SeriesSheetActionItem(
                        icon = TmmIconId.Up,
                        label = stringResource(R.string.series_sheet_action_send_label),
                        hint = stringResource(R.string.series_sheet_action_send_hint),
                        count = stringResource(R.string.series_sheet_count_fmt, 1),
                        icBg = TmmColors.dangerBg,
                        icFg = TmmColors.accent,
                        onClick = { onEvent(SeriesEvent.SeriesSendMissing) },
                    )
                    SeriesSheetActionItem(
                        icon = TmmIconId.Down,
                        label = stringResource(R.string.series_sheet_action_get_label),
                        hint = stringResource(R.string.series_sheet_action_get_hint),
                        count = stringResource(R.string.series_sheet_count_fmt, 1),
                        icBg = TmmColors.infoBg,
                        icFg = TmmColors.info,
                        onClick = { onEvent(SeriesEvent.SeriesFetchMissing) },
                    )
                    SeriesSheetActionItem(
                        icon = TmmIconId.ShieldCheck,
                        label = stringResource(R.string.series_sheet_action_verify_label),
                        hint = stringResource(R.string.series_sheet_action_verify_hint),
                        count = stringResource(R.string.series_sheet_count_fmt, state.frCount),
                        icBg = TmmColors.CE8F1EB,
                        icFg = TmmColors.success,
                        onClick = { onEvent(SeriesEvent.SeriesVerifyAll) },
                    )
                    SeriesSheetActionItem(
                        icon = TmmIconId.Refresh,
                        label = stringResource(R.string.series_sheet_action_retranslate_label),
                        hint = stringResource(R.string.series_sheet_action_retranslate_hint),
                        count = stringResource(R.string.series_sheet_count_fmt, state.frCount),
                        icBg = TmmColors.CECE7DC,
                        icFg = TmmColors.ink,
                        onClick = { onEvent(SeriesEvent.SeriesRequestRetranslateAll) },
                        isLast = true,
                    )
                }
            }
        }
        is SeriesSheet.Confirm -> {
            TmmSheet(
                visible = true,
                onDismiss = { onEvent(SeriesEvent.CloseSheet) },
                title = stringResource(R.string.library_confirm_title_fmt, pluralStringResource(R.plurals.series_chapters_count, sheet.chapterCount, sheet.chapterCount)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TmmText(
                        text = stringResource(R.string.series_confirm_text),
                        style = TmmType.sans(14, lineHeight = 21f),
                        color = TmmColors.textMuted,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TmmColors.infoBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        TmmIcon(TmmIconId.Info, size = 16.dp, tint = TmmColors.info)
                        TmmText(
                            text = stringResource(R.string.library_confirm_info),
                            style = TmmType.sans(12, lineHeight = 17f),
                            color = TmmColors.info,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TmmColors.surface)
                                .border(1.dp, TmmColors.track, RoundedCornerShape(12.dp))
                                .clickable { onEvent(SeriesEvent.CloseSheet) },
                            contentAlignment = Alignment.Center,
                        ) {
                            TmmText(text = stringResource(R.string.common_cancel), style = TmmType.sans(15, weight = 600), color = TmmColors.ink)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TmmColors.accent)
                                .clickable { onEvent(SeriesEvent.ConfirmRetranslate) },
                            contentAlignment = Alignment.Center,
                        ) {
                            TmmText(text = stringResource(R.string.library_action_retranslate_label), style = TmmType.sans(15, weight = 600), color = TmmColors.surface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesSheetActionItem(
    icon: TmmIconId,
    label: String,
    hint: String,
    count: String,
    icBg: Color,
    icFg: Color,
    onClick: () -> Unit,
    isLast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isLast) Modifier.drawBehindBottomLine(TmmColors.line) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(icBg),
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(icon, size = 18.dp, tint = icFg)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TmmText(text = label, style = TmmType.sans(15, weight = 600), color = TmmColors.ink)
            TmmText(text = hint, style = TmmType.sans(12), color = TmmColors.textMuted)
        }
        TmmText(text = count, style = TmmType.mono(12), color = TmmColors.textMuted)
    }
}

private fun Modifier.drawBehindBottomLine(color: Color): Modifier = this.then(
    Modifier.background(Color.Transparent) // placeholder if border is drawn
)

@Preview(showBackground = true)
@Composable
private fun SeriesScreenPreview() {
    TmmTheme {
        SeriesScreen(state = MockupData.seriesDetail, onEvent = {})
    }
}
