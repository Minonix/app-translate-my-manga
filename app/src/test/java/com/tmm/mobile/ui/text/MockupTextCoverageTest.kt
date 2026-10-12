package com.tmm.mobile.ui.text

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chaque texte visible d'une maquette portee en Compose doit avoir une ligne
 * dans mockup_texts.tsv, et toute ressource STR/FMT/PLU qu'elle declare doit
 * exister dans res/values (PLAN_GEMINI.md section 4.10/353-360).
 *
 * Maquettes de reference : les .dc.html restes a la racine de maquettes/
 * (ecrans inchanges en v2) et les sections de maquettes/v2-simplifiee/index.html,
 * notees `v2-simplifiee/index.html#<section>` dans la colonne fichier.
 */
class MockupTextCoverageTest {
    // Sections de la maquette v2 (tous les ecrans principaux sont portes).
    private val v2Sections = listOf("accueil", "bibliotheque", "serie", "traduire", "activite", "reglages", "reglages-traduction")

    private data class TsvRow(val fichier: String, val texte: String, val type: String, val ressource: String)

    private fun tsvRows(): List<TsvRow> {
        val file = File("src/test/resources/mockup_texts.tsv")
        return file.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val cols = line.split("\t")
            TsvRow(fichier = cols[0], texte = cols[1], type = cols[2], ressource = cols.getOrElse(3) { "" })
        }
    }

    private fun stringResourceNames(): Set<String> {
        // strings.xml et les fichiers par ecran (home_v2.xml, library_v2.xml...).
        val files = File("src/main/res/values").listFiles { f -> f.name.endsWith(".xml") }.orEmpty()
        return files.flatMap { f -> Regex("""name="([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] } }.toSet()
    }

    @Test
    fun everyDoneScreenHasTsvRows() {
        val doneScreens = File("../maquettes")
            .listFiles { f -> f.name.endsWith(".dc.html") }
            .orEmpty()
            .map { it.name } + v2Sections.map { "v2-simplifiee/index.html#$it" }
        val covered = tsvRows().map { it.fichier }.toSet()
        val missing = doneScreens.filterNot { it in covered }
        assertTrue("Ecrans livres sans ligne dans mockup_texts.tsv : $missing", missing.isEmpty())
    }

    @Test
    fun everyStrFmtPluRowHasAStringResource() {
        val resources = stringResourceNames()
        val missing = tsvRows()
            .filter { it.type == "STR" || it.type == "FMT" || it.type == "PLU" }
            .filterNot { it.ressource in resources }
        assertTrue("Ressources declarees dans mockup_texts.tsv absentes de res/values : $missing", missing.isEmpty())
    }
}
