package com.tmm.mobile.ui.icons

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aucune icone Material : le catalogue d'icones est TmmIcons (voir PLAN_GEMINI.md section E2). */
class NoMaterialIconsTest {
    private val forbiddenPattern = Regex("""androidx\.compose\.material\.icons|Icons\.""")

    @Test
    fun noFileReferencesMaterialIcons() {
        val srcRoot = File("src/main/java")
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { forbiddenPattern.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()

        assertTrue("References a Icons Material trouvees : $offenders", offenders.isEmpty())
    }
}
