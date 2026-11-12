package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.data.status.ErrorInfo
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

/**
 * Erreur bloquante cote PC (cle refusee, credit epuise, disque plein...) : affichee en tete de l'accueil
 * et de l'activite tant qu'aucun job n'a abouti depuis, pour ne pas la decouvrir echec par echec.
 */
@Composable
fun BlockingNotice(info: ErrorInfo, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val hint = when (info.code) {
        "api_key", "provider_quota" -> R.string.blocking_hint_key
        else -> R.string.blocking_hint_other
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TmmColors.dangerBg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TmmIcon(TmmIconId.Warning, size = 18.dp, tint = TmmColors.dangerText)
        Column {
            TmmText(
                text = stringResource(R.string.blocking_title_fmt, stringResource(info.label)),
                style = TmmType.sans(13, weight = 600),
                color = TmmColors.dangerText,
            )
            TmmText(text = stringResource(hint), style = TmmType.sans(13, lineHeight = 19f), color = TmmColors.dangerText)
        }
    }
}
