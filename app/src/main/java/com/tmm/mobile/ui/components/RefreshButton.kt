package com.tmm.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.core.LocalReducedMotion
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import kotlinx.coroutines.delay

private const val SpinDurationMs = 1300
private const val CooldownSeconds = 30
private const val RingRadiusDp = 22
private const val RingStrokeWidthDp = 3
private const val RingDashLength = 138f

/**
 * Bouton de rafraichissement de Main.dc.html : appui -> icone qui tourne 1.3s ->
 * anneau de recharge 30s (bouton desactive) -> disponible.
 */
@Composable
fun RefreshButton(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onSpinComplete: () -> Unit = {},
) {
    var spinning by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(spinning) {
        if (spinning) {
            delay(SpinDurationMs.toLong())
            spinning = false
            cooldown = CooldownSeconds
            onSpinComplete()
        }
    }
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    val disabled = spinning || cooldown > 0
    val description = if (cooldown > 0) {
        stringResource(R.string.common_refresh_cooldown_cd_fmt, cooldown)
    } else {
        stringResource(R.string.common_refresh_cd)
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = !disabled) {
                spinning = true
                onRefresh()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (cooldown > 0) {
            CooldownRing(cooldown = cooldown)
        }
        if (spinning && !reducedMotion) {
            val infinite = rememberInfiniteTransition(label = "refreshSpin")
            val angle by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "angle",
            )
            TmmIcon(TmmIconId.Refresh, tint = TmmColors.ink, modifier = Modifier.rotate(angle))
        } else if (cooldown == 0) {
            TmmIcon(TmmIconId.Refresh, tint = TmmColors.ink)
        }
    }
}

@Composable
private fun CooldownRing(cooldown: Int) {
    val progress = 1f - (cooldown / CooldownSeconds.toFloat())
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size((RingRadiusDp * 2).dp)) {
            val strokeWidthPx = RingStrokeWidthDp.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = TmmColors.line,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
            drawArc(
                color = TmmColors.accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }
        TmmText(text = "${cooldown}s", style = com.tmm.mobile.ui.theme.TmmType.mono(12), color = TmmColors.textMuted)
    }
}

@Composable
private fun TmmIcon(icon: TmmIconId, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    com.tmm.mobile.ui.icons.TmmIcon(icon = icon, tint = tint, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun RefreshButtonPreview() {
    TmmTheme { RefreshButton(onRefresh = {}) }
}
