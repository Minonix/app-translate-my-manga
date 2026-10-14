package com.tmm.mobile.ui.screens.series

/**
 * Selection par appui long puis glissement : tous les chapitres survoles entre [anchor] et
 * [current] sont coches (ou decoches si le premier l'etait deja).
 *
 * Recalcule depuis [base] a chaque pas : revenir en arriere rend aux chapitres quittes leur etat
 * d'avant le geste. Les lignes non selectionnables (en cours, manquantes, pas sur le telephone)
 * sont sautees. R2 : cocher une edition retire les autres editions du meme numero ([sameNumber]).
 */
internal fun dragSelection(
    order: List<String>,
    selectable: Set<String>,
    sameNumber: (String) -> Set<String>,
    base: Set<String>,
    anchor: String,
    current: String,
    adding: Boolean,
): Set<String> {
    val from = order.indexOf(anchor)
    val to = order.indexOf(current)
    if (from < 0 || to < 0) return base
    val range = order.subList(minOf(from, to), maxOf(from, to) + 1).filter { it in selectable }
    if (!adding) return base - range.toSet()
    return range.fold(base) { acc, key -> acc - sameNumber(key) + key }
}
