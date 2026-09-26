package com.tmm.mobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tmm.mobile.R

/**
 * Styles de texte de l'application. Les ecrans ne doivent jamais appeler
 * TextStyle(...) directement : ils passent par sans()/display()/mono().
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object TmmType {
    private val sansCache = HashMap<Pair<Int, Int>, FontFamily>()
    private val displayCache = HashMap<Pair<Int, Int>, FontFamily>()

    // ponytail: seul le poids 500 (Medium) d'IBM Plex Mono a ete telecharge car
    // c'est le seul utilise par les maquettes (classe .m). Ajouter d'autres
    // fichiers statiques si un ecran a besoin d'un autre poids en mono.
    private val monoFamily = FontFamily(
        Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
    )

    private fun sansFamily(weight: Int, size: Int): FontFamily =
        sansCache.getOrPut(weight to size) {
            FontFamily(
                Font(
                    R.font.ibm_plex_sans_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight),
                        FontVariation.width(100f),
                    ),
                ),
            )
        }

    private fun displayFamily(weight: Int, size: Int): FontFamily =
        displayCache.getOrPut(weight to size) {
            FontFamily(
                Font(
                    R.font.bricolage_grotesque_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight),
                        FontVariation.Setting("opsz", size.coerceIn(12, 96).toFloat()),
                    ),
                ),
            )
        }

    private fun style(
        family: FontFamily,
        weight: Int,
        size: Int,
        lineHeight: Float?,
        letterSpacingEm: Float?,
    ): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
        letterSpacing = letterSpacingEm?.let { (it * size).sp } ?: TextUnit.Unspecified,
    )

    fun sans(size: Int, weight: Int = 400, lineHeight: Float? = null, letterSpacingEm: Float? = null): TextStyle =
        style(sansFamily(weight, size), weight, size, lineHeight, letterSpacingEm)

    fun display(size: Int, weight: Int = 800, lineHeight: Float? = null, letterSpacingEm: Float? = null): TextStyle =
        style(displayFamily(weight, size), weight, size, lineHeight, letterSpacingEm)

    fun mono(size: Int, weight: Int = 500, lineHeight: Float? = null, letterSpacingEm: Float? = null): TextStyle =
        style(monoFamily, weight, size, lineHeight, letterSpacingEm)
}
