package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

/** Un onglet de la barre de navigation principale (nav des 13 maquettes). */
data class TmmBottomBarItem(val icon: TmmIconId, val label: String, val badgeCount: Int? = null)

/** Barre de navigation basse, 5 onglets, pastille noire sur l'onglet actif. */
@Composable
fun TmmBottomBar(
    items: List<TmmBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TmmColors.lineStrong),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .background(TmmColors.paperRaised)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(index) },
                            role = Role.Tab,
                            indication = null,
                            interactionSource = interactionSource,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 52.dp, minHeight = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) TmmColors.ink else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmIcon(item.icon, size = 20.dp, tint = if (selected) TmmColors.paper else TmmColors.textMuted)
                        if (item.badgeCount != null && item.badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                    .clip(CircleShape)
                                    .background(TmmColors.accent)
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TmmText(text = "${item.badgeCount}", style = TmmType.mono(10), color = TmmColors.surface)
                            }
                        }
                    }
                    TmmText(
                        text = item.label,
                        style = TmmType.sans(11, weight = if (selected) 600 else 500),
                        color = if (selected) TmmColors.ink else TmmColors.textMuted,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmBottomBarPreview() {
    TmmTheme {
        TmmBottomBar(
            items = listOf(
                TmmBottomBarItem(TmmIconId.Home, "Accueil"),
                TmmBottomBarItem(TmmIconId.Grid, "Bibliothèque"),
                TmmBottomBarItem(TmmIconId.Sync, "Transferts", badgeCount = 20),
                TmmBottomBarItem(TmmIconId.List, "File"),
                TmmBottomBarItem(TmmIconId.Inbox, "Catalogue"),
            ),
            selectedIndex = 1,
            onSelect = {},
        )
    }
}
