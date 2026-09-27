package com.tmm.mobile.ui.screens.catalog

// PLAN_GEMINI.md 7.6 : l'appui sur une categorie navigue vers LibraryFilteredRoute(order).
// UNCATEGORIZED_ORDER est un repere local (pas une cle Room) pour la categorie ajoutee
// "Sans catégorie" : a coordonner avec le proprietaire du package library si un vrai filtre
// "sans categorie" y est ajoute.
const val UNCATEGORIZED_ORDER = -1L

data class CatalogCategory(
    val name: String,
    val count: Int,
    val maxCount: Int = 58,
    val order: Long? = null,
) {
    val progressFraction: Float get() = if (maxCount > 0) (count.toFloat() / maxCount).coerceIn(0f, 1f) else 0f
}

sealed interface CatalogImportStep {
    data object Idle : CatalogImportStep
    data object ReadingFile : CatalogImportStep
    data object Decompressing : CatalogImportStep
    data object ReadingSeries : CatalogImportStep
    data object ReadingCategories : CatalogImportStep
    data object Comparing : CatalogImportStep

    val progressFraction: Float
        get() = when (this) {
            Idle -> 0f
            ReadingFile -> 0.2f
            Decompressing -> 0.4f
            ReadingSeries -> 0.6f
            ReadingCategories -> 0.8f
            Comparing -> 1.0f
        }
}

data class CatalogState(
    val backupFileName: String = "com.mihon_2026-09-26.tachibk",
    val importedWhen: String = "aujourd'hui 03:00",
    val totalSeries: Int = 142,
    val absentSeries: Int = 38,
    val totalCategories: Int = 7,
    val categories: List<CatalogCategory> = listOf(
        CatalogCategory("En cours", 24, order = 0L),
        CatalogCategory("À lire", 31, order = 1L),
        CatalogCategory("Terminé", 58, order = 2L),
        CatalogCategory("Favoris", 12, order = 3L),
        CatalogCategory("Webtoons", 9, order = 4L),
        CatalogCategory("Abandonné", 5, order = 5L),
        CatalogCategory("Sans catégorie", 3, order = UNCATEGORIZED_ORDER),
    ),
    val importStep: CatalogImportStep = CatalogImportStep.Idle,
) {
    val isBusy: Boolean get() = importStep != CatalogImportStep.Idle
}

sealed interface CatalogEvent {
    data object Reimport : CatalogEvent
    data class SetImportStep(val step: CatalogImportStep) : CatalogEvent
}
