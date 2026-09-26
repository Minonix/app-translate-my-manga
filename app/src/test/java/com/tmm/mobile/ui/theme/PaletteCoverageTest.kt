package com.tmm.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Toute couleur utilisee dans les maquettes doit exister dans TmmColors
 * (voir PLAN_GEMINI.md section E2).
 */
class PaletteCoverageTest {
    private val hexPattern = Regex("""#[0-9A-Fa-f]{6}\b""")
    private val rgbaPattern = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)""")

    private fun mockupColors(): Set<Color> {
        val mockupDir = File("../maquettes")
        val colors = mutableSetOf<Color>()
        mockupDir.listFiles { f -> f.name.endsWith(".dc.html") }?.forEach { file ->
            val text = file.readText()
            hexPattern.findAll(text).forEach { match ->
                val hex = match.value.removePrefix("#")
                val rgb = hex.toInt(16)
                colors += Color((0xFFL shl 24) or rgb.toLong())
            }
            rgbaPattern.findAll(text).forEach { match ->
                val (r, g, b, a) = match.destructured
                val alpha = if (a.isEmpty()) 1f else a.toFloat()
                val aInt = (alpha * 255f).roundToInt()
                colors += Color((aInt.toLong() shl 24) or (r.toInt().toLong() shl 16) or (g.toInt().toLong() shl 8) or b.toInt().toLong())
            }
        }
        return colors
    }

    private fun themeColors(): List<Color> =
        TmmColors.javaClass.declaredFields
            .filter { it.type == java.lang.Long.TYPE }
            .onEach { it.isAccessible = true }
            .map { Color(it.getLong(TmmColors).toULong()) }

    @Test
    fun everyMockupColorExistsInTmmColors() {
        val themeValues = themeColors().map { it.value }.toSet()
        val missing = mockupColors().filterNot { mockup ->
            themeValues.contains(mockup.value)
        }
        assertTrue("Couleurs des maquettes absentes de TmmColors : $missing", missing.isEmpty())
    }
}
