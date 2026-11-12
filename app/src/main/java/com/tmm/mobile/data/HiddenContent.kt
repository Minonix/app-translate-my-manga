package com.tmm.mobile.data

import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.TmmDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Series masquees a l'affichage (Reglages > Masquer), directement ou via une categorie Mihon masquee.
 * Purement visuel : chaque ecran filtre ses listes et ses comptes avec, rien n'est supprime en base.
 * `folders` (« source/serie ») couvre les jobs et telechargements sans seriesKey (hors sauvegarde).
 */
data class HiddenSet(val seriesKeys: Set<String> = emptySet(), val folders: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = seriesKeys.isEmpty()

    fun hidesSeries(seriesKey: String?): Boolean = seriesKey != null && seriesKey in seriesKeys

    fun hidesJob(job: JobEntity): Boolean =
        !isEmpty && (hidesSeries(job.seriesKey) || folderKey(job.sourceDir, job.seriesDir) in folders)

    fun hidesDownloaded(dl: DownloadedChapterEntity): Boolean =
        !isEmpty && (hidesSeries(dl.seriesKey) || folderKey(dl.sourceDir, dl.seriesDir) in folders)

    /** Chemin Mihon « source/serie/chapitre » (localFr, index PC). */
    fun hidesPath(mihonPath: String?): Boolean =
        !isEmpty && mihonPath != null && mihonPath.substringBeforeLast('/', "") in folders

    companion object {
        fun folderKey(sourceDir: String, seriesDir: String) = "$sourceDir/$seriesDir"
    }
}

internal fun buildHiddenSet(
    hiddenCategories: Set<String>,
    hiddenSeries: Set<String>,
    categories: List<CategoryEntity>,
    links: List<SeriesCategoryEntity>,
    downloaded: List<DownloadedChapterEntity>,
): HiddenSet {
    if (hiddenCategories.isEmpty() && hiddenSeries.isEmpty()) return HiddenSet()
    val hiddenOrders = categories.filter { it.name in hiddenCategories }.map { it.order }.toSet()
    val keys = hiddenSeries + links.filter { it.categoryOrder in hiddenOrders }.map { it.seriesKey }
    val folders = downloaded.filter { it.seriesKey != null && it.seriesKey in keys }
        .map { HiddenSet.folderKey(it.sourceDir, it.seriesDir) }.toSet()
    return HiddenSet(keys, folders)
}

fun hiddenSetFlow(db: TmmDatabase, prefs: AppPrefs): Flow<HiddenSet> = combine(
    prefs.hiddenCategories,
    prefs.hiddenSeries,
    db.categoryDao().observeAll(),
    db.seriesCategoryDao().observeAll(),
    db.downloadedChapterDao().observeAll(),
) { cats, series, categories, links, downloaded -> buildHiddenSet(cats, series, categories, links, downloaded) }
    .distinctUntilChanged()
