package com.tmm.mobile.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.Cover
import com.tmm.mobile.ui.components.StatusPill
import com.tmm.mobile.ui.components.PillTone
import com.tmm.mobile.ui.components.px
import com.tmm.mobile.ui.screens.translate.LocalOpenTranslate
import com.tmm.mobile.ui.screens.translate.TranslateRequest
import com.tmm.mobile.ui.components.TmmCheckbox
import com.tmm.mobile.ui.components.TmmChip
import com.tmm.mobile.ui.components.TmmSearchField
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.V2ActionRow
import com.tmm.mobile.ui.components.V2Button
import com.tmm.mobile.ui.components.V2ButtonSize
import com.tmm.mobile.ui.components.V2Check
import com.tmm.mobile.ui.components.V2IconButton
import com.tmm.mobile.ui.components.V2SelectionBar
import com.tmm.mobile.ui.components.V2Sheet
import com.tmm.mobile.ui.components.V2SheetHeader
import com.tmm.mobile.ui.components.V2Toast
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockSeries
import com.tmm.mobile.ui.screens.series.DeleteSheetContent
import com.tmm.mobile.ui.components.PullToRefresh
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ecran Bibliotheque (Library.dc.html, E4.2). Contenu propre a l'ecran seulement ;
 * la barre de navigation basse commune est posee par TmmRoot en E10.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    onOpenSeries: (String) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRefresh: suspend () -> Unit = {},
) {
    Box(modifier = modifier.background(TmmColors.paper)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(state = state, onEvent = onEvent)
            PullToRefresh(onRefresh, Modifier.weight(1f)) {
                LibraryGrid(state = state, onEvent = onEvent, onOpenSeries = onOpenSeries)
            }
            if (state.hasSelection) {
                SelectionActionBar(state = state, onEvent = onEvent)
            }
        }
        LibraryFiltersSheet(state = state, onEvent = onEvent)
        LibraryMoreSheet(state = state, onEvent = onEvent)
        V2Sheet(visible = state.delete != null, onDismiss = { onEvent(LibraryEvent.CloseDelete) }) {
            state.delete?.let { sheet ->
                DeleteSheetContent(
                    sheet = sheet,
                    onToggle = { onEvent(LibraryEvent.ToggleDeleteTarget(it)) },
                    onCancel = { onEvent(LibraryEvent.CloseDelete) },
                    onConfirm = { onEvent(LibraryEvent.ConfirmDelete) },
                )
            }
        }
        LibraryToastHost(
            toast = state.toast,
            onEvent = onEvent,
            onOpenQueue = onOpenQueue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun LibraryToastHost(toast: LibraryToast?, onEvent: (LibraryEvent) -> Unit, onOpenQueue: () -> Unit, modifier: Modifier) {
    val dismiss = { onEvent(LibraryEvent.DismissToast) }
    val message: String = when (toast) {
        null -> ""
        is LibraryToast.Installing -> pluralStringResource(R.plurals.series_toast_installing, toast.chapterCount, toast.chapterCount)
        is LibraryToast.Verified -> pluralStringResource(R.plurals.series_toast_verified, toast.chapterCount, toast.chapterCount)
        is LibraryToast.Retranslated -> pluralStringResource(R.plurals.series_toast_retranslated, toast.chapterCount, toast.chapterCount)
        is LibraryToast.Copied -> pluralStringResource(R.plurals.series_toast_copied, toast.chapterCount, toast.chapterCount)
        is LibraryToast.Deleted -> listOfNotNull(
            pluralStringResource(R.plurals.series_toast_deleted, toast.chapterCount, toast.chapterCount),
            if (toast.failed > 0) stringResource(R.string.series_toast_delete_failed) else null,
            if (toast.pcUnavailable) stringResource(R.string.series_toast_delete_pc_unavailable) else null,
        ).joinToString(" ")
        is LibraryToast.Followed -> {
            val series = pluralStringResource(R.plurals.library_series_count, toast.seriesCount, toast.seriesCount)
            if (toast.chapterCount > 0) {
                pluralStringResource(R.plurals.library_v2_toast_followed, toast.chapterCount, series, toast.chapterCount)
            } else {
                stringResource(R.string.library_v2_toast_followed_none_fmt, series)
            }
        }
    }
    // La suppression n'a rien a montrer dans Activite : pas de lien « Voir ».
    val showSee = toast != null && toast !is LibraryToast.Deleted
    V2Toast(
        visible = toast != null,
        message = message,
        onDismiss = dismiss,
        modifier = modifier,
        icon = if (toast is LibraryToast.Followed) TmmIconId.V2Bolt else TmmIconId.V2Check,
        actionLabel = if (showSee) stringResource(R.string.series_sent_on_follow_see) else null,
        onAction = {
            dismiss()
            onOpenQueue()
        },
        durationMs = 5000L,
    )
}

@Composable
private fun LibraryHeader(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TmmText(text = stringResource(R.string.library_title), style = TmmType.display(40, weight = 800))
            TmmText(
                text = stringResource(R.string.library_v2_subtitle, state.allSeries.size,
                    state.allSeries.count { it.onPhone == 0 }),
                style = TmmType.sans(14), color = TmmColors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TmmSearchField(value = state.query, onValueChange = { onEvent(LibraryEvent.QueryChanged(it)) },
                placeholder = stringResource(R.string.library_search_placeholder), modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                    .background(TmmColors.V2.white).border(1.5.dp, TmmColors.V2.line, RoundedCornerShape(16.dp))
                    .clickable { onEvent(LibraryEvent.OpenFilters) }, contentAlignment = Alignment.Center,
            ) {
                TmmIcon(TmmIconId.Filter, size = 20.dp, contentDescription = stringResource(R.string.library_filters_title))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TmmChip(
                label = stringResource(R.string.library_v2_category, if (state.category == "Tous") stringResource(R.string.library_v2_all_categories) else state.category),
                selected = state.category != "Tous", onClick = { onEvent(LibraryEvent.OpenFilters) },
            )
            viewFilterOptions().filter { it.first !in listOf(LibraryViewFilter.Downloaded, LibraryViewFilter.OnPc) }.forEach { (view, label) ->
                TmmChip(label = label, selected = state.view == view,
                    count = state.copy(query = "", view = view).visibleSeries.size,
                    onClick = { onEvent(LibraryEvent.SelectView(view)) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TmmText(text = stringResource(when (state.sort) {
                LibrarySort.RecentlyAdded -> R.string.library_sort_recent
                LibrarySort.Title -> R.string.library_sort_title
                LibrarySort.ToTranslateFirst -> R.string.library_sort_todo_first
            }), style = TmmType.sans(13), color = TmmColors.textMuted)
            TmmText(text = stringResource(if (state.picking) R.string.library_finish_button else R.string.library_select_button),
                style = TmmType.sans(14, weight = 600), color = TmmColors.V2.red,
                modifier = Modifier.clickable { onEvent(LibraryEvent.TogglePicking) }.padding(vertical = 8.dp))
        }
        if (state.picking) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TmmText(text = stringResource(R.string.library_picking_hint), style = TmmType.sans(13))
                TmmText(text = if (state.allVisibleSelected) stringResource(R.string.library_deselect_all)
                    else stringResource(R.string.library_select_all_fmt, state.visibleSeries.size),
                    style = TmmType.sans(13, weight = 600), color = TmmColors.V2.red,
                    modifier = Modifier.clickable { onEvent(LibraryEvent.ToggleSelectAll) })
            }
        }
    }
}

@Composable
private fun LibraryGrid(state: LibraryState, onEvent: (LibraryEvent) -> Unit, onOpenSeries: (String) -> Unit) {
    val visible = state.visibleSeries
    if (visible.isEmpty()) {
        EmptyState()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // seriesKey et non title : deux series de sources differentes peuvent porter le meme titre,
        // et une cle dupliquee fait planter LazyVerticalGrid.
        items(visible, key = { it.seriesKey }) { series ->
            if (state.picking) {
                PickingTile(series = series, selected = series.seriesKey in state.selected, onToggle = { onEvent(LibraryEvent.ToggleSelected(series.seriesKey)) })
            } else {
                SeriesTile(series = series, details = state.details[series.seriesKey], onClick = { onOpenSeries(series.seriesKey) })
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp, 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TmmIcon(TmmIconId.SearchEmpty, size = 40.dp, tint = TmmColors.textMuted)
        TmmText(text = stringResource(R.string.library_empty_title), style = TmmType.sans(15, weight = 600), modifier = Modifier.padding(top = 10.dp))
        TmmText(
            text = stringResource(R.string.library_empty_subtitle),
            style = TmmType.sans(13, lineHeight = 19f),
            color = TmmColors.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SeriesTile(series: MockSeries, details: LibrarySeriesDetails?, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(110f / 150f).then(
                if (series.tone == com.tmm.mobile.ui.components.StatusTone.Absent) {
                    Modifier.dashedBorder(TmmColors.CB9B0A0)
                } else {
                    Modifier
                },
            ),
        ) {
            Cover(seriesKey = series.seriesKey, coverUrl = series.thumbnailUrl, color = TmmColors.fromHexOrPlaceholder(series.colorHex, series.seriesKey), modifier = Modifier.fillMaxSize())
            LibraryStatusPill(series, details, Modifier.align(Alignment.BottomStart).padding(8.dp))
            if (details?.followed == true) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp))
                    .background(TmmColors.V2.ink).padding(6.dp)) {
                    TmmIcon(TmmIconId.V2Bolt, size = 13.dp, tint = TmmColors.V2.white,
                        contentDescription = stringResource(R.string.library_v2_followed_series))
                }
            }
        }
        TmmText(text = series.title, style = TmmType.sans(14, weight = 600).px(14.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val frLabel = if (series.totalChapters > 0) {
                stringResource(R.string.library_v2_progress, series.frChapters, series.totalChapters)
            } else {
                stringResource(R.string.library_fr_mihon_only)
            }
            TmmText(text = frLabel, style = TmmType.sans(12), color = TmmColors.textMuted)
        }
    }
}

@Composable
private fun LibraryStatusPill(series: MockSeries, details: LibrarySeriesDetails?, modifier: Modifier) {
    val tone = libraryPillTone(series, details)
    val label = when (tone) {
        PillTone.Fail -> pluralStringResource(R.plurals.library_v2_failures, details?.failures ?: 0, details?.failures ?: 0)
        PillTone.Busy -> stringResource(R.string.library_v2_busy)
        PillTone.Get -> stringResource(R.string.library_v2_install, details?.toInstall ?: series.toGet)
        PillTone.Todo -> stringResource(R.string.library_v2_translate, series.toSend)
        PillTone.Absent -> stringResource(R.string.library_v2_absent)
        else -> stringResource(R.string.library_v2_current)
    }
    StatusPill(tone = tone, label = label, small = true, modifier = modifier)
}

@Composable
private fun PickingTile(series: MockSeries, selected: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onToggle),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(110f / 150f)) {
            Cover(seriesKey = series.seriesKey, coverUrl = series.thumbnailUrl, color = TmmColors.fromHexOrPlaceholder(series.colorHex, series.seriesKey), modifier = Modifier.fillMaxSize())
            TmmCheckbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        TmmText(text = series.title, style = TmmType.sans(14, weight = 600).px(14.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TmmText(text = series.badge, style = TmmType.sans(12), color = TmmColors.textMuted)
    }
}

/**
 * Barre de selection multi-series : la meme que la fiche serie (ecran 4 de la maquette v2), qui
 * ouvre la feuille « Que faire ? ».
 */
@Composable
private fun SelectionActionBar(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    V2SelectionBar(
        label = pluralStringResource(R.plurals.library_series_selected, state.selectionCount, state.selectionCount),
        actionLabel = stringResource(R.string.series_selection_bar_action),
        onAction = { onEvent(LibraryEvent.OpenMore) },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

/**
 * Feuille « Que faire ? » de la selection multi-series, construite comme celle de la fiche serie.
 * Les comptes (state.actions) sont calcules a l'ouverture avec les memes predicats ; la feuille
 * attend qu'ils soient prets plutot que d'afficher des nombres faux.
 */
@Composable
private fun LibraryMoreSheet(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    val openTranslate = LocalOpenTranslate.current
    val actions = state.actions
    V2Sheet(visible = state.moreOpen && state.hasSelection && actions != null, onDismiss = { onEvent(LibraryEvent.CloseMore) }) {
        if (actions == null) return@V2Sheet
        val counts = actions.counts
        // Titres listes seulement pour une petite selection : V2SheetHeader ne tronque pas, et
        // « Tout cocher » sur 142 series donnerait un surtitre interminable.
        val count = pluralStringResource(R.plurals.library_series_selected, state.selectionCount, state.selectionCount)
        V2SheetHeader(
            kick = if (state.selectionCount <= 3) {
                stringResource(R.string.series_sheet_kicker_fmt, count, state.selectionLabel)
            } else {
                count
            },
            title = stringResource(R.string.series_sheet_more_title),
        )
        // Un seul bouton rouge : Traduire, sinon Installer s'il n'y a rien a traduire.
        val redIsInstall = counts.translate == 0 && counts.install > 0
        if (counts.translate > 0) {
            V2Button(
                label = pluralStringResource(R.plurals.series_translate_many, counts.translate, counts.translate),
                onClick = {
                    onEvent(LibraryEvent.CloseMore)
                    openTranslate(TranslateRequest(true, state.selected))
                },
                modifier = Modifier.padding(top = 16.dp),
                size = V2ButtonSize.Block,
                icon = TmmIconId.V2Translate,
            )
        } else if (redIsInstall) {
            V2Button(
                label = pluralStringResource(R.plurals.series_install_many, counts.install, counts.install),
                onClick = { onEvent(LibraryEvent.Install) },
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
                onClick = { onEvent(LibraryEvent.Install) },
                count = counts.install,
                iconBackground = TmmColors.V2.blueSoft,
                iconTint = TmmColors.V2.blue,
            )
        }
        if (counts.verify > 0) {
            V2ActionRow(
                icon = TmmIconId.V2Shield,
                title = stringResource(R.string.series_action_verify_label),
                description = stringResource(R.string.series_action_verify_hint),
                onClick = { onEvent(LibraryEvent.Verify) },
                count = counts.verify,
            )
        }
        if (counts.retranslate > 0) {
            V2ActionRow(
                icon = TmmIconId.V2Redo,
                title = stringResource(R.string.series_action_retranslate_label),
                description = stringResource(R.string.series_action_retranslate_hint),
                onClick = { onEvent(LibraryEvent.Retranslate) },
                count = counts.retranslate,
            )
        }
        if (counts.copy > 0) {
            V2ActionRow(
                icon = TmmIconId.V2Copy,
                title = stringResource(R.string.series_action_copy_label),
                description = stringResource(R.string.series_action_copy_hint),
                onClick = { onEvent(LibraryEvent.CopyToPc) },
                count = counts.copy,
            )
        }
        if (actions.toFollow > 0) {
            V2ActionRow(
                icon = TmmIconId.V2Bolt,
                title = stringResource(R.string.library_v2_follow_label),
                description = stringResource(R.string.library_v2_follow_hint),
                onClick = { onEvent(LibraryEvent.Follow) },
                count = actions.toFollow,
            )
        }
        if (actions.deletable > 0) {
            V2ActionRow(
                icon = TmmIconId.V2Trash,
                title = stringResource(R.string.series_action_delete_more_label),
                description = stringResource(R.string.series_action_delete_hint),
                onClick = { onEvent(LibraryEvent.RequestDelete) },
                count = actions.deletable,
                danger = true,
            )
        }
    }
}

/** Options "Afficher" partagees par le segmente de l'en-tete et la feuille Trier et filtrer. */
@Composable
private fun viewFilterOptions(): List<Pair<LibraryViewFilter, String>> = listOf(
    LibraryViewFilter.All to stringResource(R.string.library_view_all),
    LibraryViewFilter.Todo to stringResource(R.string.library_view_todo),
    LibraryViewFilter.Followed to stringResource(R.string.library_v2_followed),
    LibraryViewFilter.Downloaded to stringResource(R.string.library_view_downloaded),
    LibraryViewFilter.OnPc to stringResource(R.string.library_view_on_pc),
    LibraryViewFilter.Absent to stringResource(R.string.library_view_absent),
)

/**
 * Feuille Trier et filtrer (ecran 2 bis de la maquette v2) : categories Mihon (4 premieres, puis
 * « + N autres » qui deplie), Afficher, Trier, et la date de la sauvegarde Mihon lue. Les choix
 * s'appliquent immediatement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFiltersSheet(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    V2Sheet(visible = state.filtersOpen, onDismiss = { onEvent(LibraryEvent.CloseFilters) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                V2SheetHeader(kick = stringResource(R.string.library_title), title = stringResource(R.string.library_filters_title))
            }
            V2IconButton(
                icon = TmmIconId.V2X,
                contentDescription = stringResource(R.string.translate_close_cd),
                onClick = { onEvent(LibraryEvent.CloseFilters) },
            )
        }
        FiltersLabel(stringResource(R.string.library_v2_mihon_category))
        // Une categorie choisie hors des 4 premieres reste visible : la liste est alors depliee.
        val selectedIndex = state.categories.indexOfFirst { it.first == state.category }
        val expanded = state.categoriesExpanded || selectedIndex >= VISIBLE_CATEGORIES
        val shown = if (expanded) state.categories else state.categories.take(VISIBLE_CATEGORIES)
        val hidden = state.categories.size - shown.size
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TmmColors.V2.card)
                .border(1.dp, TmmColors.V2.line, RoundedCornerShape(22.dp)),
        ) {
            shown.forEachIndexed { index, (name, count) ->
                FilterListRow(divider = index > 0, onClick = { onEvent(LibraryEvent.SelectCategory(name)) }) {
                    TmmText(
                        text = if (name == "Tous") stringResource(R.string.library_view_all) else name,
                        style = TmmType.sans(15, weight = 600).px(15.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TmmText(text = count.toString(), style = TmmType.mono(14), color = TmmColors.V2.muted)
                    V2Check(checked = state.category == name)
                }
            }
            if (hidden > 0) {
                FilterListRow(divider = shown.isNotEmpty(), onClick = { onEvent(LibraryEvent.ExpandCategories) }) {
                    TmmText(
                        text = pluralStringResource(R.plurals.library_v2_more_categories, hidden, hidden),
                        style = TmmType.sans(15, weight = 600).px(15.5f),
                        color = TmmColors.V2.muted,
                        modifier = Modifier.weight(1f),
                    )
                    TmmIcon(TmmIconId.V2Chevd, size = 18.dp, tint = TmmColors.V2.muted2)
                }
            }
        }
        FiltersLabel(stringResource(R.string.library_view_section))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            viewFilterOptions().forEach { (view, label) ->
                TmmChip(label = label, selected = state.view == view, onClick = { onEvent(LibraryEvent.SelectView(view)) })
            }
        }
        FiltersLabel(stringResource(R.string.library_sort_section))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                LibrarySort.RecentlyAdded to stringResource(R.string.library_sort_recent),
                LibrarySort.Title to stringResource(R.string.library_sort_title),
                LibrarySort.ToTranslateFirst to stringResource(R.string.library_sort_todo_first),
            ).forEach { (sort, label) ->
                TmmChip(label = label, selected = state.sort == sort, onClick = { onEvent(LibraryEvent.SelectSort(sort)) })
            }
        }
        TmmText(
            text = backupNote(state.backupModifiedAt),
            style = TmmType.sans(12).px(12.5f, lineHeight = 1.45f),
            color = TmmColors.V2.muted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
        )
    }
}

private const val VISIBLE_CATEGORIES = 4

/** `.lbl` de la maquette : petites capitales grises au-dessus de chaque groupe. */
@Composable
private fun FiltersLabel(text: String) {
    TmmText(
        text = text.uppercase(),
        style = TmmType.sans(12, weight = 600, letterSpacingEm = 1.3f / 12f),
        color = TmmColors.V2.muted,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 10.dp),
    )
}

/** `.li` de la maquette : ligne de liste avec separateur `line2`, sauf en tete. */
@Composable
private fun FilterListRow(divider: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(TmmColors.V2.line2))
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** « ... du jour (03:00) » si la sauvegarde date d'aujourd'hui, sinon sa date. */
@Composable
private fun backupNote(modifiedAt: Long?): String {
    if (modifiedAt == null) return stringResource(R.string.library_v2_filters_note_none)
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(modifiedAt).atZone(zone)
    val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (at.toLocalDate() == LocalDate.now(zone)) {
        stringResource(R.string.library_v2_filters_note_today_fmt, time)
    } else {
        stringResource(R.string.library_v2_filters_note_date_fmt, at.format(DateTimeFormatter.ofPattern("dd/MM")), time)
    }
}
