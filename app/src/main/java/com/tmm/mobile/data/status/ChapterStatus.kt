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

// PLAN_GEMINI.md 5.4 : table exacte des transitions autorisees ("toute autre est un bug").
// null = creation (JobRepository.createJobs). INSTALLED/FAILED/CANCEL_REQUESTED sont terminaux
// (un retry() cree un nouveau job, il ne fait pas rejouer l'ancien).
private val PHONE_STATE_TRANSITIONS: Map<String?, Set<String>> = mapOf(
    null to setOf("PREPARING"),
    "PREPARING" to setOf("UPLOADING", "SENT", "CANCEL_REQUESTED", "FAILED"),
    "UPLOADING" to setOf("SENT", "CANCEL_REQUESTED", "FAILED"),
    "SENT" to setOf("RESULT_READY", "INSTALLED", "CANCEL_REQUESTED"),
    "RESULT_READY" to setOf("DOWNLOADING"),
    "DOWNLOADING" to setOf("INSTALLED", "FAILED"),
)

fun isAllowedPhoneStateTransition(from: String?, to: String): Boolean = to in (PHONE_STATE_TRANSITIONS[from] ?: emptySet())

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
