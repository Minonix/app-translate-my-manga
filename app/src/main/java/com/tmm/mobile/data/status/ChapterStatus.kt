package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity

enum class ChapterStatus {
    TO_SEND, QUEUED, TRANSLATING, FAILED, TO_FETCH, UP_TO_DATE, NOT_TRANSLATED, CATALOG_ONLY
}

private val ACTIVE_PHONE_STATES = setOf("PREPARING", "UPLOADING", "SENT", "RESULT_READY", "DOWNLOADING", "CANCEL_REQUESTED")
private val FAILED_PC_STATES = setOf("failed", "cancelled")

fun JobEntity.isActive(): Boolean = phoneState in ACTIVE_PHONE_STATES && pcState !in FAILED_PC_STATES
fun JobEntity.isFailed(): Boolean = phoneState == "FAILED" || pcState == "failed"
fun JobEntity.isCancelled(): Boolean = pcState == "cancelled"

data class ChapterFacts(
    val downloaded: Boolean,
    val inCatalog: Boolean,
    val index: PcIndexChapterEntity?,
    val latestJob: JobEntity?,
    val localFr: Boolean,
)

fun deriveStatus(f: ChapterFacts): ChapterStatus {
    val job = f.latestJob
    return when {
        job != null && job.phoneState in setOf("PREPARING", "UPLOADING", "SENT", "CANCEL_REQUESTED") && job.pcState !in FAILED_PC_STATES ->
            if (job.pcState == "translating") ChapterStatus.TRANSLATING else ChapterStatus.QUEUED
        job != null && job.isFailed() -> ChapterStatus.FAILED
        job != null && job.phoneState in setOf("RESULT_READY", "DOWNLOADING") -> ChapterStatus.TO_FETCH
        f.localFr -> ChapterStatus.UP_TO_DATE
        f.index?.fr == true -> ChapterStatus.TO_FETCH
        f.index?.original == true -> ChapterStatus.NOT_TRANSLATED
        f.downloaded -> ChapterStatus.TO_SEND
        else -> ChapterStatus.CATALOG_ONLY
    }
}

fun translatingPercent(job: JobEntity): Int {
    val page = job.pcPage ?: return 0
    val pages = job.pcPages ?: return 0
    if (pages == 0) return 0
    return page * 100 / pages
}

/**
 * Numeros de chapitre entiers manquants entre le min et le max des numeros presents (entiers
 * uniquement, les hors-serie type 7.5 ne comptent pas comme presence de leur partie entiere),
 * hors numeros ignores.
 */
fun missingChapters(present: Collection<Float>, ignored: Set<Int>): List<Int> {
    val presentInts = present.filter { it == it.toInt().toFloat() }.map { it.toInt() }.toSet()
    if (presentInts.isEmpty()) return emptyList()
    val min = presentInts.min()
    val max = presentInts.max()
    return (min..max).filter { it !in presentInts && it !in ignored }
}
