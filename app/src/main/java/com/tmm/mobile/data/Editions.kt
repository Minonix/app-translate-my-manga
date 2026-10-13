package com.tmm.mobile.data

/**
 * R2 (PBS V1) : une seule traduction par numero de chapitre et par serie. Quand deux editions
 * (scantrads) d'un meme numero sont candidates, l'app en preselectionne une ; l'utilisateur peut
 * changer a la main (feuille Traduire, fiche serie).
 *
 * Rang des scantrads d'une serie : la plus presente d'abord, puis l'ordre alphabetique, une
 * scantrad inconnue en dernier.
 */
fun editionRanking(scanlators: List<String?>): Map<String?, Int> =
    scanlators.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String?, Int>> { it.value }.thenBy { it.key == null }.thenBy { it.key })
        .mapIndexed { i, e -> e.key to i }
        .toMap()

/**
 * Garde au plus un candidat par numero, dans l'ordre d'origine : aucun si le numero est deja `taken`
 * (une autre edition est traduite, sur le PC ou en cours), sinon le premier selon `order`. Un candidat
 * sans numero reste : impossible de savoir s'il double un autre chapitre.
 */
fun <T> oneEditionPerNumber(
    candidates: List<T>,
    taken: Set<Float>,
    number: (T) -> Float?,
    order: Comparator<in T>,
): List<T> {
    val chosen = HashSet<Float>()
    val kept = candidates.sortedWith(order).filterTo(HashSet()) { c ->
        val n = number(c)
        n == null || (n !in taken && chosen.add(n))
    }
    return candidates.filter { it in kept }
}
