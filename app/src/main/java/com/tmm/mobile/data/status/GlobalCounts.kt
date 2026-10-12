package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.TmmDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.tmm.mobile.data.db.JobEntity

// pendingTransfersCount = toSend + toFetch, toutes series confondues (recapitulatif de
// l'Onboarding ; ex-badge de l'ecran Transferts retire en v2, PLAN_GEMINI.md 5.19).
// ponytail: le join pc_index se fait sur DownloadedChapterEntity.mihonPath directement (meme cle
// primaire que pc_index_chapter) ; les chapitres jamais telecharges ne comptent pas dans ce total,
// donc pas besoin de recalculer un mihonPath theorique via MihonNaming/Matcher ici.
object GlobalCounts {
    fun activityBadgeCount(db: TmmDatabase): Flow<Int> = db.jobDao().observeAll()
        .ignoringProgress()
        .map { jobs -> jobs.count { it.isInActivity() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun pendingTransfersCount(db: TmmDatabase): Flow<Int> = combine(
        db.downloadedChapterDao().observeAll(),
        db.jobDao().observeAll().ignoringProgress(),
        db.localFrChapterDao().observeAll(),
        db.pcIndexChapterDao().observeAll(),
    ) { downloaded, jobs, localFr, pcIndex ->
        val latestJobByPath = jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { job -> job.createdAt } }
        val localFrPaths = localFr.mapNotNull { it.mihonPath }.toSet()
        val indexByPath = pcIndex.associateBy { it.mihonPath }
        val toSend = downloaded.count { dl ->
            val facts = ChapterFacts(
                downloaded = true,
                inCatalog = dl.chapterKey != null,
                index = indexByPath[dl.mihonPath],
                latestJob = latestJobByPath[dl.mihonPath],
                localFr = dl.mihonPath in localFrPaths,
            )
            deriveStatus(facts) == ChapterStatus.TO_SEND
        }
        val toFetch = latestJobByPath.values.count { it.phoneState == "RESULT_READY" }
        toSend + toFetch
    }
        // Hors thread principal : le calcul parcourt tous les chapitres telecharges a chaque
        // ecriture en base.
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
}

/** Même périmètre pour le badge et les listes de traitements et transferts. */
internal fun JobEntity.isInActivity(): Boolean = !isFailed() && phoneState in setOf(
    "PREPARING", "UPLOADING", "SENT", "DOWNLOADING", "CANCEL_REQUESTED",
)
