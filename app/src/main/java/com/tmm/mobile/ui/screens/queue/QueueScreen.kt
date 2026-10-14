package com.tmm.mobile.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.data.status.ErrorAction
import com.tmm.mobile.ui.components.Cover
import com.tmm.mobile.ui.components.PillTone
import com.tmm.mobile.ui.components.StatusPill
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.V2Button
import com.tmm.mobile.ui.components.V2ButtonSize
import com.tmm.mobile.ui.components.V2ButtonStyle
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.components.px
import com.tmm.mobile.ui.components.v2Card
import com.tmm.mobile.ui.components.v2CardItem
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

private val C = TmmColors.V2

/**
 * Écran Activité (maquette v2, écran 7) : fusion de File et Transferts. Un chapitre suit un seul
 * chemin, Envoi, Traduction, Retour dans Mihon ; les transferts sont des lignes de « En cours ».
 */
@Composable
fun QueueScreen(
    state: QueueState,
    onEvent: (QueueEvent) -> Unit,
    onOpenNewTranslation: () -> Unit = {},
    onOpenTranslationSettings: () -> Unit = {},
    // Actions d'erreur qui menent hors de l'ecran (Permission, Reglages) ; les relances passent par onEvent.
    onErrorAction: (ErrorAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Liste paresseuse : « Ensuite », « Terminées » et « Échecs » peuvent compter des centaines de
    // jobs, tous composes d'un coup dans une Column a chaque changement d'onglet.
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(C.bg),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
    ) {
        item(key = "top", contentType = "top") {
            Column {
                Header(onOpenNewTranslation)
                Segment(state, onEvent)
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TmmText(
                        text = pluralStringResource(R.plurals.activity_slots_fmt, state.slotsMax, state.pcName, state.slotsMax),
                        style = TmmType.sans(13),
                        color = C.muted,
                        modifier = Modifier.weight(1f),
                    )
                    TmmText(
                        text = stringResource(R.string.activity_settings_link),
                        style = TmmType.sans(14, weight = 600),
                        color = C.red,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onOpenTranslationSettings),
                    )
                }
            }
        }
        when (state.activeTab) {
            QueueTab.Active -> activeItems(state, onEvent)
            QueueTab.Done -> doneItems(state, onEvent)
            QueueTab.Fail -> failItems(state, onEvent, onErrorAction)
        }
    }
}

@Composable
private fun Header(onOpenNewTranslation: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            TmmText(
                text = stringResource(R.string.activity_title),
                style = TmmType.display(40, weight = 800, letterSpacingEm = -1f / 40f).px(40f, lineHeight = 1.02f),
                color = C.ink,
                modifier = Modifier.semantics { heading() },
            )
            TmmText(
                text = stringResource(R.string.activity_subtitle),
                style = TmmType.sans(14).px(14f, lineHeight = 1.4f),
                color = C.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val cd = stringResource(R.string.activity_new_cd)
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(C.red)
                .clickable(role = Role.Button, onClick = onOpenNewTranslation)
                .semantics { contentDescription = cd },
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(TmmIconId.V2Plus, size = 22.dp, tint = C.white)
        }
    }
}

