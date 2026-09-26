package com.tmm.mobile.mihon

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.ScannedSeriesDirEntity
import com.tmm.mobile.data.db.TmmDatabase
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.first

// CAHIER_DES_CHARGES.md 8.2 : parcours downloads/<source>/<serie>/<chapitre>, cache par date de
// modification du dossier serie, dossiers incomplets ignores. Deuxieme parcours de local/ pour
// local_fr_chapter, en preservant jobId/mihonPath des lignes deja connues (poses par
// InstallJobWorker, pas par le scan).
class DownloadScanner(private val fs: MihonFs, private val db: TmmDatabase) {

    suspend fun scanDownloads(downloadsRootDocId: String) {
        val series = db.seriesDao().observeAll().first().map { MatchableSeries(it.seriesKey, it.sourceName, it.title) }
        val chaptersBySeriesKey = db.chapterDao().observeAll().first().groupBy { it.seriesKey }

        for (sourceDir in fs.children(downloadsRootDocId).filter { it.isDirectory }) {
            for (seriesDirEntry in fs.children(sourceDir.docId).filter { it.isDirectory }) {
                val cached = db.scannedSeriesDirDao().get(sourceDir.name, seriesDirEntry.name)
                if (cached != null && cached.lastModified == seriesDirEntry.lastModified) continue

                val matchedSeries = Matcher.findSeries(sourceDir.name, seriesDirEntry.name, series)
                val matchableChapters = matchedSeries?.let { chaptersBySeriesKey[it.seriesKey] }
                    ?.map { MatchableChapter(it.chapterKey, it.name, it.scanlator, it.url, it.number) }
                    .orEmpty()

                for (chapterEntry in fs.children(seriesDirEntry.docId)) {
                    val chapterDirName = chapterEntry.name.removeSuffix(".cbz")
                    if (MihonNaming.isIncompleteChapterDirName(chapterDirName)) continue

                    val isCbz = !chapterEntry.isDirectory && chapterEntry.name.endsWith(".cbz")
                    val names = entryNames(chapterEntry, isCbz)
                    val matchedChapter = Matcher.findChapter(chapterDirName, matchableChapters)

                    db.downloadedChapterDao().upsert(
                        DownloadedChapterEntity(
                            mihonPath = "${sourceDir.name}/${seriesDirEntry.name}/${chapterEntry.name}",
                            sourceDir = sourceDir.name,
                            seriesDir = seriesDirEntry.name,
                            chapterDir = chapterEntry.name,
                            documentUri = chapterEntry.docId,
                            isCbz = isCbz,
                            sizeBytes = chapterEntry.size,
                            lastModified = chapterEntry.lastModified,
                            pageCount = CbzPacker.pageCount(names),
                            seriesKey = matchedSeries?.seriesKey,
                            chapterKey = matchedChapter?.chapterKey,
                            number = matchedChapter?.number,
                        ),
                    )
                }

                db.scannedSeriesDirDao().upsert(
                    ScannedSeriesDirEntity(sourceDir.name, seriesDirEntry.name, seriesDirEntry.lastModified),
                )
            }
        }
    }

    suspend fun scanLocal(localRootDocId: String) {
        for (folderEntry in fs.children(localRootDocId).filter { it.isDirectory }) {
            for (fileEntry in fs.children(folderEntry.docId).filter { !it.isDirectory }) {
                val localPath = "${folderEntry.name}/${fileEntry.name}"
                val existing = db.localFrChapterDao().get(localPath)
                db.localFrChapterDao().upsert(
                    LocalFrChapterEntity(
                        localPath = localPath,
                        folderName = folderEntry.name,
                        fileName = fileEntry.name,
                        documentUri = fileEntry.docId,
                        lastModified = fileEntry.lastModified,
                        jobId = existing?.jobId,
                        mihonPath = existing?.mihonPath,
                    ),
                )
            }
        }
    }

    private fun entryNames(entry: MihonDocEntry, isCbz: Boolean): List<String> = when {
        entry.isDirectory -> fs.children(entry.docId).filter { !it.isDirectory }.map { it.name }
        isCbz -> ZipInputStream(fs.openInput(entry.docId)).use { zis ->
            generateSequence { zis.nextEntry }.map { it.name }.toList()
        }
        else -> emptyList()
    }
}
