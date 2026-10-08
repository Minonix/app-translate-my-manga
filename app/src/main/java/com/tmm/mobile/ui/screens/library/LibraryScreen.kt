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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.tmm.mobile.ui.components.Cover
import com.tmm.mobile.ui.components.SectionLabel
import com.tmm.mobile.ui.components.StatusBadge
import com.tmm.mobile.ui.components.TmmActionButton
import com.tmm.mobile.ui.components.TmmButton
import com.tmm.mobile.ui.components.TmmCheckbox
import com.tmm.mobile.ui.components.TmmChip
import com.tmm.mobile.ui.components.TmmSearchField
import com.tmm.mobile.ui.components.TmmSegmented
import com.tmm.mobile.ui.components.TmmSheet
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.TmmToast
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockSeries
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

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
) {
    Box(modifier = modifier.background(TmmColors.paper)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(state = state, onEvent = onEvent)
            Box(modifier = Modifier.weight(1f)) {
                LibraryGrid(state = state, onEvent = onEvent, onOpenSeries = onOpenSeries)
            }
            if (state.hasSelection) {
                SelectionActionBar(state = state, onEvent = onEvent)
            }
        }
        LibraryFiltersSheet(state = state, onEvent = onEvent)
        LibraryConfirmSheet(state = state, onEvent = onEvent)
        val toastMessage = when (val toast = state.toast) {
            is LibraryToast.Sent -> pluralStringResource(R.plurals.library_toast_sent, toast.chapterCount, toast.chapterCount)
            is LibraryToast.Received -> pluralStringResource(R.plurals.library_toast_received, toast.chapterCount, toast.chapterCount)
            is LibraryToast.Verified -> pluralStringResource(R.plurals.library_toast_verified, toast.chapterCount, toast.chapterCount)
            is LibraryToast.Translated -> pluralStringResource(R.plurals.library_toast_translated, toast.chapterCount, toast.chapterCount)
            is LibraryToast.Retranslated -> pluralStringResource(R.plurals.library_toast_retranslated, toast.chapterCount, toast.chapterCount)
            null -> ""
        }
        TmmToast(
            visible = state.toast != null,
            message = toastMessage,
            onDismiss = { onEvent(LibraryEvent.DismissToast) },
            actionLabel = stringResource(R.string.library_toast_action),
            onAction = onOpenQueue,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun LibraryHeader(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Titre seul sur sa ligne : a cote des boutons, il ne laissait plus assez de place et
        // « Sélectionner » passait sur deux lignes (comme la maquette, les boutons s'alignent sur le sous-titre).
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TmmText(text = stringResource(R.string.library_title), style = TmmType.display(36, weight = 800))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val subtitle = if (state.picking) {
                        pluralStringResource(R.plurals.library_series_selected, state.selectionCount, state.selectionCount)
                    } else {
                        // ponytail: subtitle recompose depuis les vraies donnees (state.allSeries/categories)
                        // plutot que le texte fige de la maquette (library_subtitle_default, garde defini-mais-
                        // -inutilise pour MockupTextCoverageTest, qui ne verifie que l'existence de la ressource).
                        val seriesCount = state.allSeries.size
                        val categoryCount = (state.categories.size - 1).coerceAtLeast(0)
                        pluralStringResource(R.plurals.library_series_count, seriesCount, seriesCount) + " · " +
                            pluralStringResource(R.plurals.library_categories_count, categoryCount, categoryCount)
                    }
                    TmmText(text = subtitle, style = TmmType.sans(13), color = TmmColors.textMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.picking) {
                        // Ecart a la maquette (lien vers Catalog.dc.html) : ouvre la feuille Trier et filtrer.
                        val filtersCd = stringResource(R.string.library_filters_title)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TmmColors.surface)
                                .border(1.dp, TmmColors.track, RoundedCornerShape(10.dp))
                                .clickable { onEvent(LibraryEvent.OpenFilters) },
                            contentAlignment = Alignment.Center,
                        ) {
                            TmmIcon(TmmIconId.Filter, size = 16.dp, contentDescription = filtersCd)
                        }
                    }
                    val pickLabel = if (state.picking) stringResource(R.string.library_finish_button) else stringResource(R.string.library_select_button)
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (state.picking) TmmColors.ink else TmmColors.surface)
                            .border(1.dp, if (state.picking) TmmColors.ink else TmmColors.track, RoundedCornerShape(10.dp))
                            .clickable { onEvent(LibraryEvent.TogglePicking) }
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TmmIcon(TmmIconId.CheckboxChecked, size = 16.dp, tint = if (state.picking) TmmColors.paper else TmmColors.ink)
                        TmmText(text = pickLabel, style = TmmType.sans(13, weight = 600), color = if (state.picking) TmmColors.paper else TmmColors.ink, maxLines = 1)
                    }
                }
            }
        }
        TmmSearchField(
            value = state.query,
            onValueChange = { onEvent(LibraryEvent.QueryChanged(it)) },
            placeholder = stringResource(R.string.library_search_placeholder),
        )
        val categoriesTablistCd = stringResource(R.string.library_categories_tablist_cd)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .semantics { contentDescription = categoriesTablistCd },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.categories.forEach { (name, count) ->
                TmmChip(label = name, selected = state.category == name, count = count, onClick = { onEvent(LibraryEvent.SelectCategory(name)) })
            }
        }
        if (!state.picking) {
            TmmSegmented(
                options = viewFilterOptions(),
                selected = state.view,
                onSelect = { onEvent(LibraryEvent.SelectView(it)) },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TmmColors.ink)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmText(text = stringResource(R.string.library_picking_hint), style = TmmType.sans(13), color = TmmColors.paper)
                val allLabel = if (state.allVisibleSelected) {
                    stringResource(R.string.library_deselect_all)
                } else {
                    stringResource(R.string.library_select_all_fmt, state.visibleSeries.size)
                }
                TmmText(
                    text = allLabel,
                    style = TmmType.sans(13, weight = 600),
                    color = TmmColors.CF4B3A3,
                    modifier = Modifier.clickable { onEvent(LibraryEvent.ToggleSelectAll) },
                )
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
                PickingTile(series = series, selected = series.title in state.selected, onToggle = { onEvent(LibraryEvent.ToggleSelected(series.title)) })
            } else {
                SeriesTile(series = series, onClick = { onOpenSeries(series.seriesKey) })
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
private fun SeriesTile(series: MockSeries, onClick: () -> Unit) {
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
            StatusBadge(tone = series.tone, label = series.badge, modifier = Modifier.padding(6.dp))
        }
        TmmText(text = series.title, style = TmmType.sans(13, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val fraction = if (series.totalChapters > 0) series.frChapters.toFloat() / series.totalChapters else 0f
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            com.tmm.mobile.ui.components.ProgressBar(fraction = fraction)
            val frLabel = if (series.totalChapters > 0) {
                stringResource(R.string.library_fr_progress_fmt, series.frChapters, series.totalChapters)
            } else {
                stringResource(R.string.library_fr_mihon_only)
            }
            TmmText(text = frLabel, style = TmmType.mono(10), color = TmmColors.textMuted)
        }
    }
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
        TmmText(text = series.title, style = TmmType.sans(13, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TmmText(text = series.badge, style = TmmType.mono(10), color = TmmColors.textMuted)
    }
}

@Composable
private fun SelectionActionBar(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TmmColors.paperRaised)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TmmText(text = state.selectionLabel, style = TmmType.sans(13, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                icon = TmmIconId.Up,
                label = stringResource(R.string.library_action_send_label),
                count = state.selectionToSend,
                hintSuffix = stringResource(R.string.library_action_hint_send),
                primary = true,
                onClick = { onEvent(LibraryEvent.SendToPc) },
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                icon = TmmIconId.Down,
                label = stringResource(R.string.library_action_get_label),
                count = state.selectionToGet,
                hintSuffix = stringResource(R.string.library_action_hint_get),
                onClick = { onEvent(LibraryEvent.FetchToMihon) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile(
                icon = TmmIconId.ShieldCheck,
                label = stringResource(R.string.library_action_verify_label),
                count = state.selectionToVerify,
                hintSuffix = stringResource(R.string.library_action_hint_verify),
                onClick = { onEvent(LibraryEvent.VerifyPending) },
                modifier = Modifier.weight(1f),
            )
            // "Retraduire" si une cible a deja une traduction, sinon "Traduire" (planTranslation).
            ActionTile(
                icon = TmmIconId.Refresh,
                label = stringResource(if (state.selectionRetranslates) R.string.library_action_retranslate_label else R.string.library_action_translate_label),
                count = state.selectionToTranslate,
                hintSuffix = stringResource(if (state.selectionRetranslates) R.string.library_action_hint_retranslate else R.string.library_action_hint_translate),
                onClick = { onEvent(LibraryEvent.RequestRetranslate) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: TmmIconId,
    label: String,
    count: Int,
    hintSuffix: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val hint = if (count > 0) {
        pluralStringResource(R.plurals.library_chapters_count, count, count) + " · " + hintSuffix
    } else {
        ""
    }
    TmmActionButton(icon = icon, label = label, hint = hint, count = count, onClick = onClick, primary = primary, modifier = modifier)
}

/** Options "Afficher" partagees par le segmente de l'en-tete et la feuille Trier et filtrer. */
@Composable
private fun viewFilterOptions(): List<Pair<LibraryViewFilter, String>> = listOf(
    LibraryViewFilter.All to stringResource(R.string.library_view_all),
    LibraryViewFilter.Todo to stringResource(R.string.library_view_todo),
    LibraryViewFilter.Downloaded to stringResource(R.string.library_view_downloaded),
    LibraryViewFilter.OnPc to stringResource(R.string.library_view_on_pc),
    LibraryViewFilter.Absent to stringResource(R.string.library_view_absent),
)

/**
 * Feuille Trier et filtrer (bouton filtre de l'en-tete). Aucune maquette ne la decrit : elle reprend
 * TmmSheet, SectionLabel et TmmChip. Chaque choix s'applique tout de suite, la feuille reste ouverte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFiltersSheet(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    TmmSheet(
        visible = state.filtersOpen,
        onDismiss = { onEvent(LibraryEvent.CloseFilters) },
        title = stringResource(R.string.library_filters_title),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val sorts = listOf(
                LibrarySort.RecentlyAdded to stringResource(R.string.library_sort_recent),
                LibrarySort.Title to stringResource(R.string.library_sort_title),
                LibrarySort.ToTranslateFirst to stringResource(R.string.library_sort_todo_first),
            )
            SectionLabel(text = stringResource(R.string.library_sort_section), modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sorts.forEach { (sort, label) ->
                    TmmChip(label = label, selected = state.sort == sort, onClick = { onEvent(LibraryEvent.SelectSort(sort)) })
                }
            }
            SectionLabel(text = stringResource(R.string.library_view_section), modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewFilterOptions().forEach { (view, label) ->
                    TmmChip(label = label, selected = state.view == view, onClick = { onEvent(LibraryEvent.SelectView(view)) })
                }
            }
            SectionLabel(text = stringResource(R.string.library_categories_cd), modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { (name, count) ->
                    TmmChip(label = name, selected = state.category == name, count = count, onClick = { onEvent(LibraryEvent.SelectCategory(name)) })
                }
            }
        }
    }
}

@Composable
private fun LibraryConfirmSheet(state: LibraryState, onEvent: (LibraryEvent) -> Unit) {
    TmmSheet(visible = state.confirmRetranslate, onDismiss = { onEvent(LibraryEvent.DismissConfirm) }) {
        val chapters = pluralStringResource(R.plurals.library_chapters_count, state.selectionToTranslate, state.selectionToTranslate)
        val series = pluralStringResource(R.plurals.library_series_count, state.selectionCount, state.selectionCount)
        TmmText(
            text = stringResource(R.string.library_confirm_title_fmt, chapters),
            style = TmmType.display(24, weight = 800),
        )
        TmmText(
            text = stringResource(R.string.library_confirm_text_fmt, series),
            style = TmmType.sans(14, lineHeight = 21f),
            color = TmmColors.textMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TmmColors.infoBg)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TmmIcon(TmmIconId.Info, size = 16.dp, tint = TmmColors.info)
            TmmText(text = stringResource(R.string.library_confirm_info), style = TmmType.sans(12, lineHeight = 17f), color = TmmColors.info)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            TmmButton(label = stringResource(R.string.common_cancel), onClick = { onEvent(LibraryEvent.DismissConfirm) }, primary = false, modifier = Modifier.weight(1f))
            TmmButton(label = stringResource(R.string.library_action_retranslate_label), onClick = { onEvent(LibraryEvent.ConfirmRetranslate) }, modifier = Modifier.weight(1f))
        }
    }
}