/** `.seg` : trois onglets avec leur compteur, celui des échecs en rouge. */
@Composable
private fun Segment(state: QueueState, onEvent: (QueueEvent) -> Unit) {
    val tablistCd = stringResource(R.string.activity_tablist_cd)
    Row(
        Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.chip)
            .padding(4.dp)
            .semantics { contentDescription = tablistCd }
            .selectableGroup(),
    ) {
        SegButton(stringResource(R.string.activity_tab_active), state.activeCount, red = false, on = state.activeTab == QueueTab.Active) {
            onEvent(QueueEvent.SelectTab(QueueTab.Active))
        }
        SegButton(stringResource(R.string.activity_tab_done), state.doneCount, red = false, on = state.activeTab == QueueTab.Done) {
            onEvent(QueueEvent.SelectTab(QueueTab.Done))
        }
        SegButton(stringResource(R.string.activity_tab_fail), state.failCount, red = state.failCount > 0, on = state.activeTab == QueueTab.Fail) {
            onEvent(QueueEvent.SelectTab(QueueTab.Fail))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SegButton(label: String, count: Int, red: Boolean, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .weight(1f)
            .height(44.dp)
            .then(if (on) Modifier.shadow(1.dp, shape) else Modifier)
            .clip(shape)
            .background(if (on) C.white else Color.Transparent)
            .selectable(selected = on, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmText(text = label, style = TmmType.sans(14, weight = 600).px(14.5f), color = if (on) C.ink else C.muted, maxLines = 1)
        val (bg, fg) = when {
            on -> C.ink to C.white
            red -> C.red to C.white
            else -> C.sand to C.ink
        }
        Box(
            Modifier
                .heightIn(min = 22.dp)
                .widthIn(min = 22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(bg)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            TmmText(text = count.toString(), style = TmmType.sans(12, weight = 600), color = fg)
        }
    }
}

// ---------------------------------------------------------------- En cours

private fun LazyListScope.activeItems(state: QueueState, onEvent: (QueueEvent) -> Unit) {
    // Jobs actifs et transferts : bornes par les emplacements du PC et les workers, une seule carte.
    item(key = "running", contentType = "running") { Column { RunningCards(state, onEvent) } }
    if (state.waitingJobs.isNotEmpty()) {
        item(key = "nextTitle", contentType = "title") {
            SectionTitle(
                text = stringResource(R.string.activity_next_fmt, state.waitingJobs.size),
                action = stringResource(R.string.activity_cancel_all_waiting),
                onAction = { onEvent(QueueEvent.CancelAllWaiting) },
            )
        }
        val waiting = state.waitingJobs
        items(waiting.size, key = { "w:" + waiting[it].id }, contentType = { "waiting" }) { i ->
            Box(Modifier.fillMaxWidth().v2CardItem(isFirst = i == 0, isLast = i == waiting.lastIndex)) {
                WaitingRow(waiting[i], divider = i > 0, onEvent)
            }
        }
    }
}

@Composable
private fun RunningCards(state: QueueState, onEvent: (QueueEvent) -> Unit) {
    val focused = state.focused
    if (focused != null) FocusedCard(focused)
    val others = state.activeJobs.withIndex().filter { it.index != state.focusedJobIndex }
    if (others.isNotEmpty() || state.transfers.isNotEmpty()) {
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().v2Card()) {
            var first = true
            others.forEach { (index, job) ->
                ListRow(divider = !first) {
                    val cd = stringResource(R.string.activity_focus_cd_fmt, job.title)
                    ProgressRow(
                        title = job.title,
                        pill = {
                            StatusPill(PillTone.Busy, stringResource(R.string.activity_row_progress_fmt, typeLabel(job.type), job.percent))
                        },
                        percent = job.percent,
                        barColor = if (job.type == QueueJobType.Translation) C.red else C.ink,
                        modifier = Modifier
                            .clickable(role = Role.Button) { onEvent(QueueEvent.FocusJob(index)) }
                            .semantics { contentDescription = cd },
                    )
                }
                first = false
            }
            state.transfers.forEach { t ->
                ListRow(divider = !first) {
                    ProgressRow(
                        title = t.title,
                        pill = {
                            if (t.isUpload) {
                                StatusPill(PillTone.Wait, stringResource(R.string.activity_upload_fmt, t.percent), icon = TmmIconId.V2Up)
                            } else {
                                val label = if (t.totalBytes > 0) {
                                    stringResource(R.string.activity_download_fmt, mo(t.doneBytes), mo(t.totalBytes))
                                } else {
                                    stringResource(R.string.activity_download)
                                }
                                StatusPill(PillTone.Get, label, icon = TmmIconId.V2Down)
                            }
                        },
                        percent = t.percent,
                        barColor = if (t.isUpload) C.muted else C.blue,
                    )
                }
                first = false
            }
        }
    }
    if (state.uploadCount > 0) {
        TmmText(
            text = stringResource(R.string.activity_cancel_uploads_fmt, state.uploadCount),
            style = TmmType.sans(14, weight = 600),
            color = C.red,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clickable(role = Role.Button) { onEvent(QueueEvent.CancelAllUploads) }
                .padding(vertical = 8.dp),
        )
    }
    if (focused == null && state.transfers.isEmpty() && state.waitingJobs.isEmpty()) {
        EmptyCard(stringResource(R.string.activity_nothing_running))
    }
}

/** `.dark` : le chapitre suivi de près, ses 3 phases et ses étapes PC. */
@Composable
private fun FocusedCard(job: ActiveJob) {
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(C.ink)
            .padding(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Cover(job.seriesKey, job.coverUrl, Modifier.size(44.dp, 60.dp), color = job.color)
            Column(Modifier.weight(1f)) {
                TmmText(text = job.title, style = TmmType.sans(16, weight = 600), color = C.white)
                val detail = if (job.engine.isNotEmpty()) {
                    stringResource(R.string.activity_page_detail_fmt, job.currentPage, job.totalPages, job.engine)
                } else {
                    stringResource(R.string.activity_page_detail_short_fmt, job.currentPage, job.totalPages)
                }
                TmmText(text = detail, style = TmmType.mono(12).px(12.5f), color = C.onInkMuted, modifier = Modifier.padding(top = 4.dp))
            }
            TmmText(
                text = stringResource(R.string.activity_percent_fmt, job.percent),
                style = TmmType.display(30, weight = 800),
                color = C.white,
            )
        }
        Bar(job.percent, C.red, track = C.darkTrack, height = 8, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val shape = RoundedCornerShape(10.dp)
            val cell = Modifier.weight(1f)
            PhaseCell(stringResource(R.string.activity_phase_upload_done), C.greenLight, cell.clip(shape).background(C.ink2))
            PhaseCell(typeLabel(job.type), C.white, cell.clip(shape).background(C.red), bold = true)
            PhaseCell(
                stringResource(R.string.activity_phase_return),
                C.muted2,
                cell.dashedBorder(C.darkSwitch, width = 1.dp, cornerRadius = 10.dp),
            )
        }
        val steps = listOf(
            stringResource(R.string.activity_step_read),
            stringResource(R.string.activity_step_translate),
            stringResource(R.string.activity_step_lettering),
            stringResource(R.string.activity_step_verify),
        )
        val doneFmt = stringResource(R.string.activity_step_done_fmt)
        val currentFmt = stringResource(R.string.activity_step_current_fmt)
        val line = steps.mapIndexed { i, s ->
            when {
                i < job.currentStepIndex -> doneFmt.format(s)
                i == job.currentStepIndex -> currentFmt.format(s)
                else -> s
            }
        }.joinToString(" · ")
        TmmText(
            text = withSystemCheck(line),
            style = TmmType.sans(12),
            color = C.muted2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
        )
    }
}

// Le glyphe ✓ d'IBM Plex Sans ressemble a une racine carree (√) ; la maquette, servie par Google Fonts
// sans ce caractere, affiche la coche de la police systeme. On fait de meme ici.
private fun withSystemCheck(text: String): AnnotatedString = buildAnnotatedString {
    text.split('✓').forEachIndexed { i, part ->
        if (i > 0) withStyle(SpanStyle(fontFamily = FontFamily.Default)) { append('✓') }
        append(part)
    }
}

@Composable
private fun PhaseCell(label: String, color: Color, modifier: Modifier, bold: Boolean = false) {
    Box(modifier.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        TmmText(
            text = withSystemCheck(label),
            style = TmmType.sans(12, weight = if (bold) 600 else 400).px(12.5f),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProgressRow(
    title: String,
    pill: @Composable () -> Unit,
    percent: Int,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TmmText(
                text = title,
                style = TmmType.sans(15, weight = 600).px(15.5f),
                color = C.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            pill()
        }
        Bar(percent, barColor)
    }
}

@Composable
private fun WaitingRow(job: WaitingJob, divider: Boolean, onEvent: (QueueEvent) -> Unit) {
    ListRow(divider) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(job.seriesKey, job.coverUrl, Modifier.size(34.dp, 46.dp).clip(RoundedCornerShape(6.dp)), color = job.color)
            Column(Modifier.weight(1f)) {
                RowTitle(job.title)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    val label = when (job.type) {
                        QueueJobType.Translation -> stringResource(R.string.activity_waiting_translation)
                        QueueJobType.Verification -> stringResource(R.string.activity_waiting_verification)
                        QueueJobType.Retranslation -> stringResource(R.string.activity_waiting_retranslation)
                    }
                    RowDesc(if (job.auto) "$label · " else label)
                    if (job.auto) {
                        TmmIcon(TmmIconId.V2Bolt, size = 13.dp, tint = C.muted, modifier = Modifier.padding(end = 3.dp))
                        RowDesc(stringResource(R.string.activity_auto))
                    }
                }
            }
            val cd = stringResource(R.string.activity_remove_cd_fmt, job.title)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button) { onEvent(QueueEvent.RemoveWaitingJob(job.id)) }
                    .semantics { contentDescription = cd },
                contentAlignment = Alignment.Center,
            ) {
                TmmIcon(TmmIconId.V2X, size = 22.dp, tint = C.muted2)
            }
        }
    }
}

