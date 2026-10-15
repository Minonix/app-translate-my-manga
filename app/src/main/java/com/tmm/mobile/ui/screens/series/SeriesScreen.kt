package com.tmm.mobile.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.AutoTag
import com.tmm.mobile.ui.components.Cover
import com.tmm.mobile.ui.components.PillTone
import com.tmm.mobile.ui.components.PullToRefresh
import com.tmm.mobile.ui.components.StatusPill
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.V2ActionRow
import com.tmm.mobile.ui.components.V2Button
import com.tmm.mobile.ui.components.V2ButtonSize
import com.tmm.mobile.ui.components.V2ButtonStyle
import com.tmm.mobile.ui.components.V2Check
import com.tmm.mobile.ui.components.V2IconButton
import com.tmm.mobile.ui.components.V2Sheet
import com.tmm.mobile.ui.components.V2SheetHeader
import com.tmm.mobile.ui.components.V2SelectionBar
import com.tmm.mobile.ui.components.V2Switch
import com.tmm.mobile.ui.components.V2Toast
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
 * Fiche serie (maquette v2, ecrans 3 a 5) : progression, suivi automatique, gros bouton Traduire,
 * liste a une etiquette par chapitre, feuilles « Que faire ? », « Suivre » et « Supprimer ».
 */
