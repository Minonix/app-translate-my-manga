package com.tmm.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
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
                colors += Color((0xFF shl 24) or rgb)
            }
            rgbaPattern.findAll(text).forEach { match ->
                val (r, g, b, a) = match.destructured
                val alpha = if (a.isEmpty()) 1f else a.toFloat()
                colors += Color(red = r.toInt() / 255f, green = g.toInt() / 255f, blue = b.toInt() / 255f, alpha = alpha)
            }
        }
        return colors
    }

    private fun themeColors(): List<Color> =
        TmmColors.javaClass.declaredFields
            .filter { it.type == Color::class.java }
            .onEach { it.isAccessible = true }
            .map { it.get(TmmColors) as Color }

    @Test
    fun everyMockupColorExistsInTmmColors() {
        val theme = themeColors()
        val missing = mockupColors().filterNot { mockup ->
            theme.any { it.value == mockup.value }
        }
        assertTrue("Couleurs des maquettes absentes de TmmColors : $missing", missing.isEmpty())
    }
}
