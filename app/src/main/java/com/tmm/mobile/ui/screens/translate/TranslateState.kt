package com.tmm.mobile.ui.screens.translate

import androidx.compose.ui.graphics.Color

/** Un chapitre téléchargé, jamais traduit (statut TO_SEND). `mihonPath` sert d'identifiant de sélection. */
data class TranslateChapter(
    val mihonPath: String,
    val number: Float,
    val pages: Int,
) {
    /** "52" ou "10.5" (pas de décimale inutile). */
    val numberLabel: String get() = if (number == number.toInt().toFloat()) number.toInt().toString() else number.toString()
}

data class TranslateSeries(
    val seriesKey: String,
    val title: String,
    val coverUrl: String?,
    val color: Color,
    val chapters: List<TranslateChapter>,
)

/** Case de série à 3 états (maquette v2, écran 6 : tiret quand la sélection est partielle). */
enum class SeriesCheck { All, Partial, None }

/**
 * Qui ouvre la feuille et avec quoi coché. Accueil : tout ; Bibliothèque (sélection multi-séries) :
 * tout, restreint aux séries choisies ; Activité (+) : rien.
 */
data class TranslateRequest(val preselectAll: Boolean, val seriesKeys: Set<String>? = null)

sealed interface TranslatePhase {
    data object Idle : TranslatePhase
    data object Busy : TranslatePhase
    data class Done(val count: Int, val translate: Boolean) : TranslatePhase
}

data class TranslateState(
    val query: String = "",
    val series: List<TranslateSeries> = emptyList(),
    val selected: Set<String> = emptySet(),
    val expanded: Set<String> = emptySet(),
    val phase: TranslatePhase = TranslatePhase.Idle,
) {
    val totalCount: Int get() = series.sumOf { it.chapters.size }
    val selectedCount: Int get() = series.sumOf { s -> s.chapters.count { it.mihonPath in selected } }

    val filtered: List<TranslateSeries>
        get() {
            val q = query.trim().lowercase()
            return if (q.isEmpty()) series else series.filter { it.title.lowercase().contains(q) }
        }

    fun checkOf(series: TranslateSeries): SeriesCheck {
        val n = series.chapters.count { it.mihonPath in selected }
        return when (n) {
            0 -> SeriesCheck.None
            series.chapters.size -> SeriesCheck.All
            else -> SeriesCheck.Partial
        }
    }
}

sealed interface TranslateEvent {
    data class Open(val request: TranslateRequest) : TranslateEvent
    data class UpdateQuery(val query: String) : TranslateEvent
    data class ToggleSeries(val seriesKey: String) : TranslateEvent
    data class ToggleExpand(val seriesKey: String) : TranslateEvent
    data class ToggleChapter(val mihonPath: String) : TranslateEvent

    /** A : « Traduire » envoie translate = true, « copier sur le PC sans traduire » translate = false. */
    data class Send(val translate: Boolean) : TranslateEvent
}