@Composable
fun SeriesScreen(
    state: SeriesState,
    onEvent: (SeriesEvent) -> Unit,
    onBack: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRefresh: suspend () -> Unit = {},
) {
    PullToRefresh(onRefresh, modifier.fillMaxSize().background(C.bg)) {
        Box(Modifier.fillMaxSize()) {
            // Liste paresseuse : une serie longue a plus de 1000 chapitres, tous composes d'un coup
            // dans une Column a chaque ouverture de la fiche.
            val listState = rememberLazyListState()
            LazyColumn(
                Modifier.fillMaxSize().dragToSelect(listState, state.rows, onEvent),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
            ) {
                item(key = "top", contentType = "top") {
                    Column {
                        TopRow(showMore = !state.selecting, onBack = onBack, onMore = { onEvent(SeriesEvent.OpenSeriesMore) })
                        if (state.selecting) {
                            TmmText(
                                text = state.title,
                                style = TmmType.display(28, weight = 800, letterSpacingEm = -0.3f / 28f),
                                color = C.ink,
                                modifier = Modifier.padding(top = 18.dp),
                            )
                        } else {
                            Header(state)
                            ProgressCard(state.progress)
                            FollowCard(state, onEvent)
                            if (state.sentOnFollow.isNotEmpty()) SentOnFollowCard(state.sentOnFollow, onOpenQueue)
                            if (state.toTranslateNumbers.isNotEmpty()) {
                                V2Button(
                                    label = translateLabel(state.toTranslateNumbers),
                                    onClick = { onEvent(SeriesEvent.TranslateAll) },
                                    modifier = Modifier.padding(top = 14.dp),
                                    size = V2ButtonSize.Block,
                                    icon = TmmIconId.V2Translate,
                                )
                            }
                            // « Sélectionner » seul, petit lien rouge, passait inapercu : traduire, retraduire ou
                            // verifier certains chapitres passe par ce bouton puis « Que faire ? ».
                            V2Button(
                                label = stringResource(R.string.series_choose_chapters),
                                onClick = { onEvent(SeriesEvent.StartSelection) },
                                modifier = Modifier.padding(top = 10.dp),
                                style = V2ButtonStyle.Ghost,
                                size = V2ButtonSize.Block,
                            )
                            if (state.showGap && state.missing.isNotEmpty()) GapBanner(state.missing) { onEvent(SeriesEvent.DismissGap) }
                        }
                        SectionHeader(
                            link = stringResource(if (state.selecting) R.string.series_select_done else R.string.series_select),
                            onLink = { onEvent(if (state.selecting) SeriesEvent.EndSelection else SeriesEvent.StartSelection) },
                        )
                    }
                }
                chapterItems(state, onEvent)
            }

            if (state.selecting && state.selectionCount > 0) {
                V2SelectionBar(
                    label = pluralStringResource(R.plurals.series_chapters_selected, state.selectionCount, state.selectionCount),
                    actionLabel = stringResource(R.string.series_selection_bar_action),
                    onAction = { onEvent(SeriesEvent.OpenSelectionMore) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }

            val sheet = state.sheet
            V2Sheet(visible = sheet is SeriesSheet.More, onDismiss = { onEvent(SeriesEvent.CloseSheet) }) {
                if (sheet is SeriesSheet.More) MoreSheetContent(state, sheet.allSeries, onEvent)
            }
            V2Sheet(visible = sheet is SeriesSheet.Follow, onDismiss = { onEvent(SeriesEvent.CloseSheet) }) {
                if (sheet is SeriesSheet.Follow) FollowSheetContent(state.title, sheet.numbers, onEvent)
            }
            V2Sheet(visible = sheet is SeriesSheet.Delete, onDismiss = { onEvent(SeriesEvent.CloseSheet) }) {
                if (sheet is SeriesSheet.Delete) {
                    DeleteSheetContent(
                        sheet = sheet,
                        onToggle = { onEvent(SeriesEvent.ToggleDeleteTarget(it)) },
                        onCancel = { onEvent(SeriesEvent.CloseSheet) },
                        onConfirm = { onEvent(SeriesEvent.ConfirmDelete) },
                    )
                }
            }

            SeriesToastHost(
                toast = state.toast,
                title = state.title,
                onEvent = onEvent,
                onOpenQueue = onOpenQueue,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun TopRow(showMore: Boolean, onBack: () -> Unit, onMore: () -> Unit) {
    val backCd = stringResource(R.string.series_back_cd)
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backCd },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmIcon(TmmIconId.V2Back, size = 22.dp, tint = C.muted)
            TmmText(text = stringResource(R.string.series_back_label), style = TmmType.sans(15), color = C.muted)
        }
        if (showMore) V2IconButton(TmmIconId.V2More, stringResource(R.string.series_more_cd), onMore)
    }
}

@Composable
private fun Header(state: SeriesState) {
    Row(
        Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Cover(
            seriesKey = state.title,
            coverUrl = state.coverUrl,
            color = state.coverColor,
            modifier = Modifier.size(96.dp, 136.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f)) {
            if (state.followed) {
                AutoTag(stringResource(R.string.series_followed_tag))
            } else if (state.categoryLabel.isNotEmpty()) {
                StatusPill(PillTone.Wait, state.categoryLabel, plain = true)
            }
            TmmText(
                text = state.title,
                style = TmmType.display(32, weight = 800, letterSpacingEm = -0.6f / 32f).px(32f, lineHeight = 1.05f),
                color = C.ink,
                modifier = Modifier.padding(top = 8.dp),
            )
            TmmText(
                text = state.subtitle,
                style = TmmType.sans(14).px(14f, lineHeight = 1.4f),
                color = C.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressCard(progress: SeriesProgress) {
    Column(Modifier.padding(top = 18.dp).fillMaxWidth().v2Card().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TmmText(
                text = pluralStringResource(R.plurals.series_progress_title, progress.inMihon, progress.inMihon, progress.total),
                style = TmmType.sans(15, weight = 600).px(15.5f),
                color = C.ink,
                modifier = Modifier.weight(1f),
            )
            TmmText(text = stringResource(R.string.series_progress_percent_fmt, progress.percent), style = TmmType.mono(13), color = C.muted)
        }
        val segments = listOf(
            progress.inMihon to C.green,
            progress.ready to C.blue,
            progress.busy to C.ink,
            progress.toTranslate to C.red,
            progress.notDownloaded to C.line2,
        ).filter { it.first > 0 }
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.line2),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segments.forEach { (count, color) -> Box(Modifier.weight(count.toFloat()).height(10.dp).background(color)) }
        }
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendItem(C.green, stringResource(R.string.series_legend_in_mihon_fmt, progress.inMihon))
            if (progress.ready > 0) LegendItem(C.blue, stringResource(R.string.series_legend_ready_fmt, progress.ready))
            if (progress.busy > 0) LegendItem(C.ink, stringResource(R.string.series_legend_busy_fmt, progress.busy))
            if (progress.toTranslate > 0) LegendItem(C.red, stringResource(R.string.series_legend_to_translate_fmt, progress.toTranslate))
            if (progress.notDownloaded > 0) {
                LegendItem(C.line, pluralStringResource(R.plurals.series_legend_not_downloaded, progress.notDownloaded, progress.notDownloaded))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        TmmText(text = label, style = TmmType.sans(12).px(12.5f), color = C.muted)
    }
}

/** `.card.follow` : claire et eteinte, puis noire (`.follow.on`) une fois la serie suivie. */
@Composable
private fun FollowCard(state: SeriesState, onEvent: (SeriesEvent) -> Unit) {
    val on = state.followed
    val modifier = if (on) {
        Modifier.clip(RoundedCornerShape(24.dp)).background(C.ink)
    } else {
        Modifier.v2Card()
    }
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().then(modifier).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.ink2 else C.chip),
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(TmmIconId.V2Bolt, size = 22.dp, tint = if (on) C.greenLight else C.ink)
        }
        Column(Modifier.weight(1f)) {
            TmmText(
                text = stringResource(if (on) R.string.series_followed_title else R.string.series_follow_title),
                style = TmmType.sans(16, weight = 600),
                color = if (on) C.white else C.ink,
            )
            TmmText(
                text = if (on) {
                    stringResource(R.string.series_followed_desc_fmt, state.nextExpected ?: 1)
                } else {
                    stringResource(R.string.series_follow_desc)
                },
                style = TmmType.sans(13).px(13.5f, lineHeight = 1.4f),
                color = if (on) C.onInkMuted else C.muted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        V2Switch(
            checked = on,
            onCheckedChange = { onEvent(SeriesEvent.SetFollow(it)) },
            onColor = C.green,
            offColor = C.sand,
            contentDescription = stringResource(R.string.series_follow_title),
        )
    }
}

@Composable
private fun SentOnFollowCard(numbers: List<Int>, onOpenQueue: () -> Unit) {
    val joined = numbers.joinToString(", ")
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().v2Card().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmIcon(TmmIconId.V2Check, size = 22.dp, tint = C.green)
        TmmText(
            text = stringResource(
                if (numbers.size == 1) R.string.series_sent_on_follow_one_fmt else R.string.series_sent_on_follow_many_fmt,
                joined,
            ),
            style = TmmType.sans(14),
            color = C.ink,
            modifier = Modifier.weight(1f),
        )
        TmmText(
            text = stringResource(R.string.series_sent_on_follow_see),
            style = TmmType.sans(14, weight = 600),
            color = C.red,
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenQueue),
        )
    }
}

@Composable
private fun GapBanner(missing: List<Int>, onDismiss: () -> Unit) {
    val joined = missing.joinToString(", ")
    val one = missing.size == 1
    val bold = stringResource(if (one) R.string.series_gap_bold_one_fmt else R.string.series_gap_bold_many_fmt, joined)
    val hint = stringResource(if (one) R.string.series_gap_hint_one else R.string.series_gap_hint_many)
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .v2Card(background = C.amberSoft, borderColor = C.amberLine)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmIcon(TmmIconId.V2Warn, size = 22.dp, tint = C.amber)
        TmmText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
                append(" ")
                append(hint)
            },
            style = TmmType.sans(13).px(13.5f, lineHeight = 1.4f),
            color = C.ink,
            modifier = Modifier.weight(1f),
        )
        V2Button(
            label = stringResource(R.string.series_gap_dismiss),
            onClick = onDismiss,
            style = V2ButtonStyle.Ghost,
            size = V2ButtonSize.Small,
        )
    }
}

