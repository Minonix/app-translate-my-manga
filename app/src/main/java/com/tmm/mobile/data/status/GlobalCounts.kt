package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.TmmDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// PLAN_GEMINI.md 5.19 : badge Transferts = toSend + toFetch, toutes series confondues.
// ponytail: le join pc_index se fait sur DownloadedChapterEntity.mihonPath directement (meme cle
// primaire que pc_index_chapter) ; les chapitres jamais telecharges ne comptent pas dans ce badge,
// donc pas besoin de recalculer un mihonPath theorique via MihonNaming/Matcher ici.
object GlobalCounts {
    fun transfersBadgeCount(db: TmmDatabase): Flow<Int> = combine(
        db.downloadedChapterDao().observeAll(),
        db.jobDao().observeAll(),
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
}
