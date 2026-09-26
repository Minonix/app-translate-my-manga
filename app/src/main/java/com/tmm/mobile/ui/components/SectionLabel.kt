package com.tmm.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import java.util.Locale

/** Titre de section en petites majuscules espacées (ex. Settings.dc.html `g.title`). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    TmmText(
        text = text.uppercase(Locale.FRENCH),
        modifier = modifier,
        style = TmmType.sans(12, weight = 600, letterSpacingEm = 0.06f),
        color = TmmColors.textMuted,
    )
}

@Preview(showBackground = true)
@Composable
private fun SectionLabelPreview() {
    TmmTheme { SectionLabel("En cours maintenant") }
}