@Composable
private fun SectionHeader(link: String, onLink: () -> Unit) {
    Row(
        Modifier.padding(top = 26.dp, bottom = 12.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        TmmText(
            text = stringResource(R.string.series_chapters_title),
            style = TmmType.display(22, weight = 800, letterSpacingEm = -0.3f / 22f),
            color = C.ink,
        )
        TmmText(
            text = link,
            style = TmmType.sans(14, weight = 600),
            color = C.red,
            modifier = Modifier.clickable(role = Role.Button, onClick = onLink),
        )
    }
}

/**
 * Appui long sur un chapitre puis glissement vers le haut ou le bas : coche chaque chapitre
 * survole. Pres des bords, la liste defile seule pour continuer la selection.
 */
private fun Modifier.dragToSelect(
    listState: LazyListState,
    rows: List<SeriesRow>,
    onEvent: (SeriesEvent) -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val currentRows by rememberUpdatedState(rows)
    val send by rememberUpdatedState(onEvent)
    var pointerY by remember { mutableFloatStateOf(-1f) }
    var speed by remember { mutableFloatStateOf(0f) }

    // Cle de la ligne sous le doigt. L'element 0 est l'en-tete « top », les chapitres suivent.
    fun keyAt(y: Float): String? {
        val info = listState.layoutInfo
        // Les decalages des elements partent du haut du contenu, apres la marge interne.
        val inList = y.toInt() + info.viewportStartOffset
        val item = info.visibleItemsInfo.firstOrNull { inList in it.offset until it.offset + it.size }
            ?: return null
        return currentRows.getOrNull(item.index - 1)?.key
    }

    LaunchedEffect(listState) {
        snapshotFlow { speed != 0f }.collectLatest { scrolling ->
            while (scrolling) {
                withFrameNanos { }
                listState.scrollBy(speed)
                keyAt(pointerY)?.let { send(SeriesEvent.DragSelectTo(it)) }
            }
        }
    }

    pointerInput(listState) {
        val edge = 64.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Un defilement ou un relachement avant le delai annule : la liste et le tap restent normaux.
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val key = keyAt(longPress.position.y) ?: return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            pointerY = longPress.position.y
            send(SeriesEvent.DragSelectStart(key))
            try {
                while (true) {
                    // Passe Initial : la liste ne defile pas et la ligne ne recoit pas de tap pendant le geste.
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    pointerY = change.position.y
                    val height = size.height.toFloat()
                    speed = when {
                        pointerY < edge -> -(edge - pointerY) / 4f
                        pointerY > height - edge -> (pointerY - (height - edge)) / 4f
                        else -> 0f
                    }
                    keyAt(pointerY)?.let { send(SeriesEvent.DragSelectTo(it)) }
                }
            } finally {
                speed = 0f
                pointerY = -1f
                send(SeriesEvent.DragSelectEnd)
            }
        }
    }
}

