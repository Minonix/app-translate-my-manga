package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.pressScale

/** Bouton plein-largeur (confirmations de TmmSheet/TmmDialog, Library.dc.html). */
@Composable
fun TmmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val bg = if (primary) TmmColors.accent else TmmColors.surface
    val fg = if (primary) TmmColors.surface else TmmColors.ink
    val border = if (primary) null else TmmColors.track
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(12.dp)) else Modifier)
            .pressScale(interactionSource)
            .selectable(selected = false, enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .alpha(if (enabled) 1f else .45f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmText(text = label, style = TmmType.sans(15, weight = 600), color = fg)
    }
}

/**
 * Tuile de la grille d'actions 2x2 (barre de selection de Library.dc.html) :
 * icone dans pastille, libelle + sous-texte, desactivee quand `count == 0`.
 */
@Composable
fun TmmActionButton(
    icon: TmmIconId,
    label: String,
    hint: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val active = count > 0
    val bg = if (primary && active) TmmColors.accent else TmmColors.surface
    val fg = if (primary && active) TmmColors.surface else TmmColors.ink
    val border = if (primary && active) TmmColors.accent else TmmColors.track
    val iconBg = if (primary && active) TmmColors.White18 else TmmColors.paper
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .pressScale(interactionSource)
            .selectable(selected = false, enabled = active, interactionSource = interactionSource, indication = null, onClick = onClick)
            .alpha(if (active) 1f else .45f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TmmIcon(icon, size = 16.dp, tint = fg)
        }
        androidx.compose.foundation.layout.Column {
            TmmText(text = label, style = TmmType.sans(14, weight = 600), color = fg)
            TmmText(text = if (active) hint else "Non applicable", style = TmmType.sans(11), color = fg.copy(alpha = .8f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TmmButtonPreview() {
    TmmTheme {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TmmButton(label = "Retraduire", onClick = {})
            TmmButton(label = "Annuler", onClick = {}, primary = false)
            TmmActionButton(TmmIconId.Up, "Envoyer au PC", "absents du PC", count = 3, onClick = {}, primary = true)
        }
    }
}
