package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme

/**
 * Case a cocher ronde en surimpression de tuile (Library.dc.html, mode selection) :
 * 26x26, contour 2px, coche blanche quand cochee.
 */
@Composable
fun TmmCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (checked) TmmColors.accent else TmmColors.C9C9486
    val bg = if (checked) TmmColors.accent else TmmColors.White85
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, border, CircleShape)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            TmmIcon(TmmIconId.CheckSimple, size = 14.dp, tint = TmmColors.surface, strokeWidth = 3f)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmCheckboxPreview() {
    TmmTheme {
        androidx.compose.foundation.layout.Row {
            TmmCheckbox(checked = true, onCheckedChange = {})
            TmmCheckbox(checked = false, onCheckedChange = {})
        }
    }
}
