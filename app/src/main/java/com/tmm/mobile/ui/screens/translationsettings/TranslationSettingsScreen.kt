package com.tmm.mobile.ui.screens.translationsettings

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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = backCd,
                            onClick = onNavigateBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(
                        icon = TmmIconId.ChevronLeft,
                        size = 22.dp,
                        tint = TmmColors.ink,
                        contentDescription = backCd,
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
                        text = stringResource(R.string.trans_settings_title),
                        style = TmmType.display(32, weight = 800),
                        color = TmmColors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    TmmText(
                        text = stringResource(R.string.trans_settings_subtitle),
                        style = TmmType.sans(13),
                        color = TmmColors.textMuted,
                    )
                }

                // Carte d'état de synchronisation avec le PC
                SyncStatusCard(status = state.syncStatus, isReadOnly = state.isReadOnly, pcName = state.pcName)

                // Groupe 1 : Langues
                SettingsGroup(title = stringResource(R.string.trans_settings_group_languages)) {
                    PickRow(
                        label = stringResource(R.string.trans_settings_source_language),
                        value = state.sourceLanguage.displaySummary,
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.SourceLanguage)) },
                        enabled = enabled,
                    )
                    PickRow(
                        label = stringResource(R.string.trans_settings_target_language),
                        value = state.targetLanguage.displaySummary,
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.TargetLanguage)) },
                        showDivider = false,
                        enabled = enabled,
                    )
                }

                // Groupe 2 : Moteur
                SettingsGroup(title = stringResource(R.string.trans_settings_group_engine)) {
                    PickRow(
                        label = stringResource(R.string.trans_settings_engine),
                        value = state.translationProvider.displaySummary,
                        onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.TranslationProvider)) },
                        enabled = enabled,
                    )
                    ToggleRow(
                        label = stringResource(R.string.trans_settings_auto_failover),
                        description = stringResource(R.string.trans_settings_auto_failover_desc),
                        checked = state.autoFailoverToLocalLlm,
                        onCheckedChange = { onEvent(TranslationSettingsEvent.SetAutoFailover(it)) },
                        showDivider = false,
                        enabled = enabled,
                    )
                }

                // Groupe 3 : Après traduction
                SettingsGroup(title = stringResource(R.string.trans_settings_group_after_translation)) {
                    ToggleRow(
                        label = stringResource(R.string.trans_settings_auto_verify),
                        description = stringResource(R.string.trans_settings_auto_verify_desc),
                        checked = state.autoVerifyAfterTranslate,
                        onCheckedChange = { onEvent(TranslationSettingsEvent.SetAutoVerify(it)) },
                        showDivider = false,
                        enabled = enabled,
                    )
                }

                // Groupe 4 : Performance
                SettingsGroup(title = stringResource(R.string.trans_settings_group_performance)) {
                    val chapterHint = if (state.chapterConcurrency == 1) {
                        stringResource(R.string.trans_settings_single_chapter)
                    } else {
                        stringResource(R.string.trans_settings_parallel_chapters_fmt, state.chapterConcurrency)
                    }
                    SegmentedRow(
                        label = stringResource(R.string.trans_settings_chapter_concurrency),
                        hint = chapterHint,
                        options = listOf(1, 2, 3, 4),
                        selected = state.chapterConcurrency,
                        onSelect = { onEvent(TranslationSettingsEvent.SetChapterConcurrency(it)) },
                        enabled = enabled,
                    )

                    val pageHint = if (state.concurrency == 2) {
                        stringResource(R.string.trans_settings_recommended)
                    } else {
                        stringResource(R.string.trans_settings_per_chapter)
                    }
                    SegmentedRow(
                        label = stringResource(R.string.trans_settings_page_concurrency),
                        hint = pageHint,
                        options = listOf(1, 2, 3, 4),
                        selected = state.concurrency,
                        onSelect = { onEvent(TranslationSettingsEvent.SetConcurrency(it)) },
                        enabled = enabled,
                    )

                    val pileHint = if (state.pileSize == 3) {
                        stringResource(R.string.trans_settings_recommended)
                    } else {
                        stringResource(R.string.trans_settings_more_context)
                    }
                    SegmentedRow(
                        label = stringResource(R.string.trans_settings_pile_size),
                        hint = pileHint,
                        options = listOf(1, 2, 3),
                        selected = state.pileSize,
                        onSelect = { onEvent(TranslationSettingsEvent.SetPileSize(it)) },
                        enabled = enabled,
                    )

                    ToggleRow(
                        label = stringResource(R.string.trans_settings_use_gpu),
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

                // Bouton vision avancée
                val advLabel = if (state.isAdvancedExpanded) {
                    stringResource(R.string.trans_settings_adv_collapse)
                } else {
                    stringResource(R.string.trans_settings_adv_expand)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .dashedBorder(color = TmmColors.CD5CDBC, width = 1.dp, cornerRadius = 12.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = advLabel,
                            onClick = { onEvent(TranslationSettingsEvent.ToggleAdvanced) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TmmText(
                            text = advLabel,
                            style = TmmType.sans(14, weight = 600),
                            color = TmmColors.ink,
                        )
                        TmmIcon(
                            icon = TmmIconId.ChevronDown,
                            size = 14.dp,
                            tint = TmmColors.ink,
                            modifier = Modifier.rotate(if (state.isAdvancedExpanded) 180f else 0f),
                        )
                    }
                }

                // Section vision avancée
                AnimatedVisibility(visible = state.isAdvancedExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                            .background(TmmColors.surface),
                    ) {
                        PickRow(
                            label = stringResource(R.string.trans_settings_detector),
                            value = state.detector.displaySummary,
                            onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.BubbleDetector)) },
                            enabled = enabled,
                        )
                        PickRow(
                            label = stringResource(R.string.trans_settings_ocr),
                            value = state.preferredOcr.displaySummary,
                            onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.PreferredOcr)) },
                            enabled = enabled,
                        )
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
                        )
                        PickRow(
                            label = stringResource(R.string.trans_settings_font),
                            value = state.font.displaySummary,
                            onClick = { onEvent(TranslationSettingsEvent.OpenSheet(ActiveSheet.Font)) },
                            enabled = enabled,
                            showDivider = false,
                        )
                    }
                }

                // Note d'information de sécurité
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TmmColors.line)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TmmIcon(
                        icon = TmmIconId.Lock,
                        size = 16.dp,
                        tint = TmmColors.textMuted,
                    )
                    TmmText(
                        text = stringResource(R.string.trans_settings_security_note),
                        style = TmmType.sans(12, lineHeight = 18f),
                        color = TmmColors.textMuted,
                    )
                }

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
private fun SyncStatusCard(status: TranslationSyncStatus, isReadOnly: Boolean, pcName: String) {
    val sending = status == TranslationSyncStatus.Sending
    val waiting = status == TranslationSyncStatus.WaitingPc || isReadOnly
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
        else -> stringResource(R.string.trans_settings_sync_synced_title_fmt, pcName)
    }
    val hint = when {
        isReadOnly -> stringResource(R.string.trans_settings_sync_readonly_hint)
        sending -> stringResource(R.string.trans_settings_sync_sending_hint)
        status == TranslationSyncStatus.WaitingPc -> stringResource(R.string.trans_settings_sync_waiting_hint)
        else -> stringResource(R.string.trans_settings_sync_synced_hint)
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
            .background(TmmColors.ink)
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
                    icon = TmmIconId.CheckSimple,
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
        ActiveSheet.SourceLanguage -> stringResource(R.string.trans_settings_source_language)
        ActiveSheet.TargetLanguage -> stringResource(R.string.trans_settings_target_language)
        ActiveSheet.TranslationProvider -> stringResource(R.string.trans_settings_engine)
        ActiveSheet.BubbleDetector -> stringResource(R.string.trans_settings_detector)
        ActiveSheet.PreferredOcr -> stringResource(R.string.trans_settings_ocr)
        ActiveSheet.Inpainter -> stringResource(R.string.trans_settings_inpainter)
        ActiveSheet.InpaintingSize -> stringResource(R.string.trans_settings_inpainting_size)
        ActiveSheet.Font -> stringResource(R.string.trans_settings_font)
    }

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
        ActiveSheet.Font -> TranslationFont.entries.map { fnt ->
            SheetOptionItem(
                id = fnt.fontName,
                label = fnt.label,
                hint = "",
                isSelected = state.font == fnt,
                onClick = { onEvent(TranslationSettingsEvent.SelectFont(fnt)) },
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
                                text = option.hint,
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