// ---------------------------------------------------------------- Terminées

private fun LazyListScope.doneItems(state: QueueState, onEvent: (QueueEvent) -> Unit) {
    val done = state.doneJobs
    if (done.isEmpty()) {
        item(key = "doneEmpty", contentType = "empty") { EmptyCard(stringResource(R.string.activity_done_empty)) }
        return
    }
    if (done.any { it.installed }) item(key = "doneClear", contentType = "action") { ClearAction(onEvent, QueueEvent.ClearDone) }
    items(done.size, key = { "d:" + done[it].mihonPath }, contentType = { "done" }) { i ->
        Box(
            Modifier
                .padding(top = if (i == 0) 12.dp else 0.dp)
                .fillMaxWidth()
                .v2CardItem(isFirst = i == 0, isLast = i == done.lastIndex),
        ) {
            DoneRow(i, done[i], onEvent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneRow(index: Int, done: DoneJob, onEvent: (QueueEvent) -> Unit) {
    ListRow(divider = index > 0) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val cd = stringResource(R.string.activity_done_cd_fmt, done.title)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onEvent(QueueEvent.ToggleDoneJobDetails(index)) }
                    .semantics { contentDescription = cd },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Cover(done.seriesKey, done.coverUrl, Modifier.size(34.dp, 46.dp).clip(RoundedCornerShape(6.dp)), color = done.color)
                Column(Modifier.weight(1f)) {
                    RowTitle(done.title)
                    RowDesc(
                        stringResource(if (done.installed) R.string.activity_done_installed_fmt else R.string.activity_done_ready_fmt, done.whenText),
                        Modifier.padding(top = 2.dp),
                    )
                }
                if (done.alerts.isNotEmpty()) {
                    StatusPill(PillTone.Todo, pluralStringResource(R.plurals.activity_alerts, done.alerts.size, done.alerts.size))
                } else {
                    StatusPill(PillTone.Ok, stringResource(R.string.activity_no_alert))
                }
            }
            if (done.isExpanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(PillTone.Wait, stringResource(R.string.activity_pages_fmt, done.translatedPages, done.totalPages), plain = true)
                    done.totalBubbles?.let { n ->
                        StatusPill(PillTone.Wait, pluralStringResource(R.plurals.activity_bubbles, n, n), plain = true)
                        if (done.untranslatedBubbles > 0) {
                            StatusPill(PillTone.Todo, pluralStringResource(R.plurals.activity_bubbles_vo, done.untranslatedBubbles, done.untranslatedBubbles), plain = true)
                        }
                    }
                    if (done.sfxCount > 0) StatusPill(PillTone.Wait, stringResource(R.string.activity_sfx_fmt, done.sfxCount), plain = true)
                }
                if (done.alerts.isNotEmpty()) AlertBox(index, done.alerts, onEvent)
            }
        }
    }
}

