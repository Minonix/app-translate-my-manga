package com.tmm.mobile.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone

/**
 * Écran File (Queue.dc.html, E4.5).
 * Affiche l'état des traductions (Actives, Terminées, Échecs), la progression
 * des travaux en cours sur le PC distant, la liste d'attente et les alertes de relecture.
 */
@Composable
fun QueueScreen(
    state: QueueState,
    onEvent: (QueueEvent) -> Unit,
    onOpenNewTranslation: () -> Unit = {},
    onOpenTranslationSettings: () -> Unit = {},
    onOpenSeries: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        QueueHeader(
            state = state,
            onEvent = onEvent,
            onOpenNewTranslation = onOpenNewTranslation,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.activeTab) {
                QueueTab.Active -> {
                    QueueActiveTabContent(
                        state = state,
                        onEvent = onEvent,
                        onOpenTranslationSettings = onOpenTranslationSettings,
                    )
                }
                QueueTab.Done -> {
                    QueueDoneTabContent(
                        state = state,
                        onEvent = onEvent,
                        onOpenSeries = onOpenSeries,
                    )
                }
                QueueTab.Fail -> {
                    QueueFailTabContent(
                        state = state,
                        onEvent = onEvent,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QueueHeader(
    state: QueueState,
    onEvent: (QueueEvent) -> Unit,
    onOpenNewTranslation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TmmText(
                    text = stringResource(R.string.queue_title),
                    style = TmmType.display(36, weight = 800),
                    color = TmmColors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                TmmText(
                    text = stringResource(R.string.queue_subtitle),
                    style = TmmType.sans(13),
                    color = TmmColors.textMuted,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TmmColors.accent)
                    .clickable(
                        onClick = onOpenNewTranslation,
                        role = Role.Button,
                    )
                    .semantics {
                        contentDescription = "Nouvelle traduction"
                    },
                contentAlignment = Alignment.Center,
            ) {
                TmmIcon(
                    icon = TmmIconId.Plus,
                    tint = TmmColors.CFFFFFF,
                    size = 22.dp,
                )
            }
        }

        val tablistCd = stringResource(R.string.queue_tablist_cd)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TmmColors.CECE7DC)
                .padding(4.dp)
                .semantics { contentDescription = tablistCd }
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QueueTabButton(
                title = stringResource(R.string.queue_tab_active),
                count = state.activeCount,
                isSelected = state.activeTab == QueueTab.Active,
                badgeBg = if (state.activeTab == QueueTab.Active) TmmColors.C16161A else TmmColors.CD5CDBC,
                badgeFg = if (state.activeTab == QueueTab.Active) TmmColors.CFFFFFF else TmmColors.C16161A,
                onClick = { onEvent(QueueEvent.SelectTab(QueueTab.Active)) },
                modifier = Modifier.weight(1f),
            )
            QueueTabButton(
                title = stringResource(R.string.queue_tab_done),
                count = state.doneCount,
                isSelected = state.activeTab == QueueTab.Done,
                badgeBg = if (state.activeTab == QueueTab.Done) TmmColors.C16161A else TmmColors.CD5CDBC,
                badgeFg = if (state.activeTab == QueueTab.Done) TmmColors.CFFFFFF else TmmColors.C16161A,
                onClick = { onEvent(QueueEvent.SelectTab(QueueTab.Done)) },
                modifier = Modifier.weight(1f),
            )
            val hasFailures = state.failCount > 0
            QueueTabButton(
                title = stringResource(R.string.queue_tab_fail),
                count = state.failCount,
                isSelected = state.activeTab == QueueTab.Fail,
                badgeBg = if (hasFailures) TmmColors.CC8391F else if (state.activeTab == QueueTab.Fail) TmmColors.C16161A else TmmColors.CD5CDBC,
                badgeFg = if (hasFailures || state.activeTab == QueueTab.Fail) TmmColors.CFFFFFF else TmmColors.C16161A,
                onClick = { onEvent(QueueEvent.SelectTab(QueueTab.Fail)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QueueTabButton(
    title: String,
    count: Int,
    isSelected: Boolean,
    badgeBg: Color,
    badgeFg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) TmmColors.surface else Color.Transparent)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TmmText(
                text = title,
                style = TmmType.sans(13, weight = 600),
                color = if (isSelected) TmmColors.ink else TmmColors.textMuted,
            )
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 20.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                TmmText(
                    text = count.toString(),
                    style = TmmType.mono(11, weight = 500),
                    color = badgeFg,
                )
            }
        }
    }
}

@Composable
private fun QueueActiveTabContent(
    state: QueueState,
    onEvent: (QueueEvent) -> Unit,
    onOpenTranslationSettings: () -> Unit,
) {
    // Slots row
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(state.slotsCount) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(TmmColors.accent),
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmText(
                text = "${state.slotsCount}/${state.slotsMax}",
                style = TmmType.mono(12, weight = 600),
                color = TmmColors.ink,
            )
            TmmText(
                text = " " + stringResource(R.string.queue_parallel_on_pc_fmt, state.pcName),
                style = TmmType.sans(12),
                color = TmmColors.textMuted,
            )
        }
        TmmText(
            text = stringResource(R.string.queue_settings_action),
            style = TmmType.sans(12, weight = 600),
            color = TmmColors.accent,
            modifier = Modifier
                .clickable(onClick = onOpenTranslationSettings, role = Role.Button),
        )
    }

    // Active jobs dark card
    val focusedJob = state.activeJobs.getOrNull(state.focusedJobIndex) ?: state.activeJobs.firstOrNull()
    if (focusedJob != null) {
        val activeSectionCd = stringResource(R.string.queue_active_section_cd)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(TmmColors.C16161A)
                .padding(16.dp)
                .semantics { contentDescription = activeSectionCd },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Main focused item row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 60.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(focusedJob.color)
                        .halftone(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(TmmColors.accent),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(focusedJob.type.bg)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            TmmText(
                                text = focusedJob.type.label,
                                style = TmmType.sans(12, weight = 600),
                                color = focusedJob.type.fg,
                            )
                        }
                    }
                    TmmText(
                        text = focusedJob.title,
                        style = TmmType.sans(16, weight = 600),
                        color = TmmColors.CF4F1EA,
                    )
                    TmmText(
                        text = stringResource(
                            R.string.queue_page_detail_fmt,
                            focusedJob.currentPage,
                            focusedJob.totalPages,
                            focusedJob.extra,
                        ),
                        style = TmmType.mono(12),
                        color = TmmColors.textOnInkMuted,
                    )
                }
                TmmText(
                    text = "${focusedJob.progressPercent}%",
                    style = TmmType.display(28, weight = 800),
                    color = TmmColors.CF4F1EA,
                )
            }

            // Big progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TmmColors.C2E2D29),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (focusedJob.progressPercent / 100f).coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TmmColors.accent),
                )
            }

            // 5 steps
            val stepNames = listOf(
                stringResource(R.string.queue_step_fetch),
                stringResource(R.string.queue_step_ocr),
                stringResource(R.string.queue_step_translate),
                stringResource(R.string.queue_step_lettering),
                stringResource(R.string.queue_step_verify),
            )
            val stepsCd = stringResource(R.string.queue_steps_cd)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = stepsCd },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                stepNames.forEachIndexed { i, name ->
                    val isDone = i < focusedJob.currentStepIndex
                    val isCurrent = i == focusedJob.currentStepIndex
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isDone) TmmColors.CF4F1EA else if (isCurrent) TmmColors.accent else TmmColors.C3A3934,
                                    shape = CircleShape,
                                )
                                .background(if (isDone) TmmColors.CF4F1EA else if (isCurrent) TmmColors.accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDone) {
                                TmmIcon(
                                    icon = TmmIconId.CheckSimple,
                                    tint = TmmColors.C16161A,
                                    size = 12.dp,
                                )
                            }
                        }
                        TmmText(
                            text = name,
                            style = TmmType.sans(11, weight = if (isCurrent) 600 else 400),
                            color = if (i <= focusedJob.currentStepIndex) TmmColors.CF4F1EA else TmmColors.CA9A293,
                        )
                    }
                }
            }

            // Other active jobs
            val otherJobs = state.activeJobs.mapIndexed { idx, j -> idx to j }.filter { it.first != state.focusedJobIndex }
            if (otherJobs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TmmColors.C2E2D29),
                    )
                    otherJobs.forEach { (idx, job) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    onClick = { onEvent(QueueEvent.FocusJob(idx)) },
                                    role = Role.Button,
                                )
                                .semantics {
                                    contentDescription = "Voir le détail de ${job.title}"
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(job.color)
                                    .halftone(),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    TmmText(
                                        text = job.title,
                                        style = TmmType.sans(13, weight = 600),
                                        color = TmmColors.CF4F1EA,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(job.type.bg)
                                            .padding(horizontal = 5.dp),
                                    ) {
                                        TmmText(
                                            text = job.type.label,
                                            style = TmmType.sans(10, weight = 600),
                                            color = job.type.fg,
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(TmmColors.C2E2D29),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (job.progressPercent / 100f).coerceIn(0f, 1f))
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(TmmColors.CF4F1EA),
                                    )
                                }
                            }
                            TmmText(
                                text = "${job.progressPercent}%",
                                style = TmmType.mono(12),
                                color = TmmColors.textOnInkMuted,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(TmmColors.C2E2D29),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TmmText(
                            text = stringResource(R.string.queue_touch_hint),
                            style = TmmType.sans(12),
                            color = TmmColors.CA9A293,
                        )
                        TmmText(
                            text = stringResource(R.string.queue_auto_return),
                            style = TmmType.sans(12),
                            color = TmmColors.CA9A293,
                        )
                    }
                }
            }
        }
    }

    // Ensuite (waiting) section
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TmmText(
            text = stringResource(R.string.queue_waiting_title_fmt, state.waitingJobs.size),
            style = TmmType.display(19, weight = 800),
            color = TmmColors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp)),
        ) {
            if (state.waitingJobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = stringResource(R.string.queue_no_waiting),
                        style = TmmType.sans(13),
                        color = TmmColors.textMuted,
                    )
                }
            } else {
                state.waitingJobs.forEachIndexed { i, job ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TmmText(
                            text = job.position,
                            style = TmmType.mono(12),
                            color = TmmColors.C9C9486,
                            modifier = Modifier.width(22.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 44.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(job.color)
                                .halftone(),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            TmmText(
                                text = job.title,
                                style = TmmType.sans(14, weight = 600),
                                color = TmmColors.ink,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                TmmIcon(
                                    icon = if (job.isPhone) TmmIconId.PhoneOutline else TmmIconId.MonitorOutline,
                                    tint = TmmColors.textMuted,
                                    size = 12.dp,
                                )
                                TmmText(
                                    text = job.source,
                                    style = TmmType.sans(12),
                                    color = TmmColors.textMuted,
                                )
                            }
                        }
                        val removeCd = stringResource(R.string.queue_remove_cd_fmt, job.title)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClick = { onEvent(QueueEvent.RemoveWaitingJob(job.id)) },
                                    role = Role.Button,
                                )
                                .semantics { contentDescription = removeCd },
                            contentAlignment = Alignment.Center,
                        ) {
                            TmmIcon(
                                icon = TmmIconId.Close,
                                tint = TmmColors.textMuted,
                                size = 18.dp,
                            )
                        }
                    }
                    if (i < state.waitingJobs.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(TmmColors.CECE7DC),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueDoneTabContent(
    state: QueueState,
    onEvent: (QueueEvent) -> Unit,
    onOpenSeries: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TmmColors.surface)
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp)),
    ) {
        state.doneJobs.forEachIndexed { i, done ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenSeries),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 44.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(done.color)
                                .halftone(),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TmmText(
                                text = done.title,
                                style = TmmType.sans(14, weight = 600),
                                color = TmmColors.ink,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TmmText(
                                    text = "${done.whenText} · ",
                                    style = TmmType.sans(12),
                                    color = TmmColors.textMuted,
                                )
                                TmmText(
                                    text = done.stateText,
                                    style = TmmType.sans(12, weight = 600),
                                    color = done.stateColor,
                                )
                            }
                        }
                    }

                    if (done.alerts.isNotEmpty()) {
                        val alertCd = stringResource(R.string.queue_alert_cd)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TmmColors.accent)
                                .semantics { contentDescription = alertCd },
                        )
                    }

                    val detailsCd = stringResource(R.string.queue_done_details_cd_fmt, done.title)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (done.isExpanded) TmmColors.CECE7DC else Color.Transparent)
                            .clickable(
                                onClick = { onEvent(QueueEvent.ToggleDoneJobDetails(i)) },
                                role = Role.Button,
                            )
                            .semantics { contentDescription = detailsCd },
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmIcon(
                            icon = TmmIconId.BarChart,
                            tint = TmmColors.textMuted,
                            size = 16.dp,
                        )
                    }
                }

                if (done.isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 44.dp, end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Chips row
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val pagesWarn = done.translatedPages < done.totalPages
                            QueueChip(
                                text = stringResource(R.string.series_chip_pages_fmt, done.translatedPages, done.totalPages),
                                bg = if (pagesWarn) TmmColors.dangerBg else TmmColors.CECE7DC,
                                fg = if (pagesWarn) TmmColors.dangerText else TmmColors.C16161A,
                            )
                            QueueChip(
                                text = stringResource(R.string.series_chip_bubbles_translated_fmt, done.totalBubbles),
                                bg = TmmColors.CECE7DC,
                                fg = TmmColors.C16161A,
                            )
                            if (done.untranslatedBubbles > 0) {
                                QueueChip(
                                    text = stringResource(R.string.series_chip_bubbles_untranslated_fmt, done.untranslatedBubbles),
                                    bg = TmmColors.dangerBg,
                                    fg = TmmColors.dangerText,
                                )
                            }
                            QueueChip(
                                text = stringResource(R.string.series_chip_sfx_fmt, done.sfxCount),
                                bg = TmmColors.CECE7DC,
                                fg = TmmColors.C16161A,
                            )
                            if (done.alerts.isNotEmpty()) {
                                QueueChip(
                                    text = pluralStringResource(R.plurals.queue_alerts_count, done.alerts.size, done.alerts.size),
                                    bg = TmmColors.dangerBg,
                                    fg = TmmColors.dangerText,
                                )
                            } else {
                                QueueChip(
                                    text = stringResource(R.string.queue_no_alerts),
                                    bg = TmmColors.CE8F1EB,
                                    fg = TmmColors.success,
                                )
                            }
                        }

                        // Alerts cards
                        done.alerts.forEach { alert ->
                            val whyText = when (alert.code) {
                                "source_copy" -> stringResource(R.string.queue_alert_source_copy)
                                "source_text" -> stringResource(R.string.queue_alert_source_text)
                                "overflow" -> stringResource(R.string.queue_alert_overflow)
                                "too_small" -> stringResource(R.string.queue_alert_too_small)
                                else -> alert.code
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TmmColors.dangerBg)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TmmText(
                                        text = stringResource(R.string.queue_alert_row_fmt, alert.page, whyText),
                                        style = TmmType.sans(12),
                                        color = TmmColors.dangerText,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, TmmColors.dangerText, RoundedCornerShape(8.dp))
                                        .clickable(
                                            onClick = {
                                                if (!alert.isChecked) {
                                                    onEvent(QueueEvent.VerifyPage(i, alert.page))
                                                }
                                            },
                                            role = Role.Button,
                                        )
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TmmText(
                                        text = if (alert.isChecked) {
                                            stringResource(R.string.queue_in_queue_status)
                                        } else {
                                            stringResource(R.string.queue_verify_page_action)
                                        },
                                        style = TmmType.sans(12, weight = 600),
                                        color = TmmColors.dangerText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (i < state.doneJobs.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TmmColors.CECE7DC),
                )
            }
        }
    }
}

