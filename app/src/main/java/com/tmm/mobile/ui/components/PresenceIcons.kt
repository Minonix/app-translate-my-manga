package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme

/**
 * Trois pastilles 8x8 (Téléphone/PC/Français) de Series.dc.html : couleur pleine
 * si present, contour tirete transparent sinon.
 */
@Composable
fun PresenceIcons(phone: Boolean, pc: Boolean, fr: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PresenceDot(present = phone, color = TmmColors.info, label = "Téléphone")
        PresenceDot(present = pc, color = TmmColors.textMuted, label = "PC")
        PresenceDot(present = fr, color = TmmColors.accent, label = "Français")
    }
}

@Composable
private fun PresenceDot(present: Boolean, color: Color, label: String) {
    val description = if (present) label else "$label absent"
    if (present) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
                .semantics { contentDescription = description },
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .dashedBorder(color = TmmColors.CB9B0A0, width = 1.dp, cornerRadius = 2.dp)
                .semantics { contentDescription = description },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PresenceIconsPreview() {
    TmmTheme { PresenceIcons(phone = true, pc = false, fr = true) }
}
