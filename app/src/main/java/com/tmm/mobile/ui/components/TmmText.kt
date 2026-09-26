package com.tmm.mobile.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

/**
 * Remplace le `Text` de Material (non utilise, voir PLAN_GEMINI.md 4.9) : simple
 * enrobage de `BasicText` avec la couleur et le style par defaut de l'application.
 */
@Composable
fun TmmText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TmmType.sans(15),
    color: Color = TmmColors.ink,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: style.textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}
