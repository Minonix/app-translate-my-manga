package com.tmm.mobile.ui.icons

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chaque `d="..."` de `<path>` des maquettes doit apparaitre dans une entree de
 * TmmIconId (voir PLAN_GEMINI.md section E2). Les placeholders de template
 * (ex. `{{ a.icon }}`) sont resolus via les objets `I` inline des maquettes et
 * ne sont pas des donnees de trace : ils sont exclus de la comparaison.
 */
class IconCoverageTest {
    private val pathAttrPattern = Regex("""<path[^>]*\bd="([^"]+)"""")
    private val templatePlaceholder = Regex("""\{\{.*}}""")

    @Test
    fun everyMockupPathIsCoveredByTmmIconId() {
        val knownPaths = TmmIconId.entries
            .flatMap { it.elements }
            .filterIsInstance<IconElement.Path>()
            .map { it.d }
            .toSet()

        val mockupDir = File("../maquettes")
        // Maquettes v1 encore en vigueur + maquette v2 (maquettes/v2-simplifiee).
        val mockups = mockupDir.listFiles { f -> f.name.endsWith(".dc.html") }!!.toList() +
            File(mockupDir, "v2-simplifiee/index.html")
        val missing = mockups
            .flatMap { file ->
                pathAttrPattern.findAll(file.readText()).map { it.groupValues[1] }
            }
            .filterNot { templatePlaceholder.matches(it) }
            .filterNot { it in knownPaths }

        assertTrue("Traces de maquettes absentes de TmmIconId : $missing", missing.isEmpty())
    }
}