// Pas de cle LazyColumn : la position suffit, l'etat (selection par SeriesRow.key) vit dans le ViewModel.
private fun LazyListScope.chapterItems(state: SeriesState, onEvent: (SeriesEvent) -> Unit) {
    val rows = state.rows
    items(rows.size, contentType = { "chapter" }) { index ->
        val row = rows[index]
        Column(Modifier.fillMaxWidth().v2CardItem(isFirst = index == 0, isLast = index == rows.lastIndex)) {
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line2))
            ChapterRow(
                row = row,
                selecting = state.selecting,
                selected = row.key in state.selected,
                expanded = row.key in state.expandedStats,
                onEvent = onEvent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterRow(
    row: SeriesRow,
    selecting: Boolean,
    selected: Boolean,
    expanded: Boolean,
    onEvent: (SeriesEvent) -> Unit,
) {
    val selectable = !row.locked && row.kind != ChapterKind.NotDownloaded
    val hasStats = row.stats != null && row.kind == ChapterKind.InMihon
    val statsCd = stringResource(R.string.series_chapter_stats_cd_fmt, row.label)
    val click: Modifier = when {
        selecting && selectable -> Modifier.clickable(role = Role.Checkbox) { onEvent(SeriesEvent.ToggleChapter(row.key)) }
        !selecting && hasStats -> Modifier
            .clickable(role = Role.Button) { onEvent(SeriesEvent.ToggleStats(row.key)) }
            .semantics { contentDescription = statsCd }
        else -> Modifier
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(click)
            .alpha(if (selecting && !selectable) 0.5f else 1f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) V2Check(checked = selected, enabled = selectable)
            val dimmed = row.kind == ChapterKind.Missing || row.kind == ChapterKind.NotDownloaded
            TmmText(
                text = if (row.number < 0) {
                    stringResource(R.string.series_chapter_unnumbered)
                } else {
                    val number = stringResource(R.string.series_chapter_number_fmt, row.label)
                    if (row.duplicate) "$number · ${row.edition ?: stringResource(R.string.translate_unknown_scanlator)}" else number
                },
                style = TmmType.mono(15),
                color = if (dimmed) C.muted else C.ink,
                modifier = Modifier.weight(1f),
            )
            val (tone, label) = statusOf(row)
            StatusPill(tone, label)
            if (hasStats && !selecting) {
                TmmIcon(
                    TmmIconId.V2Chev,
                    size = 22.dp,
                    tint = C.muted2,
                    modifier = if (expanded) Modifier.rotate(90f) else Modifier,
                )
            }
        }
        val kind = row.kind
        if (kind is ChapterKind.Translating) ProgressLine(kind.percent)
        val stats = row.stats
        if (expanded && stats != null && !selecting) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(PillTone.Wait, stringResource(R.string.series_chip_pages_fmt, stats.pagesTranslated, stats.pagesTotal), plain = true)
                StatusPill(
                    PillTone.Wait,
                    pluralStringResource(R.plurals.series_chip_bubbles, stats.bubblesTranslated, stats.bubblesTranslated),
                    plain = true,
                )
                if (stats.bubblesUntranslated > 0) {
                    StatusPill(
                        PillTone.Todo,
                        pluralStringResource(R.plurals.series_chip_left_in_vo, stats.bubblesUntranslated, stats.bubblesUntranslated),
                        plain = true,
                    )
                }
                if (stats.sfxKept > 0) StatusPill(PillTone.Wait, stringResource(R.string.series_chip_sfx_fmt, stats.sfxKept), plain = true)
                if (stats.verifiedDate != null) {
                    StatusPill(PillTone.Ok, stringResource(R.string.series_chip_verified_fmt, stats.verifiedDate), plain = true)
                }
            }
        }
    }
}

