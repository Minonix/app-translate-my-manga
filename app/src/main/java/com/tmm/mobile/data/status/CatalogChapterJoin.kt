package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.flatten
import com.tmm.mobile.mihon.MihonNaming
import com.tmm.mobile.protocol.MihonPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Jointure chapitres <-> etat reel (E10, Bibliotheque + Fiche serie). Reutilise par les deux
 * ViewModels pour eviter de dupliquer le rapprochement catalogue/telecharge/job/pc_index deja
 * fait une fois dans GlobalCounts.kt (mais celui-ci ne porte que sur les chapitres deja telecharges).
 *
 * ponytail: pour un chapitre catalogue jamais telecharge, le mihonPath est reconstruit via
 * MihonNaming (meme convention que DownloadScanner/Matcher) afin de pouvoir chercher pc_index_chapter/
 * job par chapitre. Limite connue : si le dossier reel differe de cette reconstruction (renommage
 * manuel cote PC, source dont le nom differe de SeriesEntity.sourceName), le rapprochement echoue
 * silencieusement et le chapitre reste CATALOG_ONLY au lieu de NOT_TRANSLATED/TO_FETCH.
 */
data class ChapterJoinRow(
    val seriesKey: String,
    val chapter: ChapterEntity?,
    val downloaded: DownloadedChapterEntity?,
    val mihonPath: MihonPath,
    val facts: ChapterFacts,
    val status: ChapterStatus,
) {
    val flatPath: String get() = mihonPath.flatten()
}

data class ChapterJoinInputs(
    val chapters: List<ChapterEntity>,
    val downloaded: List<DownloadedChapterEntity>,
    val jobs: List<com.tmm.mobile.data.db.JobEntity>,
    val localFr: List<com.tmm.mobile.data.db.LocalFrChapterEntity>,
    val pcIndex: List<com.tmm.mobile.data.db.PcIndexChapterEntity>,
)

fun joinInputsFlow(db: TmmDatabase): Flow<ChapterJoinInputs> = combine(
    db.chapterDao().observeAll(),
    db.downloadedChapterDao().observeAll(),
    db.jobDao().observeAll(),
    db.localFrChapterDao().observeAll(),
    db.pcIndexChapterDao().observeAll(),
) { chapters, downloaded, jobs, localFr, pcIndex -> ChapterJoinInputs(chapters, downloaded, jobs, localFr, pcIndex) }

/**
 * Index des entrees de jointure, construit une seule fois : la Bibliotheque rapproche ~1000
 * series, et refiltrer toutes les listes par serie (O(series x chapitres)) gelait l'app.
 */
class ChapterJoinIndex(inputs: ChapterJoinInputs) {
    internal val latestJobByPath = inputs.jobs.groupBy { it.mihonPath }.mapValues { (_, v) -> v.maxBy { j -> j.createdAt } }
    internal val localFrPaths = inputs.localFr.mapNotNull { it.mihonPath }.toSet()
    internal val indexByPath = inputs.pcIndex.associateBy { it.mihonPath }
    internal val chaptersBySeries = inputs.chapters.groupBy { it.seriesKey }
    internal val downloadedBySeries = inputs.downloaded.groupBy { it.seriesKey }
}

/**
 * Rapproche les chapitres d'une serie (catalogue + telecharges non rattaches a un chapitre
 * catalogue) en ChapterJoinRow. `sourceName`/`seriesTitle` viennent de SeriesEntity.
 */
fun buildChapterJoinRows(
    seriesKey: String,
    sourceName: String,
    seriesTitle: String,
    inputs: ChapterJoinInputs,
): List<ChapterJoinRow> = buildChapterJoinRows(seriesKey, sourceName, seriesTitle, ChapterJoinIndex(inputs))

fun buildChapterJoinRows(
    seriesKey: String,
    sourceName: String,
    seriesTitle: String,
    index: ChapterJoinIndex,
): List<ChapterJoinRow> {
    val seriesDownloaded = index.downloadedBySeries[seriesKey].orEmpty()
    val downloadedByChapterKey = seriesDownloaded.associateBy { it.chapterKey }
    val downloadedOrphans = seriesDownloaded.filter { it.chapterKey == null }

    fun rowFor(chapter: ChapterEntity?, downloaded: DownloadedChapterEntity?): ChapterJoinRow {
        val mihonPath = if (downloaded != null) {
            MihonPath(downloaded.sourceDir, downloaded.seriesDir, downloaded.chapterDir)
        } else {
            MihonPath(
                MihonNaming.getSourceDirName(sourceName),
                MihonNaming.getMangaDirName(seriesTitle),
                MihonNaming.getChapterDirName(chapter!!.name, chapter.scanlator, chapter.url),
            )
        }
        val flat = mihonPath.flatten()
        val facts = ChapterFacts(
            downloaded = downloaded != null,
            inCatalog = chapter != null,
            index = index.indexByPath[flat],
            latestJob = index.latestJobByPath[flat],
            localFr = flat in index.localFrPaths,
        )
        return ChapterJoinRow(seriesKey, chapter, downloaded, mihonPath, facts, deriveStatus(facts))
    }

    val catalogRows = index.chaptersBySeries[seriesKey].orEmpty()
        .map { chapter -> rowFor(chapter, downloadedByChapterKey[chapter.chapterKey]) }
    val orphanRows = downloadedOrphans.map { downloaded -> rowFor(null, downloaded) }
    return catalogRows + orphanRows
}

fun List<ChapterJoinRow>.toAggregateInputs(): List<ChapterAggregateInput> = map { row ->
    ChapterAggregateInput(
        number = row.chapter?.number ?: row.downloaded?.number ?: 0f,
        status = row.status,
        latestJobPhoneState = row.facts.latestJob?.phoneState,
        downloaded = row.facts.downloaded,
        inCatalog = row.facts.inCatalog,
        localFr = row.facts.localFr,
        index = row.facts.index,
    )
}
