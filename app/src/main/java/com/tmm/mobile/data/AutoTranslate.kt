package com.tmm.mobile.data

import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.protocol.MihonPath
import kotlinx.coroutines.flow.first

// PLAN_GEMINI.md 5.15/5.16 : meme etape d'auto-traduction appelee par Startup.run() (une fois au
// demarrage) et par RefreshCoordinator.refresh() (a chaque cycle) - une seule implementation partagee.
suspend fun runAutoTranslate(db: TmmDatabase, jobRepository: JobRepository) {
    val localFrPaths = db.localFrChapterDao().observeAll().first().mapNotNull { it.mihonPath }.toSet()
    for (downloaded in db.downloadedChapterDao().observeAll().first()) {
        val seriesKey = downloaded.seriesKey ?: continue
        val autoTranslateOn = db.seriesSettingsDao().observe(seriesKey).first()?.autoTranslate ?: false
        if (!autoTranslateOn) continue

        val index = db.pcIndexChapterDao().get(downloaded.mihonPath)
        val latestJob = db.jobDao().getAllForMihonPath(downloaded.mihonPath).firstOrNull()
        val facts = ChapterFacts(
            downloaded = true,
            inCatalog = index != null,
            index = index,
            latestJob = latestJob,
            localFr = downloaded.mihonPath in localFrPaths,
        )
        if (deriveStatus(facts) != ChapterStatus.TO_SEND) continue

        val target = JobTarget(
            mihonPath = MihonPath(downloaded.sourceDir, downloaded.seriesDir, downloaded.chapterDir),
            downloaded = downloaded,
            series = db.seriesDao().get(seriesKey),
            chapter = downloaded.chapterKey?.let { db.chapterDao().get(it) },
        )
        jobRepository.createJobs(JobMode.TRANSLATE, listOf(target))
    }
}
