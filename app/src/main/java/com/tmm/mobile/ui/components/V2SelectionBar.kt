package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

/**
 * Barre de selection commune (fiche serie et Bibliotheque, ecran 4 de la maquette v2) : absente de
 * la maquette, qui montre directement la feuille « Que faire ? », ajoutee pour l'ouvrir une fois
 * la selection faite. [label] porte le compte deja mis en forme (chapitres ou series).
 */
@Composable
fun V2SelectionBar(label: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TmmColors.V2.ink)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmText(
            text = label,
            style = TmmType.sans(15, weight = 600),
            color = TmmColors.V2.white,
            modifier = Modifier.weight(1f),
        )
        V2Button(
            label = actionLabel,
            onClick = onAction,
            size = V2ButtonSize.Small,
        )
    }
}
