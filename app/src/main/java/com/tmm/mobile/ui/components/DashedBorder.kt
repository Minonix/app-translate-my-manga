package com.tmm.mobile.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bordure en tirets, equivalent de `border: Npx dashed #couleur` des maquettes
 * (tuiles "absent", encarts vides...). Compose n'a pas de dash-array natif sur
 * `Modifier.border`, d'ou ce dessin manuel via `Stroke.pathEffect`.
 */
fun Modifier.dashedBorder(
    color: Color,
    width: Dp = 1.5.dp,
    cornerRadius: Dp = 8.dp,
    dashLength: Dp = 4.dp,
    gapLength: Dp = 3.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = androidx.compose.ui.graphics.Path().apply {
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(outline.roundRect)
            is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(outline.rect)
            is androidx.compose.ui.graphics.Outline.Generic -> addPath(outline.path)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx())),
        ),
    )
}
