package com.tmm.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.core.LocalReducedMotion
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme

/**
 * Barre de progression FR d'une serie (`.grow` de Library.dc.html) : piste
 * `track`, remplissage `accent`, croissance animee depuis 0 sauf mouvement reduit.
 */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) tween(0) else tween(1100),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(TmmColors.line),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(TmmColors.accent),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressBarPreview() {
    TmmTheme { ProgressBar(fraction = 0.96f, modifier = Modifier.fillMaxWidth()) }
}
