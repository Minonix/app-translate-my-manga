package com.tmm.mobile.data.status

/**
 * Action "Traduire" / "Retraduire" de la Fiche serie et de la Bibliotheque : un seul bouton, un seul
 * mode (JobMode.RETRANSLATE). Cote PC, "retranslate" repart de l'original stocke sur le PC, qu'il ait
 * deja ete traduit ou non : un chapitre NOT_TRANSLATED se traduit donc par ce meme mode.
 *
 * Cibles = chapitres dont l'original est sur le PC et sans job actif. Les chapitres TO_SEND (original
 * absent du PC) n'en font jamais partie : ils passent par "Envoyer au PC".
 * `retranslate` = au moins une cible a deja une traduction (locale ou sur le PC) que l'action ecrase,
 * d'ou le libelle "Retraduire" et la feuille de confirmation ; sinon "Traduire", sans confirmation.
 */
data class TranslationPlan(
    val targets: List<ChapterJoinRow>,
    val alreadyTranslated: Int,
) {
    val count: Int get() = targets.size
    val retranslate: Boolean get() = alreadyTranslated > 0
}

fun planTranslation(rows: List<ChapterJoinRow>): TranslationPlan {
    val targets = rows.filter { row ->
        row.status != ChapterStatus.TO_SEND &&
            row.facts.index?.original == true &&
            row.facts.latestJob?.isActive() != true
    }
    val alreadyTranslated = targets.count { it.facts.localFr || it.facts.index?.fr == true }
    return TranslationPlan(targets, alreadyTranslated)
}
