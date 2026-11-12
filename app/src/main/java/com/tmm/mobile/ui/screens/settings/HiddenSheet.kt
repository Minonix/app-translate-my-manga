package com.tmm.mobile.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.V2Check
import com.tmm.mobile.ui.components.V2IconButton
import com.tmm.mobile.ui.components.V2LazySheet
import com.tmm.mobile.ui.components.V2SheetHeader
import com.tmm.mobile.ui.components.v2CardItem
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.screens.translate.SearchField
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

private val C = TmmColors.V2

/** Feuille « Masquer » : categories Mihon puis series (liste paresseuse, 2000 series possibles). */
@Composable
internal fun HiddenSheet(hidden: HiddenChoice, onEvent: (SettingsEvent) -> Unit) {
    val title = stringResource(R.string.settings_hidden_title)
    val close = { onEvent(SettingsEvent.CloseHidden) }
    val series = hidden.filteredSeries
    V2LazySheet(visible = hidden.open, onDismiss = close, paneTitle = title) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { V2SheetHeader(kick = stringResource(R.string.settings_hidden_kick), title = title) }
                V2IconButton(TmmIconId.V2X, stringResource(R.string.settings_hidden_close_cd), close)
            }
            TmmText(
                text = stringResource(R.string.settings_hidden_hint),
                style = TmmType.sans(14),
                color = C.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item(key = "cats-title") { SectionTitle(stringResource(R.string.settings_hidden_categories_title)) }
        if (hidden.allCategories.isEmpty()) {
            item(key = "cats-empty") {
                TmmText(text = stringResource(R.string.settings_hidden_no_category), style = TmmType.sans(14), color = C.muted)
            }
        }
        hidden.allCategories.forEachIndexed { i, name ->
            item(key = "c:$name") {
                CheckRow(
                    label = name,
                    checked = name in hidden.categories,
                    onClick = { onEvent(SettingsEvent.ToggleHiddenCategory(name)) },
                    modifier = Modifier.v2CardItem(isFirst = i == 0, isLast = i == hidden.allCategories.lastIndex),
                )
            }
        }
        item(key = "series-title") { SectionTitle(stringResource(R.string.settings_hidden_series_title)) }
        item(key = "search") {
            SearchField(
                value = hidden.query,
                onValueChange = { onEvent(SettingsEvent.HiddenQueryChanged(it)) },
                placeholder = stringResource(R.string.settings_hidden_search),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        series.forEachIndexed { i, s ->
            item(key = "s:" + s.key, contentType = "series") {
                CheckRow(
                    label = s.title,
                    checked = s.key in hidden.series,
                    onClick = { onEvent(SettingsEvent.ToggleHiddenSeries(s.key)) },
                    modifier = Modifier.v2CardItem(isFirst = i == 0, isLast = i == series.lastIndex),
                )
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
private fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        V2Check(checked = checked)
        TmmText(text = label, style = TmmType.sans(15), color = C.ink, maxLines = 1, modifier = Modifier.weight(1f))
    }
}
