package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

/**
 * Bascule de vue (`role=radiogroup`), Library.dc.html. Jusqu'a 3 segments ils se partagent la largeur ;
 * au-dela (filtres de la Bibliotheque) ils prennent leur largeur naturelle et la rangee defile.
 */
@Composable
fun <T> TmmSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrolls = options.size > 3
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TmmColors.line)
            .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(if (scrolls) 4.dp else 0.dp),
    ) {
        options.forEach { (value, label) ->
            val isOn = value == selected
            Row(
                modifier = Modifier
                    .then(if (scrolls) Modifier else Modifier.weight(1f))
                    .height(36.dp)
                    .then(if (isOn) Modifier.shadow(1.dp, RoundedCornerShape(9.dp)) else Modifier)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isOn) TmmColors.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = isOn, onClick = { onSelect(value) }, role = Role.RadioButton)
                    .then(if (scrolls) Modifier.padding(horizontal = 14.dp) else Modifier),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmText(
                    text = label,
                    style = TmmType.sans(13, weight = 600),
                    color = if (isOn) TmmColors.ink else TmmColors.textMuted,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmSegmentedPreview() {
    TmmTheme {
        TmmSegmented(
            options = listOf("all" to "Toutes", "todo" to "À traduire", "absent" to "Non téléchargées"),
            selected = "all",
            onSelect = {},
        )
    }
}
