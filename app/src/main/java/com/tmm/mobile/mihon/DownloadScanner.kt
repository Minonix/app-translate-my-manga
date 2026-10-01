package com.tmm.mobile.mihon

import androidx.room.withTransaction
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.ScannedSeriesDirEntity
import com.tmm.mobile.data.db.TmmDatabase
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.first

// Cle "source" des dossiers de local/ dans scanned_series_dir : un nom de dossier source Mihon ne
// contient jamais de "/", donc aucune collision avec les vrais dossiers de downloads/.
internal const val LOCAL_SCAN_SOURCE = "local/"

// CAHIER_DES_CHARGES.md 8.2 : parcours downloads/<source>/<serie>/<chapitre>, cache par date de
// modification du dossier serie, dossiers incomplets ignores. Deuxieme parcours de local/ pour
// local_fr_chapter, en preservant jobId/mihonPath des lignes deja connues (poses par
// InstallJobWorker, pas par le scan).
// Avec ~2000 series, le cas courant est "rien n'a change" : seuls les dossiers dont la date a bouge
// sont relus, le catalogue n'est charge que si besoin, et les dossiers disparus (series ou chapitres
// supprimes dans Mihon) sont retires du cache Room.
class DownloadScanner(private val fs: MihonFs, private val db: TmmDatabase) {

    suspend fun scanDownloads(downloadsRootDocId: String) {
        val cached = db.scannedSeriesDirDao().observeAll().first()
            .filter { it.sourceDir != LOCAL_SCAN_SOURCE }
            .associate { (it.sourceDir to it.seriesDir) to it.lastModified }
        val seen = HashSet<Pair<String, String>>()
        var series: List<MatchableSeries>? = null

        for (sourceDir in fs.children(downloadsRootDocId).filter { it.isDirectory }) {
            for (seriesDirEntry in fs.children(sourceDir.docId).filter { it.isDirectory }) {
                val key = sourceDir.name to seriesDirEntry.name
                seen += key
                if (cached[key] == seriesDirEntry.lastModified) continue

                val allSeries = series
                    ?: db.seriesDao().observeAll().first().map { MatchableSeries(it.seriesKey, it.sourceName, it.title) }
                        .also { series = it }
                val matchedSeries = Matcher.findSeries(sourceDir.name, seriesDirEntry.name, allSeries)
                val matchableChapters = matchableChapters(matchedSeries)

                val rows = fs.children(seriesDirEntry.docId).mapNotNull { chapterEntry ->
                    val chapterDirName = chapterEntry.name.removeSuffix(".cbz")
                    if (MihonNaming.isIncompleteChapterDirName(chapterDirName)) return@mapNotNull null

                    val isCbz = !chapterEntry.isDirectory && chapterEntry.name.endsWith(".cbz")
                    val matchedChapter = Matcher.findChapter(chapterDirName, matchableChapters)
                    DownloadedChapterEntity(
                        mihonPath = "${sourceDir.name}/${seriesDirEntry.name}/${chapterEntry.name}",
                        sourceDir = sourceDir.name,
                        seriesDir = seriesDirEntry.name,
                        chapterDir = chapterEntry.name,
                        documentUri = chapterEntry.docId,
                        isCbz = isCbz,
                        sizeBytes = chapterEntry.size,
                        lastModified = chapterEntry.lastModified,
                        pageCount = CbzPacker.pageCount(entryNames(chapterEntry, isCbz)),
                        seriesKey = matchedSeries?.seriesKey,
                        chapterKey = matchedChapter?.chapterKey,
                        number = matchedChapter?.number,
                    )
                }

                // Une transaction par serie : les chapitres supprimes disparaissent, et Room n'emet
                // qu'une invalidation (la Bibliotheque ne se recalcule pas a chaque chapitre).
                db.withTransaction {
                    db.downloadedChapterDao().deleteForSeriesDir(sourceDir.name, seriesDirEntry.name)
                    db.downloadedChapterDao().upsertAll(rows)
                    db.scannedSeriesDirDao().upsert(
                        ScannedSeriesDirEntity(sourceDir.name, seriesDirEntry.name, seriesDirEntry.lastModified),
                    )
                }
            }
        }

        val gone = cached.keys - seen
        if (gone.isNotEmpty()) {
            db.withTransaction {
                for ((sourceDir, seriesDir) in gone) {
                    db.downloadedChapterDao().deleteForSeriesDir(sourceDir, seriesDir)
                    db.scannedSeriesDirDao().delete(sourceDir, seriesDir)
                }
            }
        }
    }

