package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.PcIndexChapterEntity

data class ChapterAggregateInput(
    val number: Float,
    val status: ChapterStatus,
    val latestJobPhoneState: String?,
    val downloaded: Boolean,
    val inCatalog: Boolean,
    val localFr: Boolean,
    val index: PcIndexChapterEntity?,
)

data class SeriesSummary(
    val seriesKey: String,
    val toSend: Int,
    val toFetch: Int,
    val busy: Int,
    val frCount: Int,
    val onPhone: Int,
    val verifiable: Int,
    val onPc: Int,
    val missing: List<Int>,
)

enum class SeriesBadge { BUSY, SEND, GET, ABSENT, OK }

fun deriveSeriesSummary(seriesKey: String, chapters: List<ChapterAggregateInput>, ignoredGaps: Set<Int>): SeriesSummary {
    val present = chapters.filter { it.downloaded || it.localFr || it.inCatalog }.map { it.number }
    // Un trou n'a de sens que si la serie est suivie concretement : au moins un chapitre sur le
    // telephone (original ou VF) ou sur le PC. Sinon ce n'est qu'une serie de la bibliotheque Mihon
    // dont rien n'est telecharge, et les numeros absents de la source ne manquent a personne.
    val owned = chapters.any { it.downloaded || it.localFr || it.index != null }
    return SeriesSummary(
        seriesKey = seriesKey,
        toSend = chapters.count { it.status == ChapterStatus.TO_SEND },
        // ponytail: le TO_FETCH derive du seul index (sans job RESULT_READY) est volontairement exclu
        // du compteur d'action "Recuperer", cf. plan §5.7.
        toFetch = chapters.count { it.latestJobPhoneState == "RESULT_READY" },
        busy = chapters.count { it.status == ChapterStatus.QUEUED || it.status == ChapterStatus.TRANSLATING },
        frCount = chapters.count { it.status == ChapterStatus.UP_TO_DATE },
        onPhone = chapters.filter { it.downloaded || it.localFr }.map { it.number }.distinct().size,
        verifiable = chapters.count { it.index?.original == true && (it.localFr || it.index.fr) },
        onPc = chapters.count { it.index?.original == true },
        missing = if (owned) missingChapters(present, ignoredGaps) else emptyList(),
    )
}

fun SeriesSummary.badge(): SeriesBadge = when {
    busy > 0 -> SeriesBadge.BUSY
    toSend > 0 -> SeriesBadge.SEND
    toFetch > 0 -> SeriesBadge.GET
    onPhone == 0 -> SeriesBadge.ABSENT
    else -> SeriesBadge.OK
}

fun SeriesSummary.matchesToTranslateFilter(): Boolean = toSend > 0 || busy > 0
fun SeriesSummary.matchesNotDownloadedFilter(): Boolean = onPhone == 0