@Composable
private fun statusOf(row: SeriesRow): Pair<PillTone, String> = when (val kind = row.kind) {
    ChapterKind.ToTranslate -> PillTone.Todo to stringResource(R.string.series_status_to_translate)
    ChapterKind.OnPc -> PillTone.Wait to stringResource(R.string.series_status_on_pc)
    ChapterKind.Waiting -> PillTone.Wait to stringResource(R.string.series_status_waiting)
    is ChapterKind.Translating -> PillTone.Busy to stringResource(R.string.series_status_translating_fmt, kind.percent)
    is ChapterKind.Failed -> PillTone.Fail to (
        kind.page?.let { stringResource(R.string.series_status_failed_page_fmt, it) } ?: stringResource(R.string.series_status_failed)
        )
    ChapterKind.Ready -> PillTone.Get to stringResource(R.string.series_status_ready)
    ChapterKind.InMihon -> PillTone.Ok to stringResource(R.string.series_status_in_mihon)
    ChapterKind.NotDownloaded -> PillTone.Absent to stringResource(R.string.series_status_not_downloaded_one)
    ChapterKind.Missing -> PillTone.Gap to stringResource(R.string.series_status_missing)
}

/** `.bar` : 6 de haut, fond line2, remplissage encre. */
@Composable
private fun ProgressLine(percent: Int) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.line2)) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(C.ink),
        )
    }
}

@Composable
private fun translateLabel(numbers: List<Int>): String =
    if (numbers.size == 1) {
        stringResource(R.string.series_translate_one_fmt, numbers.first())
    } else {
        pluralStringResource(R.plurals.series_translate_many, numbers.size, numbers.size)
    }

