package com.tmm.mobile.ui.screens.newtranslation

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmCheckbox
import com.tmm.mobile.ui.components.TmmSearchField
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone

/**
 * Écran / Feuille modale Nouvelle Traduction (NewTranslation.dc.html, E4.7).
 * Étape 1 : choix de la série à envoyer parmi celles de Mihon.
 * Étape 2 : sélection des chapitres, option traduction auto et validation.
 */
@Composable
fun NewTranslationScreen(
    state: NewTranslationState,
    onEvent: (NewTranslationEvent) -> Unit,
    onClose: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        // Scrim background area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TmmColors.Ink45)
                .clickable(onClick = onClose),
        )

        // Modal Sheet container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(720.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(TmmColors.paper),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(TmmColors.track),
                )
            }

            // Sheet Header
            NewTranslationHeader(
                state = state,
                onEvent = onEvent,
                onClose = onClose,
            )

            // Step Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (!state.isStep2) {
                    NewTranslationStep1(state = state, onEvent = onEvent)
                } else {
                    NewTranslationStep2(state = state, onEvent = onEvent)
                }
            }

            // Bottom Action Bar
            NewTranslationBottomBar(
                state = state,
                onEvent = onEvent,
                onOpenQueue = onOpenQueue,
            )
        }
    }
}

@Composable
private fun NewTranslationHeader(
    state: NewTranslationState,
    onEvent: (NewTranslationEvent) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isStep2) {
            val backCd = stringResource(R.string.new_trans_change_series_cd)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClick = { onEvent(NewTranslationEvent.BackToSeries) },
                        role = Role.Button,
                    )
                    .semantics { contentDescription = backCd },
                contentAlignment = Alignment.Center,
            ) {
                TmmIcon(
                    icon = TmmIconId.ChevronLeft,
                    tint = TmmColors.ink,
                    size = 22.dp,
                    strokeWidth = 2.0f,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val stepLabel = if (state.isStep2) {
                stringResource(R.string.new_trans_step2_label)
            } else {
                stringResource(R.string.new_trans_step1_label)
            }
            TmmText(
                text = stepLabel.uppercase(),
                style = TmmType.sans(12, weight = 600),
                color = TmmColors.textMuted,
            )
            val title = if (state.isStep2) {
                state.selectedSeries?.title ?: ""
            } else {
                stringResource(R.string.new_trans_title)
            }
            TmmText(
                text = title,
                style = TmmType.display(24, weight = 800),
                color = TmmColors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }

        val closeCd = stringResource(R.string.new_trans_close_cd)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose, role = Role.Button)
                .semantics { contentDescription = closeCd },
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(
                icon = TmmIconId.Close,
                tint = TmmColors.ink,
                size = 20.dp,
                strokeWidth = 2.0f,
            )
        }
    }
}