@Composable
private fun QueueChip(
    text: String,
    bg: Color,
    fg: Color,
) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        TmmText(
            text = text,
            style = TmmType.mono(10),
            color = fg,
        )
    }
}

@Composable
private fun QueueFailTabContent(
    state: QueueState,
    onEvent: (QueueEvent) -> Unit,
) {
    if (state.failedJobs.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.failedJobs.forEach { failed ->
                QueueFailedJobCard(failed = failed, onRetry = { onEvent(QueueEvent.RetryFailedJob(failed.jobId)) })
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TmmIcon(
                icon = TmmIconId.CheckCircle,
                tint = TmmColors.success,
                size = 36.dp,
            )
            TmmText(
                text = stringResource(R.string.queue_retried_title),
                style = TmmType.sans(15, weight = 600),
                color = TmmColors.success,
            )
            TmmText(
                text = stringResource(R.string.queue_no_failures),
                style = TmmType.sans(13),
                color = TmmColors.textMuted,
            )
        }
    }
}

@Composable
private fun QueueFailedJobCard(
    failed: FailedJob,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TmmColors.surface)
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
            .border(
                width = 4.dp,
                color = TmmColors.accent,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(failed.color)
                    .halftone(),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TmmText(
                    text = failed.title,
                    style = TmmType.sans(14, weight = 600),
                    color = TmmColors.ink,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TmmIcon(
                        icon = TmmIconId.Warning,
                        tint = TmmColors.dangerText,
                        size = 14.dp,
                    )
                    val page = failed.page
                    TmmText(
                        text = if (page != null) {
                            stringResource(R.string.queue_fail_message_fmt, page, failed.message)
                        } else {
                            stringResource(R.string.queue_fail_message_simple_fmt, failed.message)
                        },
                        style = TmmType.sans(12, weight = 600),
                        color = TmmColors.dangerText,
                    )
                }
                TmmText(
                    text = failed.keptInfo,
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.C16161A, RoundedCornerShape(10.dp))
                .clickable(
                    onClick = onRetry,
                    role = Role.Button,
                ),
            contentAlignment = Alignment.Center,
        ) {
            TmmText(
                text = failed.page?.let { stringResource(R.string.queue_retry_button_fmt, it) }
                    ?: stringResource(R.string.queue_retry_button_simple),
                style = TmmType.sans(14, weight = 600),
                color = TmmColors.C16161A,
            )
        }
    }
}

@Preview(name = "Queue Screen - Active", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun QueueScreenActivePreview() {
    TmmTheme {
        QueueScreen(
            state = MockupData.queue,
            onEvent = {},
        )
    }
}

@Preview(name = "Queue Screen - Done", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun QueueScreenDonePreview() {
    TmmTheme {
        QueueScreen(
            state = MockupData.queue.copy(activeTab = QueueTab.Done),
            onEvent = {},
        )
    }
}

@Preview(name = "Queue Screen - Fail", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun QueueScreenFailPreview() {
    TmmTheme {
        QueueScreen(
            state = MockupData.queue.copy(activeTab = QueueTab.Fail),
            onEvent = {},
        )
    }
}