@Composable
private fun MoreSheetContent(state: SeriesState, allSeries: Boolean, onEvent: (SeriesEvent) -> Unit) {
    val counts = if (allSeries) state.seriesCounts else state.selectionCounts
    val kick = if (allSeries) {
        stringResource(R.string.series_sheet_kicker_fmt, stringResource(R.string.series_sheet_all_series), state.title)
    } else {
        stringResource(
            R.string.series_sheet_kicker_fmt,
            pluralStringResource(R.plurals.series_chapters_selected, state.selectionCount, state.selectionCount),
            state.selectedLabels.joinToString(", "),
        )
    }
    V2SheetHeader(kick = kick, title = stringResource(R.string.series_sheet_more_title))

    val redIsInstall = counts.translate == 0 && counts.install > 0
    if (counts.translate > 0) {
        val numbers = if (allSeries) state.toTranslateNumbers else state.selectionToTranslateNumbers
        V2Button(
            label = if (numbers.size == counts.translate) {
                translateLabel(numbers)
            } else {
                pluralStringResource(R.plurals.series_translate_many, counts.translate, counts.translate)
            },
            onClick = { onEvent(SeriesEvent.Translate) },
            modifier = Modifier.padding(top = 16.dp),
            size = V2ButtonSize.Block,
            icon = TmmIconId.V2Translate,
        )
    } else if (redIsInstall) {
        V2Button(
            label = pluralStringResource(R.plurals.series_install_many, counts.install, counts.install),
            onClick = { onEvent(SeriesEvent.Install) },
            modifier = Modifier.padding(top = 16.dp),
            size = V2ButtonSize.Block,
            icon = TmmIconId.V2Down,
        )
    }
    if (counts.install > 0 && !redIsInstall) {
        V2ActionRow(
            icon = TmmIconId.V2Down,
            title = stringResource(R.string.series_action_install_label),
            description = stringResource(R.string.series_action_install_hint),
            onClick = { onEvent(SeriesEvent.Install) },
            count = counts.install,
            iconBackground = C.blueSoft,
            iconTint = C.blue,
        )
    }
    if (counts.verify > 0) {
        V2ActionRow(
            icon = TmmIconId.V2Shield,
            title = stringResource(R.string.series_action_verify_label),
            description = stringResource(R.string.series_action_verify_hint),
            onClick = { onEvent(SeriesEvent.Verify) },
            count = counts.verify,
        )
    }
    if (counts.retranslate > 0) {
        V2ActionRow(
            icon = TmmIconId.V2Redo,
            title = stringResource(R.string.series_action_retranslate_label),
            description = stringResource(R.string.series_action_retranslate_hint),
            onClick = { onEvent(SeriesEvent.Retranslate) },
            count = counts.retranslate,
        )
    }
    if (counts.copy > 0) {
        V2ActionRow(
            icon = TmmIconId.V2Copy,
            title = stringResource(R.string.series_action_copy_label),
            description = stringResource(R.string.series_action_copy_hint),
            onClick = { onEvent(SeriesEvent.CopyToPc) },
            count = counts.copy,
        )
    }
    val deletable = if (allSeries) {
        state.rows.count { it.kind != ChapterKind.Missing && it.kind != ChapterKind.NotDownloaded }
    } else {
        state.selectionCount
    }
    if (deletable > 0) {
        V2ActionRow(
            icon = TmmIconId.V2Trash,
            title = stringResource(R.string.series_action_delete_more_label),
            description = stringResource(R.string.series_action_delete_hint),
            onClick = { onEvent(SeriesEvent.RequestDelete) },
            count = deletable,
            danger = true,
        )
    }
    val locked = state.lockedNumbers
    if (!allSeries && locked.isNotEmpty()) {
        TmmText(
            text = stringResource(
                if (locked.size == 1) R.string.series_locked_note_one_fmt else R.string.series_locked_note_many_fmt,
                locked.joinToString(", "),
            ),
            style = TmmType.sans(12).px(12.5f, lineHeight = 1.4f),
            color = C.muted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
        )
    }
}

