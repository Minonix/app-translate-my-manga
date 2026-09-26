package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

/** Onglet de categorie (`role=tab`), pastille arrondie 36px, Library.dc.html. */
@Composable
fun TmmChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val bg = if (selected) TmmColors.ink else TmmColors.surface
    val fg = if (selected) TmmColors.paper else TmmColors.ink
    val border = if (selected) TmmColors.ink else TmmColors.track
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TmmText(text = label, style = TmmType.sans(13, weight = 600), color = fg)
        if (count != null) {
            TmmText(text = "$count", style = TmmType.mono(11), color = fg.copy(alpha = .7f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmChipPreview() {
    TmmTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TmmChip("Tous", selected = true, onClick = {}, count = 142)
            TmmChip("En cours", selected = false, onClick = {}, count = 24)
        }
    }
}
