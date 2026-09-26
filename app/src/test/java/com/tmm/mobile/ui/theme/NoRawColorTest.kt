package com.tmm.mobile.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aucune couleur brute hors de ui/theme/ : toutes les couleurs viennent de TmmColors
 * (voir PLAN_GEMINI.md section E2).
 */
class NoRawColorTest {
    private val rawColorPattern = Regex("""Color\(0x|Color\(red|Color\(\s*\d""")

    @Test
    fun noFileOutsideThemePackageDeclaresRawColor() {
        val srcRoot = File("src/main/java")
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.replace('\\', '/').contains("/ui/theme/") }
            .filter { rawColorPattern.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()

        assertTrue("Couleurs brutes trouvees hors de ui/theme/ : $offenders", offenders.isEmpty())
    }
}