@Composable
private fun FollowSheetContent(title: String, numbers: List<Int>, onEvent: (SeriesEvent) -> Unit) {
    V2SheetHeader(kick = title, title = stringResource(R.string.series_follow_sheet_title))
    Column(Modifier.padding(top = 18.dp)) {
        FollowStep(1, R.string.series_follow_step1_title, R.string.series_follow_step1_desc)
        StepLink()
        FollowStep(2, R.string.series_follow_step2_title, R.string.series_follow_step2_desc)
        StepLink()
        FollowStep(null, R.string.series_follow_step3_title, R.string.series_follow_step3_desc)
    }
    if (numbers.isNotEmpty()) {
        val bold = pluralStringResource(R.plurals.series_follow_catchup_bold, numbers.size, numbers.size)
        val rest = stringResource(
            if (numbers.size == 1) R.string.series_follow_catchup_rest_one_fmt else R.string.series_follow_catchup_rest_many_fmt,
            numbers.joinToString(", "),
        )
        Row(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .v2Card(background = C.amberSoft, borderColor = C.amberLine)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TmmIcon(TmmIconId.V2Info, size = 22.dp, tint = C.amber, modifier = Modifier.padding(top = 1.dp))
            TmmText(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
                    append(" ")
                    append(rest)
                },
                style = TmmType.sans(13).px(13.5f, lineHeight = 1.45f),
                color = C.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
    V2Button(
        label = stringResource(R.string.series_follow_confirm_fmt, title),
        onClick = { onEvent(SeriesEvent.ConfirmFollow) },
        modifier = Modifier.padding(top = 16.dp),
        size = V2ButtonSize.Block,
        icon = TmmIconId.V2Bolt,
    )
    V2Button(
        label = stringResource(R.string.common_cancel),
        onClick = { onEvent(SeriesEvent.CloseSheet) },
        modifier = Modifier.padding(top = 6.dp),
        style = V2ButtonStyle.Plain,
        size = V2ButtonSize.Block,
        contentColor = C.muted,
    )
}

/** Etape numerotee de la feuille « Suivre » ; `number = null` : rond vert coche (derniere etape). */
@Composable
private fun FollowStep(number: Int?, titleRes: Int, descRes: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (number == null) C.green else C.ink),
            contentAlignment = Alignment.Center,
        ) {
            if (number == null) {
                TmmIcon(TmmIconId.V2Check, size = 18.dp, tint = C.white)
            } else {
                TmmText(text = number.toString(), style = TmmType.sans(15, weight = 600), color = C.white)
            }
        }
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            TmmText(text = stringResource(titleRes), style = TmmType.sans(15, weight = 600), color = C.ink)
            TmmText(
                text = stringResource(descRes),
                style = TmmType.sans(13).px(13.5f),
                color = C.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun StepLink() {
    Box(Modifier.padding(start = 17.dp).width(2.dp).height(16.dp).background(C.line))
}

/** Feuille « Supprimer » a 3 cases, partagee par la fiche serie et la selection de la Bibliotheque. */
@Composable
internal fun DeleteSheetContent(sheet: SeriesSheet.Delete, onToggle: (DeleteTarget) -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    V2SheetHeader(
        kick = null,
        title = stringResource(
            R.string.series_delete_title_fmt,
            pluralStringResource(R.plurals.series_chapters_count, sheet.chapterCount, sheet.chapterCount),
        ),
    )
    if (sheet.skippedCount > 0) {
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(C.blueSoft)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmIcon(TmmIconId.V2Info, size = 20.dp, tint = C.blue)
            TmmText(
                text = pluralStringResource(R.plurals.series_delete_skipped, sheet.skippedCount, sheet.skippedCount),
                style = TmmType.sans(13).px(13.5f, lineHeight = 1.4f),
                color = C.blue,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Column(Modifier.padding(top = 6.dp)) {
        DeleteOption(
            checked = sheet.localFr,
            label = stringResource(R.string.series_delete_option_fr_label),
            hint = stringResource(R.string.series_delete_option_fr_hint),
            onToggle = { onToggle(DeleteTarget.LocalFr) },
        )
        DeleteOption(
            checked = sheet.mihon,
            label = stringResource(R.string.series_delete_option_mihon_label),
            hint = stringResource(R.string.series_delete_option_mihon_warning),
            hintColor = C.redDeep,
            onToggle = { onToggle(DeleteTarget.Mihon) },
        )
        DeleteOption(
            checked = sheet.pc,
            label = stringResource(R.string.series_delete_option_pc_label),
            hint = stringResource(R.string.series_delete_option_pc_hint),
            onToggle = { onToggle(DeleteTarget.Pc) },
        )
    }
    Row(Modifier.padding(top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V2Button(
            label = stringResource(R.string.common_cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            style = V2ButtonStyle.Ghost,
        )
        V2Button(
            label = stringResource(R.string.series_action_delete_label),
            onClick = onConfirm,
            modifier = Modifier.weight(1f).alpha(if (sheet.anyChecked) 1f else 0.4f),
            enabled = sheet.anyChecked,
        )
    }
}

@Composable
private fun DeleteOption(checked: Boolean, label: String, hint: String, onToggle: () -> Unit, hintColor: Color = C.muted) {
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.white)
            .border(1.dp, C.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        V2Check(checked = checked)
        Column(Modifier.weight(1f)) {
            TmmText(text = label, style = TmmType.sans(15, weight = 600), color = C.ink)
            TmmText(
                text = hint,
                style = TmmType.sans(13).px(13.5f, lineHeight = 1.35f),
                color = hintColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SeriesToastHost(
    toast: SeriesToast?,
    title: String,
    onEvent: (SeriesEvent) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss = { onEvent(SeriesEvent.DismissToast) }
    val see = stringResource(R.string.series_sent_on_follow_see)
    val openQueue = {
        dismiss()
        onOpenQueue()
    }
    val message: String = when (toast) {
        null -> ""
        is SeriesToast.Translated -> pluralStringResource(R.plurals.series_toast_translated, toast.chapterCount, toast.chapterCount)
        is SeriesToast.Copied -> pluralStringResource(R.plurals.series_toast_copied, toast.chapterCount, toast.chapterCount)
        is SeriesToast.Installing -> pluralStringResource(R.plurals.series_toast_installing, toast.chapterCount, toast.chapterCount)
        is SeriesToast.Verified -> pluralStringResource(R.plurals.series_toast_verified, toast.chapterCount, toast.chapterCount)
        is SeriesToast.Retranslated -> pluralStringResource(R.plurals.series_toast_retranslated, toast.chapterCount, toast.chapterCount)
        is SeriesToast.Deleted -> listOfNotNull(
            pluralStringResource(R.plurals.series_toast_deleted, toast.chapterCount, toast.chapterCount),
            if (toast.failed > 0) stringResource(R.string.series_toast_delete_failed) else null,
            if (toast.pcUnavailable) stringResource(R.string.series_toast_delete_pc_unavailable) else null,
        ).joinToString(" ")
        is SeriesToast.PcDeleteResult -> when (toast.outcome) {
            PcDeleteOutcome.DONE -> stringResource(R.string.series_toast_pc_delete_done)
            PcDeleteOutcome.PARTIAL -> pluralStringResource(R.plurals.series_toast_pc_delete_partial, toast.failed, toast.failed)
            PcDeleteOutcome.REJECTED -> stringResource(R.string.series_toast_pc_delete_rejected)
            PcDeleteOutcome.WAITING -> stringResource(R.string.series_toast_pc_delete_waiting)
        }
        is SeriesToast.Followed -> if (toast.chapterCount > 0) {
            pluralStringResource(R.plurals.series_toast_followed, toast.chapterCount, title, toast.chapterCount)
        } else {
            stringResource(R.string.series_toast_followed_none_fmt, title)
        }
    }
    when (toast) {
        is SeriesToast.Followed -> V2Toast(
            visible = true,
            message = message,
            onDismiss = dismiss,
            modifier = modifier,
            icon = TmmIconId.V2Bolt,
            actionLabel = stringResource(R.string.series_toast_undo),
            onAction = { onEvent(SeriesEvent.UndoFollow) },
            durationMs = 7000L,
        )
        is SeriesToast.Deleted, is SeriesToast.PcDeleteResult ->
            V2Toast(visible = true, message = message, onDismiss = dismiss, modifier = modifier, icon = TmmIconId.V2Check)
        null -> V2Toast(visible = false, message = message, onDismiss = dismiss, modifier = modifier)
        else -> V2Toast(
            visible = true,
            message = message,
            onDismiss = dismiss,
            modifier = modifier,
            icon = TmmIconId.V2Check,
            actionLabel = see,
            onAction = openQueue,
            durationMs = 5000L,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SeriesScreenPreview() {
    TmmTheme {
        SeriesScreen(state = MockupData.seriesDetail, onEvent = {})
    }
}
