package com.tmm.mobile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Courbes d'animation nommees, reprises des keyframes CSS des maquettes
 * (maquettes, balise <style>).
 */
object Motion {
    /** cubic-bezier(.2,.8,.2,1) : rise/pop/toast/pill/sheet/slide/grow */
    val TmmEase: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** CSS "ease" standard : cubic-bezier(.25,.1,.25,1) */
    val CssEase: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** CSS "ease-out" standard : cubic-bezier(0,0,.58,1) */
    val CssEaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** CSS "ease-in-out" standard : cubic-bezier(.42,0,.58,1) */
    val CssEaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    val Linear: Easing = LinearEasing

    // ponytail: seules les primitives generiques (press, halftone) sont
    // fournies ici. Les animations propres a un composant (toast, pill, sheet,
    // spin, pulse, stripes, shimmer, float, blink, travel, anneau du bouton
    // refresh, compteurs animes) seront ajoutees composant par composant en E3/E4,
    // au moment ou l'ecran ou le composant qui les utilise sera ecrit -
    // les construire maintenant sans consommateur serait de la sur-ingenierie.
}

/** Equivalent de la classe CSS '.press' : scale(.97) pendant l'appui, sans ripple. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150, easing = Motion.CssEase),
        label = "pressScale",
    )
    return this.scale(scale)
}

/** Motif de points (halftone) utilise en fond de certaines cartes ('.tone'). */
fun Modifier.halftone(color: Color = TmmColors.Ink28, step: androidx.compose.ui.unit.Dp = 5.dp): Modifier = drawBehind {
    val stepPx = step.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawCircle(color = color, radius = 1f, center = Offset(x, y))
            x += stepPx
        }
        y += stepPx
    }
}