    /**
     * Apres un nouvel import de sauvegarde, refait le rapprochement serie/chapitre des telechargements
     * deja connus, sans relire le stockage : le cache par date de dossier ne les reverrait jamais, et
     * une serie ajoutee a la bibliotheque Mihon resterait "hors catalogue".
     */
    suspend fun rematch() {
        val series = db.seriesDao().observeAll().first().map { MatchableSeries(it.seriesKey, it.sourceName, it.title) }
        val updated = db.downloadedChapterDao().observeAll().first()
            .groupBy { it.sourceDir to it.seriesDir }
            .flatMap { (dir, rows) ->
                val matchedSeries = Matcher.findSeries(dir.first, dir.second, series)
                val chapters = matchableChapters(matchedSeries)
                rows.map { row ->
                    val matchedChapter = Matcher.findChapter(row.chapterDir.removeSuffix(".cbz"), chapters)
                    row.copy(seriesKey = matchedSeries?.seriesKey, chapterKey = matchedChapter?.chapterKey, number = matchedChapter?.number)
                }.filterIndexed { i, row -> row != rows[i] }
            }
        if (updated.isNotEmpty()) db.downloadedChapterDao().upsertAll(updated)
    }

    suspend fun scanLocal(localRootDocId: String) {
        // Une ligne posee par InstallJobWorker pendant ce scan (date >= debut) ne doit pas etre prise
        // pour un fichier supprime : elle porte le jobId/mihonPath qu'aucun scan ne sait reconstruire.
        val scanStartedAt = System.currentTimeMillis()
        val cached = db.scannedSeriesDirDao().getForSource(LOCAL_SCAN_SOURCE).associate { it.seriesDir to it.lastModified }
        val seen = HashSet<String>()

        for (folderEntry in fs.children(localRootDocId).filter { it.isDirectory }) {
            seen += folderEntry.name
            if (cached[folderEntry.name] == folderEntry.lastModified) continue

            val files = fs.children(folderEntry.docId).filter { !it.isDirectory }
            db.withTransaction {
                val existing = db.localFrChapterDao().getForFolder(folderEntry.name).associateBy { it.localPath }
                val rows = files.map { fileEntry ->
                    val localPath = "${folderEntry.name}/${fileEntry.name}"
                    LocalFrChapterEntity(
                        localPath = localPath,
                        folderName = folderEntry.name,
                        fileName = fileEntry.name,
                        documentUri = fileEntry.docId,
                        lastModified = fileEntry.lastModified,
                        jobId = existing[localPath]?.jobId,
                        mihonPath = existing[localPath]?.mihonPath,
                    )
                }
                db.localFrChapterDao().upsertAll(rows)
                val present = rows.mapTo(HashSet()) { it.localPath }
                existing.values
                    .filter { it.localPath !in present && it.lastModified < scanStartedAt }
                    .forEach { db.localFrChapterDao().delete(it) }
                db.scannedSeriesDirDao().upsert(ScannedSeriesDirEntity(LOCAL_SCAN_SOURCE, folderEntry.name, folderEntry.lastModified))
            }
        }

        val gone = cached.keys - seen
        if (gone.isNotEmpty()) {
            db.withTransaction {
                for (folderName in gone) {
                    db.localFrChapterDao().getForFolder(folderName)
                        .filter { it.lastModified < scanStartedAt }
                        .forEach { db.localFrChapterDao().delete(it) }
                    db.scannedSeriesDirDao().delete(LOCAL_SCAN_SOURCE, folderName)
                }
            }
        }
    }

    private suspend fun matchableChapters(series: MatchableSeries?): List<MatchableChapter> =
        series?.let { db.chapterDao().getForSeries(it.seriesKey) }
            ?.map { MatchableChapter(it.chapterKey, it.name, it.scanlator, it.url, it.number) }
            .orEmpty()

    private fun entryNames(entry: MihonDocEntry, isCbz: Boolean): List<String> = when {
        entry.isDirectory -> fs.children(entry.docId).filter { !it.isDirectory }.map { it.name }
        isCbz -> ZipInputStream(fs.openInput(entry.docId)).use { zis ->
            generateSequence { zis.nextEntry }.map { it.name }.toList()
        }
        else -> emptyList()
    }
}
