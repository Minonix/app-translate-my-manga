package com.tmm.mobile.ui.screens.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.mihon.localFolderKey
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.V2ActionRow
import com.tmm.mobile.ui.components.V2IconButton
import com.tmm.mobile.ui.components.V2LazySheet
import com.tmm.mobile.ui.components.V2SheetHeader
import com.tmm.mobile.ui.components.v2CardItem
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.screens.translate.SearchField
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

private val C = TmmColors.V2

/**
 * Feuille « Lier a une autre serie » (aucune maquette : composants de la feuille « Masquer »). Liste
 * paresseuse : local/ peut compter des centaines de dossiers. Les suggestions ne sont qu'affichees en tete.
 */
@Composable
internal fun LinkSeriesSheet(sheet: SeriesSheet.LinkSeries?, onEvent: (SeriesEvent) -> Unit) {
    val title = stringResource(R.string.series_link_series_label)
    val close = { onEvent(SeriesEvent.CloseSheet) }
    // Recherche propre a chaque ouverture de la feuille.
    var query by rememberSaveable(sheet != null) { mutableStateOf("") }
    val needle = localFolderKey(query)
    val folders = sheet?.folders.orEmpty().let { all -> if (needle.isEmpty()) all else all.filter { needle in localFolderKey(it) } }
    V2LazySheet(visible = sheet != null, onDismiss = close, paneTitle = title) {
        if (sheet == null) return@V2LazySheet
        item(key = "header") {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { V2SheetHeader(kick = stringResource(R.string.series_link_sheet_kick), title = title) }
                V2IconButton(TmmIconId.V2X, stringResource(R.string.series_link_close_cd), close)
            }
            TmmText(
                text = stringResource(R.string.series_link_sheet_body),
                style = TmmType.sans(14),
                color = C.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        sheet.linked?.let { linked ->
            item(key = "unlink") {
                V2ActionRow(
                    icon = TmmIconId.V2X,
                    title = stringResource(R.string.series_link_unlink_label),
                    description = stringResource(R.string.series_link_unlink_hint_fmt, linked),
                    onClick = { onEvent(SeriesEvent.Unlink) },
                    modifier = Modifier.padding(top = 6.dp),
                    danger = true,
                )
            }
        }
        if (sheet.suggestions.isNotEmpty() && needle.isEmpty()) {
            item(key = "suggestions-title") { SectionTitle(stringResource(R.string.series_link_suggestions_title)) }
            sheet.suggestions.forEachIndexed { i, name ->
                item(key = "s:$name", contentType = "folder") {
                    FolderRow(name, name == sheet.linked, Modifier.v2CardItem(isFirst = i == 0, isLast = i == sheet.suggestions.lastIndex)) {
                        onEvent(SeriesEvent.PickLinkFolder(name))
                    }
                }
            }
        }
        item(key = "all-title") { SectionTitle(stringResource(R.string.series_link_all_title)) }
        item(key = "search") {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.series_link_search),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (folders.isEmpty()) {
            item(key = "empty") { TmmText(text = stringResource(R.string.series_link_empty), style = TmmType.sans(14), color = C.muted) }
        }
        folders.forEachIndexed { i, name ->
            item(key = "f:$name", contentType = "folder") {
                FolderRow(name, name == sheet.linked, Modifier.v2CardItem(isFirst = i == 0, isLast = i == folders.lastIndex)) {
                    onEvent(SeriesEvent.PickLinkFolder(name))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    TmmText(
        text = text.uppercase(),
        style = TmmType.sans(12, weight = 600, letterSpacingEm = 1.3f / 12f),
        color = C.muted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun FolderRow(name: String, linked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TmmIcon(TmmIconId.V2Folder, size = 18.dp, tint = C.muted)
        TmmText(text = name, style = TmmType.sans(15), color = C.ink, maxLines = 1, modifier = Modifier.weight(1f))
        if (linked) TmmIcon(TmmIconId.V2Check, size = 16.dp, tint = C.green)
    }
}
