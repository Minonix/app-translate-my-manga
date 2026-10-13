package com.tmm.mobile.ui.screens.translationsettings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmSwitch
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

/**
 * Écran Réglages de traduction du PC (TranslationSettings.dc.html, E4.9).
 * Configuration distante des moteurs, langues, concurrence et réglages de vision avancée.
 */
@Composable
fun TranslationSettingsScreen(
    state: TranslationSettingsState,
    onEvent: (TranslationSettingsEvent) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val backCd = stringResource(R.string.trans_settings_back_cd)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = backCd,
                            onClick = onNavigateBack,
                        )
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TmmIcon(
                        icon = TmmIconId.ChevronLeft,
                        size = 22.dp,
                        tint = TmmColors.textMuted,
                        contentDescription = backCd,
                    )
                    TmmText(
                        text = stringResource(R.string.settings_title),
                        style = TmmType.sans(15),
                        color = TmmColors.textMuted,
                    )
                }
            }

            // Corps défilant
            val enabled = !state.isReadOnly
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 0.dp)
                    .alpha(if (enabled) 1f else 0.45f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Titre
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TmmText(
                        text = stringResource(R.string.settings_v2_translation),
                        style = TmmType.display(32, weight = 800),
                        color = TmmColors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }

                // Carte d'état de synchronisation avec le PC
                SyncStatusCard(status = state.syncStatus, isReadOnly = state.isReadOnly, pcName = state.pcName, rejectReason = state.rejectReason, overwroteKeys = state.overwroteKeys)

                // Groupe 1 : Langues
                SettingsGroup(title = stringResource(R.string.settings_v2_essential)) {
                    PickRow(
                        label = stringResource(R.string.settings_v2_source),
                        value = state.sourceLanguage.displaySummary,
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.SourceLanguage)) },
                        enabled = enabled,
                    )
                    PickRow(
                        label = stringResource(R.string.settings_v2_target),
                        value = state.targetLanguage.displaySummary,
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.TargetLanguage)) },
                        enabled = enabled,
                    )

                    PickRow(
                        label = stringResource(R.string.settings_v2_engine),
                        value = state.translationProvider.displaySummary,
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.TranslationProvider)) },
                        enabled = enabled,
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_v2_verify),
                        description = stringResource(R.string.settings_v2_verify_hint),
                        checked = state.autoVerifyAfterTranslate,
                        onCheckedChange = { onEvent(TranslationSettingsEvent.SetAutoVerify(it)) },
                        enabled = enabled,
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_v2_failover),
                        description = stringResource(R.string.settings_v2_failover_hint),
                        checked = state.autoFailoverToLocalLlm,
                        onCheckedChange = { onEvent(TranslationSettingsEvent.SetAutoFailover(it)) },
                        showDivider = false,
                        enabled = enabled,
                    )
                }

                // Groupe 4 : Performance
                CollapsibleGroup(stringResource(R.string.settings_v2_speed),
                    pluralStringResource(
                        if (state.useGpu) R.plurals.settings_v2_speed_hint else R.plurals.settings_v2_speed_hint_cpu,
                        state.chapterConcurrency,
                        state.chapterConcurrency,
                    ),
                    state.speedExpanded,
                    { onEvent(TranslationSettingsEvent.ToggleSpeed) }) {
                    SegmentedRow(
                        label = stringResource(R.string.settings_v2_chapters_at_once),
                        hint = "${state.chapterConcurrency}",
                        options = listOf(1, 2, 3, 4),
                        selected = state.chapterConcurrency,
                        onSelect = { onEvent(TranslationSettingsEvent.SetChapterConcurrency(it)) },
                        enabled = enabled,
                    )

                    val pageHint = if (state.concurrency == 2) {
                        stringResource(R.string.settings_v2_pages_hint_recommended_fmt, state.concurrency)
                    } else {
                        stringResource(R.string.settings_v2_pages_hint_fmt, state.concurrency)
                    }
                    // Maquette v2 : seule « Chapitres à la fois » reste en segmente, les deux autres
                    // sont des lignes a chevron qui ouvrent la feuille de choix.
                    PickRow(
                        label = stringResource(R.string.settings_v2_pages_at_once),
                        value = pageHint,
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.PagesAtOnce)) },
                        enabled = enabled,
                    )
                    PickRow(
                        label = stringResource(R.string.trans_settings_pile_size),
                        value = stringResource(R.string.settings_v2_pile_hint_fmt, state.pileSize),
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.PagesPerBatch)) },
                        enabled = enabled,
                    )

                    ToggleRow(
                        label = stringResource(R.string.settings_v2_gpu),
                        description = if (state.useGpu) {
                            stringResource(R.string.trans_settings_gpu_on_desc)
                        } else {
                            stringResource(R.string.trans_settings_gpu_off_desc)
                        },
                        checked = state.useGpu,
                        onCheckedChange = { onEvent(TranslationSettingsEvent.SetUseGpu(it)) },
                        showDivider = false,
                        enabled = enabled,
                    )
                }

                CollapsibleGroup(stringResource(R.string.settings_v2_rendering),
                    stringResource(R.string.settings_v2_rendering_hint_fmt, state.inpainter.label),
                    state.renderingExpanded || state.isAdvancedExpanded,
                    { onEvent(TranslationSettingsEvent.ToggleRendering) }) {
                        PickRow(
                            label = stringResource(R.string.trans_settings_inpainter),
                            value = state.inpainter.displaySummary,
                            onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.Inpainter)) },
                            enabled = enabled,
                        )
                        PickRow(
                            label = stringResource(R.string.trans_settings_inpainting_size),
                            value = state.inpaintingSize.displaySummary,
                            onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.InpaintingSize)) },
                            enabled = enabled,
                            showDivider = false,
                        )
                }
                CollapsibleGroup(stringResource(R.string.settings_v2_reading),
                    if (state.detector == BubbleDetector.Default && state.preferredOcr == PreferredOcr.Auto) {
                        stringResource(R.string.settings_v2_reading_hint_auto)
                    } else {
                        state.detector.label + " · " + state.preferredOcr.label
                    },
                    state.readingExpanded || state.isAdvancedExpanded,
                    { onEvent(TranslationSettingsEvent.ToggleReading) }) {
                        PickRow(
                            label = stringResource(R.string.trans_settings_detector),
                            value = state.detector.displaySummary,
                            onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.BubbleDetector)) },
                            enabled = enabled,
                        )
                        PickRow(
                            label = stringResource(R.string.settings_v2_ocr),
                            value = state.preferredOcr.displaySummary,
                            onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.PreferredOcr)) },
                            enabled = enabled,
                            showDivider = false,
                        )
                }

                // Note de bas de page : les cles API ne quittent pas le PC.
                TmmText(
                    text = stringResource(R.string.settings_v2_footer_note),
                    style = TmmType.sans(12, lineHeight = 18f),
                    color = TmmColors.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Box(modifier = Modifier.height(16.dp))
            }
        }

        // Bottom Sheet modal pour les sélections
        if (state.activeSheet != null) {
            // Fond d'assombrissement
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TmmColors.Ink45)
                    .clickable { onEvent(TranslationSettingsEvent.CloseSheet) },
            )

            // Feuille modale
            SettingsBottomSheet(
                activeSheet = state.activeSheet,
                state = state,
                onEvent = onEvent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SyncStatusCard(status: TranslationSyncStatus, isReadOnly: Boolean, pcName: String, @StringRes rejectReason: Int? = null, overwroteKeys: String = "") {
    val sending = status == TranslationSyncStatus.Sending
    val rejected = status == TranslationSyncStatus.Rejected && !isReadOnly
    val overwrote = status == TranslationSyncStatus.Overwrote && !isReadOnly
    val waiting = status == TranslationSyncStatus.WaitingPc || isReadOnly || rejected || overwrote
    // PLAN_GEMINI.md 5.18/7.9 : fond #8A5A00 (ajouté) pour WAITING_PC (timeout 60s ou lecture seule).
    val badgeBg = when {
        waiting -> TmmColors.C8A5A00
        sending -> TmmColors.C2B4C7E
        else -> TmmColors.C2F7D4F
    }
    val title = when {
        isReadOnly -> stringResource(R.string.trans_settings_sync_waiting_title)
        status == TranslationSyncStatus.Sending -> stringResource(R.string.trans_settings_sync_sending_title)
        status == TranslationSyncStatus.Applied -> stringResource(R.string.trans_settings_sync_applied_title_fmt, pcName)
        status == TranslationSyncStatus.WaitingPc -> stringResource(R.string.trans_settings_sync_waiting_title)
        rejected -> stringResource(R.string.trans_settings_sync_rejected_title)
        overwrote -> stringResource(R.string.trans_settings_sync_applied_title_fmt, pcName)
        else -> stringResource(R.string.trans_settings_sync_synced_title_fmt, pcName)
    }
    val hint = when {
        isReadOnly -> stringResource(R.string.trans_settings_sync_readonly_hint)
        sending -> stringResource(R.string.trans_settings_sync_sending_hint)
        status == TranslationSyncStatus.WaitingPc -> stringResource(R.string.trans_settings_sync_waiting_hint)
        rejected -> stringResource(rejectReason ?: R.string.error_unknown).replaceFirstChar { it.uppercase() }
        overwrote -> stringResource(R.string.settings_overwrote_pc_fmt, overwroteKeys)
        else -> stringResource(R.string.trans_settings_sync_synced_hint)
    }

    if (!waiting && !sending) {
        Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TmmIcon(icon = TmmIconId.CheckSimple, size = 18.dp, tint = TmmColors.C2F7D4F)
            TmmText(text = stringResource(R.string.settings_v2_applied, pcName), style = TmmType.sans(13, weight = 600), color = TmmColors.C2F7D4F)
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "spinAngle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (waiting) TmmColors.C8A5A00 else TmmColors.ink)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            if (sending) {
                TmmIcon(
                    icon = TmmIconId.SpinnerArc,
                    size = 18.dp,
                    tint = TmmColors.surface,
                    modifier = Modifier.rotate(spinAngle),
                )
            } else {
                TmmIcon(
                    icon = if (rejected) TmmIconId.Warning else TmmIconId.CheckSimple,
                    size = 18.dp,
                    tint = TmmColors.surface,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TmmText(
                text = title,
                style = TmmType.sans(14, weight = 600),
                color = TmmColors.paper,
            )
            TmmText(
                text = hint,
                style = TmmType.sans(12),
                color = TmmColors.CCFC8B8,
            )
        }
    }
}

@Composable
private fun CollapsibleGroup(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
            .background(TmmColors.surface)) {
            PickRow(label = title, value = summary, onClick = onToggle, showDivider = false)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp)).background(TmmColors.surface)) { content() }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TmmText(
            text = title.uppercase(),
            style = TmmType.sans(12, weight = 600, letterSpacingEm = 0.06f),
            color = TmmColors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                .background(TmmColors.surface),
        ) {
            content()
        }
    }
}