@Composable
private fun NewTranslationStep1(
    state: NewTranslationState,
    onEvent: (NewTranslationEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Search Input
        TmmSearchField(
            value = state.searchQuery,
            onValueChange = { onEvent(NewTranslationEvent.UpdateQuery(it)) },
            placeholder = stringResource(R.string.new_trans_search_placeholder),
        )

        TmmText(
            text = stringResource(R.string.new_trans_series_subtitle),
            style = TmmType.sans(12),
            color = TmmColors.textMuted,
        )

        // Series List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.filteredSeries.forEach { series ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(14.dp))
                        .background(TmmColors.surface)
                        .clickable(
                            onClick = { onEvent(NewTranslationEvent.SelectSeries(series.seriesKey)) },
                            role = Role.Button,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 56.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(series.color)
                            .halftone(),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        TmmText(
                            text = series.title,
                            style = TmmType.sans(15, weight = 600),
                            color = TmmColors.ink,
                        )
                        TmmText(
                            text = series.subtitle,
                            style = TmmType.sans(12),
                            color = TmmColors.textMuted,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(TmmColors.dangerBg)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmText(
                            text = stringResource(R.string.new_trans_to_send_badge_fmt, series.chapters.size),
                            style = TmmType.sans(12, weight = 600),
                            color = TmmColors.dangerText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewTranslationStep2(
    state: NewTranslationState,
    onEvent: (NewTranslationEvent) -> Unit,
) {
    val series = state.selectedSeries ?: return
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmText(
                text = stringResource(R.string.new_trans_chapters_subtitle),
                style = TmmType.sans(13),
                color = TmmColors.textMuted,
            )
            val allLabel = if (state.isAllChaptersSelected) {
                stringResource(R.string.new_trans_uncheck_all)
            } else {
                stringResource(R.string.new_trans_check_all)
            }
            TmmText(
                text = allLabel,
                style = TmmType.sans(13, weight = 600),
                color = TmmColors.accent,
                modifier = Modifier
                    .clickable(onClick = { onEvent(NewTranslationEvent.ToggleAllChapters) }, role = Role.Button)
                    .padding(vertical = 4.dp),
            )
        }

        // Chapters list card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                .background(TmmColors.surface)
                .verticalScroll(rememberScrollState()),
        ) {
            series.chapters.forEachIndexed { i, chapter ->
                val isChecked = state.selectedChapters.contains(chapter.mihonPath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 56.dp)
                        .background(if (isChecked) TmmColors.paperRaised else TmmColors.surface)
                        .clickable(onClick = { onEvent(NewTranslationEvent.ToggleChapter(chapter.mihonPath)) })
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TmmCheckbox(
                        checked = isChecked,
                        onCheckedChange = { onEvent(NewTranslationEvent.ToggleChapter(chapter.mihonPath)) },
                    )
                    TmmText(
                        text = stringResource(R.string.new_trans_chapter_item_fmt, chapter.numberLabel),
                        style = TmmType.mono(14),
                        color = TmmColors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    TmmText(
                        text = stringResource(R.string.new_trans_pages_count_fmt, chapter.pages),
                        style = TmmType.sans(12),
                        color = TmmColors.textMuted,
                    )
                }
                if (i < series.chapters.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TmmColors.CECE7DC),
                    )
                }
            }
        }

        // Translate on arrival row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { onEvent(NewTranslationEvent.ToggleTranslateOnArrival) })
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TmmCheckbox(
                checked = state.translateOnArrival,
                onCheckedChange = { onEvent(NewTranslationEvent.ToggleTranslateOnArrival) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TmmText(
                    text = stringResource(R.string.new_trans_translate_on_arrival_title),
                    style = TmmType.sans(14, weight = 500),
                    color = TmmColors.ink,
                )
                val hint = if (state.translateOnArrival) {
                    stringResource(R.string.new_trans_translate_hint_on)
                } else {
                    stringResource(R.string.new_trans_translate_hint_off)
                }
                TmmText(
                    text = hint,
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun NewTranslationBottomBar(
    state: NewTranslationState,
    onEvent: (NewTranslationEvent) -> Unit,
    onOpenQueue: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = TmmColors.lineStrong)
            .background(TmmColors.paperRaised)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        if (state.phase == NewTranslationPhase.Done) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TmmColors.success)
                    .clickable(onClick = onOpenQueue, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TmmIcon(
                        icon = TmmIconId.CheckSimple,
                        tint = TmmColors.CFFFFFF,
                        size = 18.dp,
                        strokeWidth = 2.6f,
                    )
                    TmmText(
                        text = stringResource(R.string.new_trans_btn_done),
                        style = TmmType.sans(16, weight = 600),
                        color = TmmColors.CFFFFFF,
                    )
                }
            }
        } else {
            val isEnabled = state.isStep2 && state.selectedCount > 0 && state.phase == NewTranslationPhase.Idle
            val btnBg = if (isEnabled) TmmColors.accent else TmmColors.lineStrong
            val btnFg = if (isEnabled) TmmColors.CFFFFFF else TmmColors.textMuted
            val label = when {
                state.phase == NewTranslationPhase.Busy -> stringResource(R.string.new_trans_btn_busy)
                !state.isStep2 -> stringResource(R.string.new_trans_btn_pick_series)
                state.selectedCount == 0 -> stringResource(R.string.new_trans_btn_check_chapters)
                state.selectedCount == 1 -> stringResource(R.string.new_trans_btn_send_single)
                else -> stringResource(R.string.new_trans_btn_send_multi_fmt, state.selectedCount)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(btnBg)
                    .clickable(
                        enabled = isEnabled,
                        onClick = { onEvent(NewTranslationEvent.Send) },
                        role = Role.Button,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TmmText(
                    text = label,
                    style = TmmType.sans(16, weight = 600),
                    color = btnFg,
                )
            }
        }
    }
}

@Preview(name = "New Translation - Step 1", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NewTranslationStep1Preview() {
    TmmTheme {
        NewTranslationScreen(
            state = MockupData.newTranslation,
            onEvent = {},
        )
    }
}

@Preview(name = "New Translation - Step 2", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NewTranslationStep2Preview() {
    TmmTheme {
        val series = MockupData.newTranslation.seriesList.first()
        NewTranslationScreen(
            state = MockupData.newTranslation.copy(
                selectedSeriesKey = series.seriesKey,
                selectedChapters = series.chapters.map { it.mihonPath }.toSet(),
            ),
            onEvent = {},
        )
    }
}
