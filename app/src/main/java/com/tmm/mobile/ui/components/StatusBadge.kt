package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

/**
 * Ton d'etat d'une serie ou d'un chapitre, table `tone` de Library.dc.html
 * (kind -> [bg, fg]) completee par le kind `gap` de Series.dc.html.
 */
enum class StatusTone(val background: Color, val foreground: Color) {
    Send(TmmColors.dangerBg, TmmColors.dangerText),
    Busy(TmmColors.ink, TmmColors.paper),
    Ok(TmmColors.surface, TmmColors.success),
    Absent(TmmColors.surface, TmmColors.textMuted),
    Get(TmmColors.infoBg, TmmColors.info),
    Gap(TmmColors.dangerBg, TmmColors.dangerText),
}

/** Badge d'etat des tuiles de la Bibliotheque : pastille + libelle, fond/texte du ton. */
@Composable
fun StatusBadge(tone: StatusTone, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(tone.background)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(color = tone.foreground)
        TmmText(text = label, style = TmmType.sans(10, weight = 600, lineHeight = 12f), color = tone.foreground)
    }
}

@Composable
private fun Box(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Preview(showBackground = true)
@Composable
private fun StatusBadgePreview() {
    TmmTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(StatusTone.Send, "1 à envoyer")
            StatusBadge(StatusTone.Busy, "Traduction en cours")
            StatusBadge(StatusTone.Get, "2 à récupérer")
            StatusBadge(StatusTone.Ok, "À jour")
            StatusBadge(StatusTone.Absent, "Non téléchargée")
        }
    }
}
