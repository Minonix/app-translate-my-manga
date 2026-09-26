package com.tmm.mobile.ui.text

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chaque texte visible d'une maquette deja portee en Compose (E4) doit avoir
 * une ligne dans mockup_texts.tsv, et toute ressource STR/FMT/PLU qu'elle
 * declare doit exister dans strings.xml (PLAN_GEMINI.md section 4.10/353-360).
 *
 * Ecrans pas encore livres en E4 : exclus ici, liste videe au fur et a mesure
 * des commits E4.2 a E4.13, vide a E4.13.
 */
class MockupTextCoverageTest {
    private val pendingScreens = emptySet<String>()

    private data class TsvRow(val fichier: String, val texte: String, val type: String, val ressource: String)

    private fun tsvRows(): List<TsvRow> {
        val file = File("src/test/resources/mockup_texts.tsv")
        return file.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val cols = line.split("\t")
            TsvRow(fichier = cols[0], texte = cols[1], type = cols[2], ressource = cols.getOrElse(3) { "" })
        }
    }

    private fun stringResourceNames(): Set<String> {
        val text = File("src/main/res/values/strings.xml").readText()
        return Regex("""name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun everyDoneScreenHasTsvRows() {
        val doneScreens = File("../maquettes")
            .listFiles { f -> f.name.endsWith(".dc.html") }
            .orEmpty()
            .map { it.name }
            .filterNot { it in pendingScreens }
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
        assertTrue("Ressources declarees dans mockup_texts.tsv absentes de strings.xml : $missing", missing.isEmpty())
    }
}