@Composable
private fun PickRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TmmText(
                    text = label,
                    style = TmmType.sans(14, weight = 500),
                    color = TmmColors.ink,
                )
                TmmText(
                    text = value,
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                TmmIcon(
                    icon = TmmIconId.ChevronRight,
                    size = 16.dp,
                    tint = TmmColors.C9C9486,
                )
            }
        }

        if (showDivider) {
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
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TmmText(
                    text = label,
                    style = TmmType.sans(14, weight = 500),
                    color = TmmColors.ink,
                )
                TmmText(
                    text = description,
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }

            TmmSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }

        if (showDivider) {
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
private fun SegmentedRow(
    label: String,
    hint: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmText(
                text = label,
                style = TmmType.sans(14, weight = 500),
                color = TmmColors.ink,
            )
            TmmText(
                text = hint,
                style = TmmType.sans(12),
                color = TmmColors.textMuted,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TmmColors.line)
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { opt ->
                val isOn = opt == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isOn) TmmColors.ink else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(
                            selected = isOn,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(opt) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = "$opt",
                        style = TmmType.mono(13, weight = 600),
                        color = if (isOn) TmmColors.paper else TmmColors.textMuted,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TmmColors.line),
        )
    }
}

private data class SheetOptionItem(
    val id: String,
    val label: String,
    val hint: String,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsBottomSheet(
    activeSheet: ActiveSheet,
    state: TranslationSettingsState,
    onEvent: (TranslationSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (activeSheet) {
        ActiveSheet.SourceLanguage -> stringResource(R.string.settings_v2_source)
        ActiveSheet.TargetLanguage -> stringResource(R.string.settings_v2_target)
        ActiveSheet.TranslationProvider -> stringResource(R.string.settings_v2_engine)
        ActiveSheet.BubbleDetector -> stringResource(R.string.trans_settings_detector)
        ActiveSheet.PreferredOcr -> stringResource(R.string.settings_v2_ocr)
        ActiveSheet.Inpainter -> stringResource(R.string.trans_settings_inpainter)
        ActiveSheet.InpaintingSize -> stringResource(R.string.trans_settings_inpainting_size)
        ActiveSheet.PagesAtOnce -> stringResource(R.string.settings_v2_pages_at_once)
        ActiveSheet.PagesPerBatch -> stringResource(R.string.trans_settings_pile_size)
    }
    val recommended = stringResource(R.string.settings_v2_recommended)

    val options: List<SheetOptionItem> = when (activeSheet) {
        ActiveSheet.SourceLanguage -> SourceLanguage.entries.map { lang ->
            SheetOptionItem(
                id = lang.code,
                label = lang.label,
                hint = lang.badge,
                isSelected = state.sourceLanguage == lang,
                onClick = { onEvent(TranslationSettingsEvent.SelectSourceLanguage(lang)) },
            )
        }
        ActiveSheet.TargetLanguage -> TargetLanguage.entries.map { lang ->
            SheetOptionItem(
                id = lang.code,
                label = lang.label,
                hint = "",
                isSelected = state.targetLanguage == lang,
                onClick = { onEvent(TranslationSettingsEvent.SelectTargetLanguage(lang)) },
            )
        }
        ActiveSheet.TranslationProvider -> TranslationProvider.entries.map { prov ->
            SheetOptionItem(
                id = prov.id,
                label = prov.label,
                hint = prov.badge,
                isSelected = state.translationProvider == prov,
                onClick = { onEvent(TranslationSettingsEvent.SelectTranslationProvider(prov)) },
            )
        }
        ActiveSheet.BubbleDetector -> BubbleDetector.entries.map { det ->
            SheetOptionItem(
                id = det.id,
                label = det.label,
                hint = det.badge,
                isSelected = state.detector == det,
                onClick = { onEvent(TranslationSettingsEvent.SelectDetector(det)) },
            )
        }
        ActiveSheet.PreferredOcr -> PreferredOcr.entries.map { ocr ->
            SheetOptionItem(
                id = ocr.id,
                label = ocr.label,
                hint = ocr.badge,
                isSelected = state.preferredOcr == ocr,
                onClick = { onEvent(TranslationSettingsEvent.SelectPreferredOcr(ocr)) },
            )
        }
        ActiveSheet.Inpainter -> Inpainter.entries.map { inp ->
            SheetOptionItem(
                id = inp.id,
                label = inp.label,
                hint = inp.badge,
                isSelected = state.inpainter == inp,
                onClick = { onEvent(TranslationSettingsEvent.SelectInpainter(inp)) },
            )
        }
        ActiveSheet.InpaintingSize -> InpaintingSize.entries.map { sz ->
            SheetOptionItem(
                id = "${sz.sizePx}",
                label = sz.label,
                hint = sz.badge,
                isSelected = state.inpaintingSize == sz,
                onClick = { onEvent(TranslationSettingsEvent.SelectInpaintingSize(sz)) },
            )
        }
        ActiveSheet.PagesAtOnce -> (1..4).map { n ->
            SheetOptionItem(
                id = "$n",
                label = stringResource(R.string.settings_v2_pages_hint_fmt, n),
                hint = if (n == 2) recommended else "",
                isSelected = state.concurrency == n,
                onClick = { onEvent(TranslationSettingsEvent.SetConcurrency(n)) },
            )
        }
        ActiveSheet.PagesPerBatch -> (1..3).map { n ->
            SheetOptionItem(
                id = "$n",
                label = "$n",
                hint = "",
                isSelected = state.pileSize == n,
                onClick = { onEvent(TranslationSettingsEvent.SetPileSize(n)) },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(TmmColors.paper)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Handle pill
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TmmColors.CD5CDBC),
        )

        TmmText(
            text = title,
            style = TmmType.display(22, weight = 800),
            color = TmmColors.ink,
            modifier = Modifier.semantics { heading() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                .background(TmmColors.surface)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton, onClick = option.onClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Radio ring
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (option.isSelected) 6.dp else 2.dp,
                                color = if (option.isSelected) TmmColors.accent else TmmColors.CB9B0A0,
                                shape = CircleShape,
                            ),
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        TmmText(
                            text = option.label,
                            style = TmmType.sans(14, weight = if (option.isSelected) 600 else 400),
                            color = TmmColors.ink,
                        )
                        if (option.hint.isNotEmpty()) {
                            TmmText(
                                text = option.hint.replaceFirstChar { it.titlecase() },
                                style = TmmType.sans(12),
                                color = TmmColors.textMuted,
                            )
                        }
                    }
                }

                if (index < options.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TmmColors.line),
                    )
                }
            }
        }
    }
}

@Preview(name = "TranslationSettings - Default", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TranslationSettingsDefaultPreview() {
    TmmTheme {
        TranslationSettingsScreen(
            state = MockupData.translationSettings,
            onEvent = {},
        )
    }
}

@Preview(name = "TranslationSettings - Advanced Open", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TranslationSettingsAdvancedPreview() {
    TmmTheme {
        TranslationSettingsScreen(
            state = MockupData.translationSettings.copy(isAdvancedExpanded = true),
            onEvent = {},
        )
    }
}

@Preview(name = "TranslationSettings - Sheet Open", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TranslationSettingsSheetPreview() {
    TmmTheme {
        TranslationSettingsScreen(
            state = MockupData.translationSettings.copy(activeSheet = ActiveSheet.TranslationProvider),
            onEvent = {},
        )
    }
}
