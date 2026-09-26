package com.tmm.mobile.ui.screens.catalog

data class CatalogCategory(
    val name: String,
    val count: Int,
    val maxCount: Int = 58,
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
        CatalogCategory("En cours", 24),
        CatalogCategory("À lire", 31),
        CatalogCategory("Terminé", 58),
        CatalogCategory("Favoris", 12),
        CatalogCategory("Webtoons", 9),
        CatalogCategory("Abandonné", 5),
        CatalogCategory("Sans catégorie", 3),
    ),
    val importStep: CatalogImportStep = CatalogImportStep.Idle,
) {
    val isBusy: Boolean get() = importStep != CatalogImportStep.Idle
}

sealed interface CatalogEvent {
    data object Reimport : CatalogEvent
    data class SetImportStep(val step: CatalogImportStep) : CatalogEvent
}
