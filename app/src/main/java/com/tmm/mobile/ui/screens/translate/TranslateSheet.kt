package com.tmm.mobile.ui.screens.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.Cover
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.V2Button
import com.tmm.mobile.ui.components.V2ButtonSize
import com.tmm.mobile.ui.components.V2Check
import com.tmm.mobile.ui.components.V2IconButton
import com.tmm.mobile.ui.components.V2LazySheet
import com.tmm.mobile.ui.components.V2SheetHeader
import com.tmm.mobile.ui.components.px
import com.tmm.mobile.ui.components.v2CardItem
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

private val C = TmmColors.V2

/** Feuille « Traduire » (maquette v2, écran 6) : une seule étape, séries dépliables sur place. */
@Composable
fun TranslateSheet(
    visible: Boolean,
    state: TranslateState,
    onEvent: (TranslateEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val kick = pluralStringResource(R.plurals.translate_kick, state.totalCount, state.totalCount)
    val title = stringResource(R.string.translate_title)
    val closeCd = stringResource(R.string.translate_close_cd)
    // Liste paresseuse : avec 2000 series, une Column composait chaque ligne (et chaque chapitre
    // deplie) a l'ouverture de la feuille. Le filtre n'est recalcule que si la liste ou la recherche change.
    val series = remember(state.series, state.query) { state.filtered }
    V2LazySheet(
        visible = visible,
        onDismiss = onDismiss,
        paneTitle = title,
        footer = { TranslateFooter(state, onEvent) },
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { V2SheetHeader(kick = kick, title = title) }
                V2IconButton(TmmIconId.V2X, closeCd, onDismiss)
            }
        }
        item(key = "search") {
            SearchField(
                value = state.query,
                onValueChange = { onEvent(TranslateEvent.UpdateQuery(it)) },
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        series.forEachIndexed { index, s ->
            val expanded = s.seriesKey in state.expanded
            val lastSeries = index == series.lastIndex
            item(key = "s:" + s.seriesKey, contentType = "series") {
                SeriesItem(
                    series = s,
                    state = state,
                    onEvent = onEvent,
                    expanded = expanded,
                    modifier = Modifier
                        .padding(top = if (index == 0) 12.dp else 0.dp)
                        .fillMaxWidth()
                        .v2CardItem(isFirst = index == 0, isLast = lastSeries && !expanded),
                    showDivider = index > 0,
                )
            }
            if (expanded) {
                val chapters = s.chapters
                items(chapters.size, key = { "c:" + chapters[it].mihonPath }, contentType = { "chapter" }) { i ->
                    val last = i == chapters.lastIndex
                    ChapterItem(
                        chapter = chapters[i],
                        checked = chapters[i].mihonPath in state.selected,
                        onEvent = onEvent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .v2CardItem(isFirst = false, isLast = lastSeries && last)
                            .padding(start = 52.dp, end = 16.dp, bottom = if (last) 6.dp else 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslateFooter(state: TranslateState, onEvent: (TranslateEvent) -> Unit) {
    val count = remember(state.series, state.selected) { state.selectedCount }
    val enabled = count > 0 && (state.phase == TranslatePhase.Idle || state.phase == TranslatePhase.Failed)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(C.bg),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Column(Modifier.padding(top = 14.dp, bottom = 22.dp)) {
            if (state.phase == TranslatePhase.Failed) {
                TmmText(
                    text = stringResource(R.string.translate_failed),
                    style = TmmType.sans(14).copy(textAlign = TextAlign.Center),
                    color = C.red,
                    modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth(),
                )
            }
            V2Button(
                label = pluralStringResource(R.plurals.translate_button, count, count),
                onClick = { onEvent(TranslateEvent.Send(translate = true)) },
                size = V2ButtonSize.Block,
                icon = TmmIconId.V2Translate,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            val link = stringResource(R.string.translate_copy_link)
            TmmText(
                text = buildAnnotatedString {
                    append(stringResource(R.string.translate_copy_prefix))
                    append(" ")
                    withStyle(SpanStyle(color = C.red, textDecoration = TextDecoration.Underline)) { append(link) }
                },
                style = TmmType.sans(14).copy(textAlign = TextAlign.Center),
                color = C.muted,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .semantics {
                        role = Role.Button
                        contentDescription = link
                    }
                    .clickable(enabled = enabled) { onEvent(TranslateEvent.Send(translate = false)) },
            )
            TmmText(
                text = stringResource(R.string.translate_note),
                style = TmmType.sans(12).px(12.5f).copy(textAlign = TextAlign.Center),
                color = C.muted,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SeriesItem(
    series: TranslateSeries,
    state: TranslateState,
    onEvent: (TranslateEvent) -> Unit,
    expanded: Boolean,
    modifier: Modifier,
    showDivider: Boolean,
) {
    val check = state.checkOf(series)
    Column(modifier) {
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line2))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TriCheck(
                check = check,
                modifier = Modifier
                    .semantics { contentDescription = series.title }
                    .clickable { onEvent(TranslateEvent.ToggleSeries(series.seriesKey)) },
            )
            Row(
                Modifier
                    .weight(1f)
                    .clickable { onEvent(TranslateEvent.ToggleExpand(series.seriesKey)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Cover(
                    seriesKey = series.seriesKey,
                    coverUrl = series.coverUrl,
                    color = series.color,
                    modifier = Modifier.size(34.dp, 46.dp).clip(RoundedCornerShape(6.dp)),
                )
                Column(Modifier.weight(1f)) {
                    TmmText(text = series.title, style = TmmType.sans(15, weight = 600).px(15.5f), color = C.ink, maxLines = 1)
                    TmmText(
                        text = describe(series, state),
                        style = TmmType.sans(13).px(13.5f, 1.35f),
                        color = C.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TmmIcon(
                    TmmIconId.V2Chevd,
                    size = 20.dp,
                    tint = C.muted2,
                    modifier = if (expanded) Modifier.rotate(180f) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ChapterItem(chapter: TranslateChapter, checked: Boolean, onEvent: (TranslateEvent) -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.translate_chapter_fmt, chapter.numberLabel)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onEvent(TranslateEvent.ToggleChapter(chapter.mihonPath)) }
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            V2Check(checked = checked)
            TmmText(text = label, style = TmmType.mono(14), color = C.ink)
            // Plusieurs scantrads pour ce numéro : on la montre pour choisir laquelle traduire (ou les deux).
            TmmText(
                text = if (chapter.duplicate) chapter.scanlator ?: stringResource(R.string.translate_unknown_scanlator) else "",
                style = TmmType.sans(13),
                color = C.muted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TmmText(
                text = stringResource(R.string.translate_pages_fmt, chapter.pages),
                style = TmmType.sans(13),
                color = C.muted,
            )
        }
    }
}

/** `.check` à 3 états : la sélection partielle montre un tiret noir sur fond blanc. */
@Composable
private fun TriCheck(check: SeriesCheck, modifier: Modifier = Modifier) {
    if (check != SeriesCheck.Partial) {
        V2Check(checked = check == SeriesCheck.All, modifier = modifier)
        return
    }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(shape)
            .background(C.white)
            .border(1.8.dp, C.ink, shape)
            .semantics { role = Role.Checkbox },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(10.dp, 2.4.dp).clip(RoundedCornerShape(2.dp)).background(C.ink))
    }
}

/**
 * « Ch. 7 à 11 · 5 ch. », « Ch. 118, 119 », « Ch. 214 » ou « 2 sur 3 cochés » si partiel, suivi de
 * « · 15 en double » quand d'autres scantrads fournissent les mêmes numéros.
 */
@Composable
private fun describe(series: TranslateSeries, state: TranslateState): String {
    val chapters = series.chapters
    val base = if (state.checkOf(series) == SeriesCheck.Partial) {
        val n = chapters.count { it.mihonPath in state.selected }
        stringResource(R.string.translate_desc_partial_fmt, n, chapters.size)
    } else {
        val labels = chapters.map { it.numberLabel }.distinct()
        when (labels.size) {
            1 -> stringResource(R.string.translate_chapter_fmt, labels[0])
            2 -> stringResource(R.string.translate_desc_two_fmt, labels[0], labels[1])
            else -> stringResource(R.string.translate_desc_range_fmt, labels.first(), labels.last(), series.numberCount)
        }
    }
    val dupes = series.duplicateCount
    return if (dupes == 0) base else stringResource(R.string.translate_desc_dupes_fmt, base, dupes)
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val placeholder = stringResource(R.string.translate_search)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(C.white)
            .border(1.5.dp, C.line, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TmmIcon(TmmIconId.V2Search, size = 20.dp, tint = C.muted2)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) TmmText(text = placeholder, style = TmmType.sans(15).px(15.5f), color = C.muted2)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TmmType.sans(15).px(15.5f).copy(color = C.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}
