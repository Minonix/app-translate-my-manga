package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.pressScale

/** Bandeau d'etat reseau/session (Offline.dc.html) : 3 variantes fixes de l'app. */
sealed class OfflineBannerState {
    data object NoNetwork : OfflineBannerState()
    data class TokenExpired(val reconnecting: Boolean, val onReconnect: () -> Unit) : OfflineBannerState()
    data object ProtocolTooNew : OfflineBannerState()
}

@Composable
fun OfflineBanner(state: OfflineBannerState, modifier: Modifier = Modifier) {
    when (state) {
        is OfflineBannerState.NoNetwork -> BannerRow(
            modifier = modifier,
            bg = TmmColors.ink,
            fg = TmmColors.paper,
            liveMode = LiveRegionMode.Polite,
            icon = TmmIconId.WifiOff,
        ) {
            RichText(bold = "Pas de connexion.", rest = " Les données affichées peuvent être obsolètes.", color = TmmColors.paper)
        }

        is OfflineBannerState.ProtocolTooNew -> BannerRow(
            modifier = modifier,
            bg = TmmColors.ink,
            fg = TmmColors.paper,
            liveMode = LiveRegionMode.Polite,
            icon = TmmIconId.WifiOff,
        ) {
            TmmText(
                text = "Le PC utilise une version plus récente du protocole. Mettez à jour l'app.",
                style = TmmType.sans(13),
                color = TmmColors.paper,
                modifier = Modifier.weight(1f),
            )
        }

        is OfflineBannerState.TokenExpired -> BannerRow(
            modifier = modifier,
            bg = TmmColors.dangerBg,
            fg = TmmColors.dangerText,
            liveMode = LiveRegionMode.Assertive,
            icon = TmmIconId.Lock,
        ) {
            RichText(bold = "Accès à Google Drive expiré", rest = ".", color = TmmColors.dangerText, modifier = Modifier.weight(1f))
            ReconnectButton(reconnecting = state.reconnecting, onClick = state.onReconnect)
        }
    }
}

@Composable
private fun BannerRow(
    modifier: Modifier,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    liveMode: LiveRegionMode,
    icon: TmmIconId,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = liveMode },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmIcon(icon, size = 18.dp, tint = fg)
        content()
    }
}

@Composable
private fun ReconnectButton(reconnecting: Boolean, onClick: () -> Unit) {
    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(TmmColors.dangerText)
            .pressScale(interactionSource)
            .clickable(
                enabled = !reconnecting,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        TmmText(
            text = if (reconnecting) "Ouverture..." else "Reconnecter",
            style = TmmType.sans(13, weight = 600),
            color = TmmColors.surface,
        )
    }
}

@Composable
private fun RichText(bold: String, rest: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) { append(bold) }
        append(rest)
    }
    androidx.compose.foundation.text.BasicText(
        text = annotated,
        style = TmmType.sans(13).copy(color = color),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun OfflineBannerPreview() {
    TmmTheme {
        androidx.compose.foundation.layout.Column {
            OfflineBanner(OfflineBannerState.NoNetwork)
            OfflineBanner(OfflineBannerState.TokenExpired(reconnecting = false, onReconnect = {}))
        }
    }
}
