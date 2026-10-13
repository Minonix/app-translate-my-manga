package com.tmm.mobile.data

import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.protocol.MihonPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

// PLAN_GEMINI.md 5.15/5.16 : meme etape d'auto-traduction appelee par Startup.run() (une fois au
// demarrage) et par RefreshCoordinator.refresh() (a chaque cycle) - une seule implementation partagee.
// Tourne a chaque refresh : les series en auto-traduction sont lues en une requete (et le cas courant,
// aucune, sort tout de suite) au lieu d'une requete par chapitre telecharge.
// R15 : appelee hors de tout try (Startup, refresh) ; une erreur est journalisee, jamais propagee.
suspend fun runAutoTranslate(db: TmmDatabase, jobRepository: JobRepository) {
    try {
        val autoSeries = db.seriesSettingsDao().autoTranslateSeriesKeys().toSet()
        if (autoSeries.isEmpty()) return
        for (target in autoTranslateTargets(db, autoSeries)) {
            jobRepository.createJobs(JobMode.TRANSLATE, listOf(target))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.d("AutoTranslate", "traduction automatique interrompue : ${e.javaClass.simpleName} ${e.message}")
    }
}

/**
 * Chapitres telecharges au statut A traduire et sans aucun job des series donnees : ce que le suivi
 * automatique enverra.
 * Partage par runAutoTranslate et par l'activation du suivi sur la fiche serie, pour que la feuille de
 * confirmation annonce exactement les chapitres qui partiront.
 */
suspend fun autoTranslateTargets(db: TmmDatabase, seriesKeys: Set<String>): List<JobTarget> {
    if (seriesKeys.isEmpty()) return emptyList()
    val localFrPaths = db.localFrChapterDao().observeAll().first().mapNotNull { it.mihonPath }.toSet()
    val targets = mutableListOf<JobTarget>()
    for (downloaded in db.downloadedChapterDao().observeAll().first()) {
        val seriesKey = downloaded.seriesKey ?: continue
        if (seriesKey !in seriesKeys) continue

        val index = db.pcIndexChapterDao().get(downloaded.mihonPath)
        val latestJob = db.jobDao().getAllForMihonPath(downloaded.mihonPath).firstOrNull()
        // R3/R7 : le suivi ne s'occupe que des chapitres jamais envoyes. Un chapitre qui a deja eu un
        // job (annule, installe puis supprime, installe sans fichier) ne repart qu'a la main.
        if (latestJob != null) continue
        val facts = ChapterFacts(
            downloaded = true,
            inCatalog = index != null,
            index = index,
            latestJob = latestJob,
            localFr = downloaded.mihonPath in localFrPaths,
        )
        if (deriveStatus(facts) != ChapterStatus.TO_SEND) continue

        targets += JobTarget(
            mihonPath = MihonPath(downloaded.sourceDir, downloaded.seriesDir, downloaded.chapterDir),
            downloaded = downloaded,
            series = db.seriesDao().get(seriesKey),
            chapter = downloaded.chapterKey?.let { db.chapterDao().get(it) },
        )
    }
    return targets
}
