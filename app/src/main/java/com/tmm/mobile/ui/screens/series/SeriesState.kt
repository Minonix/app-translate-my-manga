package com.tmm.mobile.ui.screens.series

import androidx.compose.ui.graphics.Color

/** Statistiques de traduction d'un chapitre (pages, bulles, SFX, verification), depliees sous la ligne. */
data class SeriesChapterStats(
    val pagesTotal: Int,
    val pagesTranslated: Int,
    val bubblesTranslated: Int,
    val bubblesUntranslated: Int,
    val sfxKept: Int,
    val verifiedDate: String?,
)

/** Statut affiche d'une ligne (maquette v2, ecran 3) : une seule etiquette par chapitre. */
sealed interface ChapterKind {
    data object ToTranslate : ChapterKind
    data object OnPc : ChapterKind
    data object Waiting : ChapterKind
    data class Translating(val percent: Int) : ChapterKind
    data class Failed(val page: Int?) : ChapterKind
    data object Ready : ChapterKind
    data object InMihon : ChapterKind
    data object NotDownloaded : ChapterKind
    data object Missing : ChapterKind
}

/**
 * Ligne de la liste « Chapitres » : un chapitre, jamais un intervalle. `key` l'identifie (chemin Mihon),
 * car deux lignes peuvent partager un numero (12 et 12.5, deux editions du 12). `label` est le numero
 * affiche ; `edition` (scantrad) n'est renseignee que si un autre chapitre porte le meme numero.
 */
data class SeriesRow(
    val number: Int,
    val kind: ChapterKind,
    val stats: SeriesChapterStats? = null,
    val key: String = number.toString(),
    val label: String = number.toString(),
    val edition: String? = null,
    val duplicate: Boolean = false,
) {
    /** En cours (file ou traduction) et manquant : rien a faire dessus. */
    val locked: Boolean get() = kind is ChapterKind.Translating || kind == ChapterKind.Waiting || kind == ChapterKind.Missing
}

/** Barre « N chapitres en VF sur T » : segments vert, bleu, encre, rouge puis gris. */
data class SeriesProgress(
    val inMihon: Int,
    val ready: Int,
    val busy: Int,
    val toTranslate: Int,
    val notDownloaded: Int,
    val total: Int,
) {
    val percent: Int get() = if (total == 0) 0 else inMihon * 100 / total
}

/** Nombre de chapitres concernes par chaque action de la feuille « Que faire ? » (lignes a 0 masquees). */
data class ActionCounts(
    val translate: Int = 0,
    val install: Int = 0,
    val verify: Int = 0,
    val retranslate: Int = 0,
    val copy: Int = 0,
) {
    val any: Boolean get() = translate + install + verify + retranslate + copy > 0
}

/** Feuille du bas ouverte. `More.allSeries` : bouton ··· (toute la serie) ou selection. */
sealed interface SeriesSheet {
    data object None : SeriesSheet
    data class More(val allSeries: Boolean) : SeriesSheet

    /** Confirmation du suivi : `numbers` sont les chapitres telecharges a traduire qui partiront aussi. */
    data class Follow(val numbers: List<Int>) : SeriesSheet

    /**
     * Feuille « Supprimer » : chapitres traites, chapitres ignores (job actif) et les trois cases.
     * Le telechargement Mihon (irreversible) et le PC sont decoches par defaut.
     */
    data class Delete(
        val chapterCount: Int,
        val skippedCount: Int,
        val localFr: Boolean = true,
        val mihon: Boolean = false,
        val pc: Boolean = false,
    ) : SeriesSheet {
        val anyChecked: Boolean get() = localFr || mihon || pc
    }
}

/** Case de la feuille « Supprimer ». */
enum class DeleteTarget { LocalFr, Mihon, Pc }

/** Toast bas d'ecran : le compte est mis en forme via stringResource au rendu. */
enum class PcDeleteOutcome { DONE, PARTIAL, REJECTED, WAITING }