@Composable
private fun AlertBox(doneIndex: Int, alerts: List<DoneJobAlert>, onEvent: (QueueEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.white)
            .border(1.dp, C.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        alerts.forEachIndexed { i, alert ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line2))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val why = when (alert.code) {
                    "source_copy" -> stringResource(R.string.activity_alert_source_copy)
                    "source_text" -> stringResource(R.string.activity_alert_source_text)
                    "overflow" -> stringResource(R.string.activity_alert_overflow)
                    "too_small" -> stringResource(R.string.activity_alert_too_small)
                    else -> alert.code
                }
                val page = stringResource(R.string.activity_alert_page_fmt, alert.page)
                TmmText(
                    text = buildAnnotatedString {
                        withStyle(TmmType.mono(14, weight = 700).toSpanStyle().copy(fontWeight = FontWeight.Bold)) { append(page) }
                        append(" · ")
                        append(why)
                    },
                    style = TmmType.sans(14),
                    color = C.ink,
                    modifier = Modifier.weight(1f),
                )
                if (alert.isChecked) {
                    StatusPill(PillTone.Busy, stringResource(R.string.activity_fixing))
                } else {
                    V2Button(
                        label = stringResource(R.string.activity_fix_page),
                        onClick = { onEvent(QueueEvent.VerifyPage(doneIndex, alert.page)) },
                        style = V2ButtonStyle.Ghost,
                        size = V2ButtonSize.Small,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Échecs

private fun LazyListScope.failItems(state: QueueState, onEvent: (QueueEvent) -> Unit, onErrorAction: (ErrorAction) -> Unit) {
    val failed = state.failedJobs
    if (failed.isEmpty()) {
        item(key = "failEmpty", contentType = "empty") { EmptyCard(stringResource(R.string.activity_fail_empty)) }
        return
    }
    item(key = "failClear", contentType = "action") { ClearAction(onEvent, QueueEvent.ClearFailed) }
    items(failed.size, key = { "f:" + failed[it].jobId }, contentType = { "fail" }) { i -> FailCard(failed[i], onEvent, onErrorAction) }
    if (failed.any { it.quota }) item(key = "quotaTip", contentType = "tip") { QuotaTip() }
}

@Composable
private fun QuotaTip() {
    TmmText(
        text = stringResource(R.string.activity_quota_tip),
        style = TmmType.sans(13).px(13f, lineHeight = 1.45f),
        color = C.muted,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
    )
}

@Composable
private fun FailCard(failed: FailedJob, onEvent: (QueueEvent) -> Unit, onErrorAction: (ErrorAction) -> Unit) {
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .v2Card(borderColor = C.toastLink)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(failed.seriesKey, failed.coverUrl, Modifier.size(34.dp, 46.dp).clip(RoundedCornerShape(6.dp)), color = failed.color)
            Column(Modifier.weight(1f)) {
                RowTitle(failed.title)
                val page = failed.page
                val label = stringResource(failed.label)
                TmmText(
                    text = if (page != null) {
                        stringResource(R.string.activity_fail_page_fmt, page, label)
                    } else {
                        stringResource(R.string.activity_fail_fmt, label)
                    },
                    style = TmmType.sans(13).px(13.5f),
                    color = C.redDark,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // docs/ERREURS_PROTOCOLE.md 1.3 : le message brut n'est qu'un detail secondaire.
                failed.detail?.let { detail ->
                    TmmText(
                        text = detail,
                        style = TmmType.sans(12),
                        color = C.muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        TmmText(text = failed.keptInfo, style = TmmType.sans(13), color = C.muted, modifier = Modifier.padding(top = 10.dp))
        val page = failed.page
        val retry = { onEvent(QueueEvent.RetryFailedJob(failed.jobId)) }
        val (buttonLabel, onClick) = when (failed.action) {
            ErrorAction.RETRY -> (if (failed.resumeAtPage && page != null) stringResource(R.string.activity_resume_page_fmt, page) else stringResource(R.string.activity_resume)) to retry
            ErrorAction.RESEND -> stringResource(R.string.activity_action_resend) to retry
            ErrorAction.REDELIVER -> stringResource(R.string.activity_action_redeliver) to retry
            ErrorAction.REPAIR -> stringResource(R.string.activity_action_repair) to { onErrorAction(ErrorAction.REPAIR) }
            ErrorAction.RECONNECT_DRIVE -> stringResource(R.string.offline_btn_reconnect) to { onErrorAction(ErrorAction.RECONNECT_DRIVE) }
            ErrorAction.OPEN_PERMISSION -> stringResource(R.string.activity_action_open_permission) to { onErrorAction(ErrorAction.OPEN_PERMISSION) }
            // Sans remede dedie (doublon, modele absent, source illisible) : on peut toujours reessayer
            // une fois la cause levee, sinon l'echec reste bloque a l'ecran.
            ErrorAction.NONE -> stringResource(R.string.activity_retry) to retry
            ErrorAction.UPDATE_PC, ErrorAction.UPDATE_APP -> null to null
        }
        if (buttonLabel != null && onClick != null) {
            V2Button(
                label = buttonLabel,
                onClick = onClick,
                style = V2ButtonStyle.Ink,
                size = V2ButtonSize.Block,
                modifier = Modifier.padding(top = 12.dp).height(48.dp),
            )
        }
        // Mise a jour a faire hors de l'app : consigne a la place du bouton.
        val hint = when (failed.action) {
            ErrorAction.UPDATE_PC -> R.string.activity_action_update_pc
            ErrorAction.UPDATE_APP -> R.string.activity_action_update_app
            else -> null
        }
        hint?.let { TmmText(text = stringResource(it), style = TmmType.sans(13), color = C.ink, modifier = Modifier.padding(top = 12.dp)) }
    }
}

// ---------------------------------------------------------------- Briques

@Composable
private fun ListRow(divider: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line2))
        content()
    }
}

@Composable
private fun RowTitle(text: String) {
    TmmText(text = text, style = TmmType.sans(15, weight = 600).px(15.5f), color = C.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RowDesc(text: String, modifier: Modifier = Modifier) {
    TmmText(text = text, style = TmmType.sans(13).px(13.5f, 1.35f), color = C.muted, modifier = modifier)
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.padding(top = 26.dp, bottom = 12.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        TmmText(
            text = text,
            style = TmmType.display(22, weight = 800, letterSpacingEm = -0.3f / 22f),
            color = C.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (action != null) {
            TmmText(
                text = action,
                style = TmmType.sans(14, weight = 600),
                color = C.red,
                modifier = Modifier.clickable(role = Role.Button, onClick = onAction),
            )
        }
    }
}

/** « Vider » en tete d'onglet : meme style que l'action de SectionTitle (« Tout annuler »), aligne a droite. */
@Composable
private fun ClearAction(onEvent: (QueueEvent) -> Unit, event: QueueEvent) {
    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TmmText(
            text = stringResource(R.string.activity_clear),
            style = TmmType.sans(14, weight = 600),
            color = C.red,
            modifier = Modifier.clickable(role = Role.Button, onClick = { onEvent(event) }),
        )
    }
}

@Composable
private fun EmptyCard(text: String) {
    TmmText(
        text = text,
        style = TmmType.sans(14),
        color = C.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().v2Card().padding(horizontal = 16.dp, vertical = 22.dp),
    )
}

/** `.bar` : 6 dp par défaut, rail line2 (ou #34332E sur fond sombre). */
@Composable
private fun Bar(percent: Int, color: Color, modifier: Modifier = Modifier, track: Color = C.line2, height: Int = 6) {
    val shape = RoundedCornerShape((height / 2f).dp)
    Box(modifier.fillMaxWidth().height(height.dp).clip(shape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(percent.coerceIn(0, 100) / 100f).clip(shape).background(color))
    }
}

@Composable
private fun typeLabel(type: QueueJobType): String = when (type) {
    QueueJobType.Translation -> stringResource(R.string.activity_type_translation)
    QueueJobType.Verification -> stringResource(R.string.activity_type_verification)
    QueueJobType.Retranslation -> stringResource(R.string.activity_type_retranslation)
}

/** Octets en Mo arrondis, comme « Retour 3/4 Mo ». */
private fun mo(bytes: Long): Int = Math.round(bytes / 1_048_576.0).toInt()

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun QueueScreenActivePreview() {
    TmmTheme { QueueScreen(state = MockupData.queue, onEvent = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun QueueScreenDonePreview() {
    TmmTheme { QueueScreen(state = MockupData.queue.copy(activeTab = QueueTab.Done), onEvent = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun QueueScreenFailPreview() {
    TmmTheme { QueueScreen(state = MockupData.queue.copy(activeTab = QueueTab.Fail), onEvent = {}) }
}
