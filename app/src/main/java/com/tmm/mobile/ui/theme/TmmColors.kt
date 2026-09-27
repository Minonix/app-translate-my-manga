package com.tmm.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette fermee de l'application, reproduite depuis les maquettes (maquettes).
 * Aucune autre couleur ne doit apparaitre ailleurs dans le code (voir NoRawColorTest).
 */
object TmmColors {
    // Constantes brutes (une par teinte utilisee dans les maquettes)
    val C5E5A52 = Color(0xFF5E5A52)
    val C16161A = Color(0xFF16161A)
    val CFFFFFF = Color(0xFFFFFFFF)
    val CC8391F = Color(0xFFC8391F)
    val CF4F1EA = Color(0xFFF4F1EA)
    val CDDD6C8 = Color(0xFFDDD6C8)
    val CECE7DC = Color(0xFFECE7DC)
    val C2B4C7E = Color(0xFF2B4C7E)
    val CD5CDBC = Color(0xFFD5CDBC)
    val C2F7D4F = Color(0xFF2F7D4F)
    val C7A2210 = Color(0xFF7A2210)
    val CCFC8B8 = Color(0xFFCFC8B8)
    val CFBF9F4 = Color(0xFFFBF9F4)
    val CFBE3DC = Color(0xFFFBE3DC)
    val CE9E4D8 = Color(0xFFE9E4D8)
    val CE3E9F2 = Color(0xFFE3E9F2)
    val C9E2A14 = Color(0xFF9E2A14)
    val C2E2D29 = Color(0xFF2E2D29)
    val CD8D2E4 = Color(0xFFD8D2E4)
    val CCFD8C6 = Color(0xFFCFD8C6)
    val C9C9486 = Color(0xFF9C9486)
    val CE2C6BB = Color(0xFFE2C6BB)
    val CE6DCC0 = Color(0xFFE6DCC0)
    val CC9D3DE = Color(0xFFC9D3DE)
    val CE8F1EB = Color(0xFFE8F1EB)
    val CB9B0A0 = Color(0xFFB9B0A0)
    val C9BD3AE = Color(0xFF9BD3AE)
    val CF4B3A3 = Color(0xFFF4B3A3)
    val CA9A293 = Color(0xFFA9A293)
    val CDCCFC4 = Color(0xFFDCCFC4)
    val C3F6B50 = Color(0xFF3F6B50)
    val CFBF1D6 = Color(0xFFFBF1D6)
    val CE4DED0 = Color(0xFFE4DED0)
    val C8A5A00 = Color(0xFF8A5A00)
    val C3A3934 = Color(0xFF3A3934)

    // Variantes rgba (nommees par teinte de base + opacite en %)
    val Green0 = C2F7D4F.copy(alpha = 0f)
    val Green55 = C2F7D4F.copy(alpha = .55f)
    val White85 = CFFFFFF.copy(alpha = .85f)
    val White55 = CFFFFFF.copy(alpha = .55f)
    val White22 = CFFFFFF.copy(alpha = .22f)
    val White18 = CFFFFFF.copy(alpha = .18f)
    val Ink06 = C16161A.copy(alpha = .06f)
    val Ink08 = C16161A.copy(alpha = .08f)
    val Ink12 = C16161A.copy(alpha = .12f)
    val Ink20 = C16161A.copy(alpha = .20f)
    val Ink25 = C16161A.copy(alpha = .25f)
    val Ink28 = C16161A.copy(alpha = .28f)
    val Ink45 = C16161A.copy(alpha = .45f)

    // Alias semantiques (CdC 10.1)
    val ink = C16161A
    val paper = CF4F1EA
    val paperRaised = CFBF9F4
    val surface = CFFFFFF
    val line = CECE7DC
    val lineStrong = CDDD6C8
    val track = CD5CDBC
    val textMuted = C5E5A52
    val textOnInkMuted = CCFC8B8
    val textOnInkMuted2 = CE4DED0
    val accent = CC8391F
    val accentPressed = C9E2A14
    val dangerBg = CFBE3DC
    val dangerText = C7A2210
    val info = C2B4C7E
    val infoBg = CE3E9F2
    val success = C2F7D4F
    val absent = CE6DCC0
    val absentText = C8A5A00

    /** Palette de vignettes placeholder pour les couvertures de series sans image. */
    val placeholderPalette = listOf(CC9D3DE, CE2C6BB, CCFD8C6, CD8D2E4, CE6DCC0, CDCCFC4)

    fun placeholderFor(seriesKey: String): Color =
        placeholderPalette[(seriesKey.hashCode() and 0x7fffffff) % placeholderPalette.size]

    /** Couleurs de tuile de MockupData.series (Library.dc.html), indexees par leur hex de maquette. */
    private val byHex: Map<String, Color> = mapOf(
        "#D8D2E4" to CD8D2E4,
        "#C9D3DE" to CC9D3DE,
        "#E2C6BB" to CE2C6BB,
        "#CFD8C6" to CCFD8C6,
        "#F4F1EA" to CF4F1EA,
        "#E6DCC0" to CE6DCC0,
        "#DCCFC4" to CDCCFC4,
    )

    fun fromHex(hex: String): Color =
        byHex[hex] ?: error("Couleur absente de la palette TmmColors : $hex")

    /** Comme fromHex, mais retombe sur placeholderFor(seriesKey) pour une serie reelle (E10),
     * dont le hex n'a pas de raison d'appartenir a la palette figee des maquettes. */
    fun fromHexOrPlaceholder(hex: String, seriesKey: String): Color =
        byHex[hex] ?: placeholderFor(seriesKey)
}