sealed interface SeriesToast {
    data class Translated(val chapterCount: Int) : SeriesToast
    data class Copied(val chapterCount: Int) : SeriesToast
    data class Installing(val chapterCount: Int) : SeriesToast
    data class Verified(val chapterCount: Int) : SeriesToast
    data class Retranslated(val chapterCount: Int) : SeriesToast
    // failed : fichiers SAF non supprimes ; pcUnavailable : commande PC non mise en file.
    data class Deleted(val chapterCount: Int, val failed: Int = 0, val pcUnavailable: Boolean = false) : SeriesToast

    /** Suivi active : porte l'action « Annuler ». */
    data class Followed(val chapterCount: Int) : SeriesToast
    /** Resultat de la suppression demandee au PC (accuse du PC, ou pas encore de reponse). */
    data class PcDeleteResult(val outcome: PcDeleteOutcome, val failed: Int = 0) : SeriesToast
}

data class SeriesState(
    val title: String,
    val categoryLabel: String,
    val subtitle: String,
    val coverUrl: String? = null,
    val coverColor: Color,
    val followed: Boolean = false,
    val progress: SeriesProgress = SeriesProgress(0, 0, 0, 0, 0, 0),
    val rows: List<SeriesRow> = emptyList(),
    val missing: List<Int> = emptyList(),
    val showGap: Boolean = true,
    // Prochain numero attendu par le suivi (plus grand numero connu + 1).
    val nextExpected: Int? = null,
    // Chapitres partis a l'activation du suivi pendant cette visite (carte « envoye au PC »).
    val sentOnFollow: List<Int> = emptyList(),
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
    val expandedStats: Set<String> = emptySet(),
    val sheet: SeriesSheet = SeriesSheet.None,
    val toast: SeriesToast? = null,
    val seriesCounts: ActionCounts = ActionCounts(),
    val selectionCounts: ActionCounts = ActionCounts(),
    // Numeros a traduire de toute la serie, pour le libelle du gros bouton (« Traduire le ch. N »).
    val toTranslateNumbers: List<Int> = emptyList(),
    // Meme chose pour la selection.
    val selectionToTranslateNumbers: List<Int> = emptyList(),
    // Supprimer : chapitres selectionnes sans job actif, et ceux ignores (ChapterDeleter).
    val selectionToDelete: Int = 0,
    val selectionDeleteSkipped: Int = 0,
) {
    val selectionCount: Int get() = selected.size
    val selectedLabels: List<String> get() = rows.filter { it.key in selected }.map { it.label }

    /** Lignes verrouillees visibles en mode selection, citees en bas de la feuille. */
    val lockedNumbers: List<Int> get() = rows.filter { it.kind is ChapterKind.Translating || it.kind == ChapterKind.Waiting }.map { it.number }
}

sealed interface SeriesEvent {
    data object StartSelection : SeriesEvent
    data object EndSelection : SeriesEvent
    data class ToggleChapter(val key: String) : SeriesEvent

    // Appui long puis glissement sur la liste : coche (ou decoche) chaque chapitre survole.
    data class DragSelectStart(val key: String) : SeriesEvent
    data class DragSelectTo(val key: String) : SeriesEvent
    data object DragSelectEnd : SeriesEvent
    data class ToggleStats(val key: String) : SeriesEvent
    data object DismissGap : SeriesEvent

    /** Gros bouton rouge : traduit tout ce qui reste a traduire dans la serie. */
    data object TranslateAll : SeriesEvent
    data object OpenSeriesMore : SeriesEvent
    data object OpenSelectionMore : SeriesEvent
    data object CloseSheet : SeriesEvent

    // Actions de la feuille « Que faire ? », appliquees a la selection ou a toute la serie selon More.allSeries.
    data object Translate : SeriesEvent
    data object Install : SeriesEvent
    data object Verify : SeriesEvent
    data object Retranslate : SeriesEvent
    data object CopyToPc : SeriesEvent
    data object RequestDelete : SeriesEvent
    data class ToggleDeleteTarget(val target: DeleteTarget) : SeriesEvent
    data object ConfirmDelete : SeriesEvent

    data class SetFollow(val follow: Boolean) : SeriesEvent
    data object ConfirmFollow : SeriesEvent
    data object UndoFollow : SeriesEvent
    data object DismissToast : SeriesEvent
}
