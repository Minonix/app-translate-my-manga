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

// Nom des fichiers poses par le PC dans local/ : "c0033 - Ch. 33.cbz", "c0014.1 - Ch. 14.1.cbz".
private val LOCAL_CHAPTER_NUMBER = Regex("""^c(\d+(?:\.\d+)?)""")

// CAHIER_DES_CHARGES.md 8.2 : parcours downloads/<source>/<serie>/<chapitre>, cache par date de
// modification du dossier serie, dossiers incomplets ignores. Deuxieme parcours de local/ pour
// local_fr_chapter, en preservant jobId/mihonPath des lignes deja connues (poses par
// InstallJobWorker, pas par le scan).
// Avec ~2000 series, le cas courant est "rien n'a change" : seuls les dossiers dont la date a bouge
// sont relus, le catalogue n'est charge que si besoin, et les dossiers disparus (series ou chapitres
// supprimes dans Mihon) sont retires du cache Room.
class DownloadScanner(private val fs: MihonFs, private val db: TmmDatabase) {

    /** [force] relit chaque dossier serie sans tenir compte du cache (tirer pour recharger). */
    suspend fun scanDownloads(downloadsRootDocId: String, force: Boolean = false) {
        // Lignes posees par les versions qui comptaient aussi les fichiers/dossiers sans page : le cache
        // par date de dossier ne les relirait jamais, on les retire ici.
        db.downloadedChapterDao().deleteWithoutPages()
        val cached = db.scannedSeriesDirDao().observeAll().first()
            .filter { it.sourceDir != LOCAL_SCAN_SOURCE }
            .associate { (it.sourceDir to it.seriesDir) to it.lastModified }
        val seen = HashSet<Pair<String, String>>()
        var series: List<MatchableSeries>? = null

        for (sourceDir in fs.children(downloadsRootDocId).filter { it.isDirectory }) {
            for (seriesDirEntry in fs.children(sourceDir.docId).filter { it.isDirectory }) {
                val key = sourceDir.name to seriesDirEntry.name
                seen += key
                if (!force && cached[key] == seriesDirEntry.lastModified) continue

                val allSeries = series ?: matchableSeries().also { series = it }
                // Un chapitre est un dossier ou un .cbz : cover.jpg, .nomedia & co ne sont pas des
                // chapitres (ils gonflaient le compteur "a envoyer" d'un faux chapitre par serie).
                var downloading = false
                val entries = fs.children(seriesDirEntry.docId).mapNotNull { chapterEntry ->
                    if (MihonNaming.isIncompleteChapterDirName(chapterEntry.name.removeSuffix(".cbz"))) {
                        downloading = true
                        return@mapNotNull null
                    }
                    val isCbz = !chapterEntry.isDirectory && chapterEntry.name.endsWith(".cbz")
                    if (!chapterEntry.isDirectory && !isCbz) return@mapNotNull null
                    val pageCount = CbzPacker.pageCount(entryNames(chapterEntry, isCbz))
                    if (pageCount == 0) null else ScannedChapter(chapterEntry, isCbz, pageCount)
                }
                val matches = matchFolder(sourceDir.name, seriesDirEntry.name, entries.map { it.entry.name.removeSuffix(".cbz") }, allSeries)

                val rows = entries.map { (chapterEntry, isCbz, pageCount) ->
                    val match = matches[chapterEntry.name.removeSuffix(".cbz")]
                    DownloadedChapterEntity(
                        mihonPath = "${sourceDir.name}/${seriesDirEntry.name}/${chapterEntry.name}",
                        sourceDir = sourceDir.name,
                        seriesDir = seriesDirEntry.name,
                        chapterDir = chapterEntry.name,
                        documentUri = chapterEntry.docId,
                        isCbz = isCbz,
                        sizeBytes = chapterEntry.size,
                        lastModified = chapterEntry.lastModified,
                        pageCount = pageCount,
                        seriesKey = match?.series?.seriesKey,
                        chapterKey = match?.chapter?.chapterKey,
                        number = match?.chapter?.number ?: DownloadOnlySeries.guessNumber(chapterEntry.name),
                    )
                }

                // Une transaction par serie : les chapitres supprimes disparaissent, et Room n'emet
                // qu'une invalidation (la Bibliotheque ne se recalcule pas a chaque chapitre).
                db.withTransaction {
                    db.downloadedChapterDao().deleteForSeriesDir(sourceDir.name, seriesDirEntry.name)
                    db.downloadedChapterDao().upsertAll(rows)
                    // Telechargement en cours ("_tmp") : Mihon renomme le dossier a la fin, souvent dans la
                    // meme seconde que la date deja lue. Garder cette date en cache masquait le chapitre
                    // fini pour toujours (« Pas telecharge » alors qu'il l'est) : on relira ce dossier.
                    if (downloading) {
                        db.scannedSeriesDirDao().delete(sourceDir.name, seriesDirEntry.name)
                    } else {
                        db.scannedSeriesDirDao().upsert(
                            ScannedSeriesDirEntity(sourceDir.name, seriesDirEntry.name, seriesDirEntry.lastModified),
                        )
                    }
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
        syncDownloadOnlySeries()
    }

    /**
     * Apres un nouvel import de sauvegarde, refait le rapprochement serie/chapitre des telechargements
     * deja connus, sans relire le stockage : le cache par date de dossier ne les reverrait jamais, et
     * une serie ajoutee a la bibliotheque Mihon resterait "hors catalogue".
     */
    suspend fun rematch() {
        val series = matchableSeries()
        val updated = db.downloadedChapterDao().observeAll().first()
            .groupBy { it.sourceDir to it.seriesDir }
            .flatMap { (dir, rows) ->
                val matches = matchFolder(dir.first, dir.second, rows.map { it.chapterDir.removeSuffix(".cbz") }, series)
                rows.map { row ->
                    val match = matches[row.chapterDir.removeSuffix(".cbz")]
                    row.copy(
                        seriesKey = match?.series?.seriesKey,
                        chapterKey = match?.chapter?.chapterKey,
                        number = match?.chapter?.number ?: DownloadOnlySeries.guessNumber(row.chapterDir),
                    )
                }.filterIndexed { i, row -> row != rows[i] }
            }
        if (updated.isNotEmpty()) db.downloadedChapterDao().upsertAll(updated)
        syncDownloadOnlySeries()
    }

    /** Series de la sauvegarde seulement : une serie "downloads|" ne sert jamais de candidate. */
    private suspend fun matchableSeries(): List<MatchableSeries> = db.seriesDao().observeAll().first()
        .filterNot { DownloadOnlySeries.isDownloadOnly(it.seriesKey) }
        .map { MatchableSeries(it.seriesKey, it.sourceName, it.title) }

    /**
     * Cree la SeriesEntity des dossiers sans serie dans la sauvegarde, retire celles devenues inutiles
     * (serie arrivee par une sauvegarde, dossier supprime). Rejoue apres chaque scan et rematch : un
     * import de sauvegarde vide la table series, et le cache par date ne relirait pas ces dossiers.
     * N'ecrit que ce qui change : la Bibliotheque ne se recalcule pas a chaque demarrage.
     */
    private suspend fun syncDownloadOnlySeries() {
        val wanted = db.downloadedChapterDao().observeAll().first()
            .filter { DownloadOnlySeries.isDownloadOnly(it.seriesKey) }
            .groupBy { it.seriesKey }
            .values.map { DownloadOnlySeries.entity(it) }
        val existing = db.seriesDao().observeAll().first().filter { DownloadOnlySeries.isDownloadOnly(it.seriesKey) }
        val wantedKeys = wanted.mapTo(HashSet()) { it.seriesKey }
        val stale = existing.filter { it.seriesKey !in wantedKeys }
        val changed = wanted - existing.toSet()
        if (stale.isEmpty() && changed.isEmpty()) return
        db.withTransaction {
            stale.forEach { db.seriesDao().delete(it) }
            if (changed.isNotEmpty()) db.seriesDao().upsertAll(changed)
        }
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
        linkOrphanLocalFr()
    }

    /**
     * Rattache au chapitre telecharge les fichiers FR presents dans local/ sans mihonPath (installes avant
     * le suivi par TMM, ou ligne perdue). Sans ce lien, le statut retombait sur l'index du PC et
     * affichait « Pret a installer » pour un chapitre deja lisible dans Mihon. Passe hors du cache de
     * dossier : un dossier inchange depuis la mise a jour doit aussi etre rattache. Ne lie que sur une
     * correspondance unique (titre normalise + numero) : plusieurs editions telechargees restent ambigues.
     */
    private suspend fun linkOrphanLocalFr() {
        val all = db.localFrChapterDao().observeAll().first()
        val orphans = all.filter { it.mihonPath == null }
        if (orphans.isEmpty()) return
        val linked = all.mapNotNullTo(HashSet()) { it.mihonPath }
        val bySeries = db.downloadedChapterDao().observeAll().first().groupBy { looseTitle(it.seriesDir) }
        val updates = orphans.mapNotNull { row ->
            val number = LOCAL_CHAPTER_NUMBER.find(row.fileName)?.groupValues?.get(1)?.toFloatOrNull() ?: return@mapNotNull null
            val match = bySeries[looseTitle(row.folderName)].orEmpty().filter { it.number == number }
            val chapter = match.singleOrNull()?.takeIf { it.mihonPath !in linked } ?: return@mapNotNull null
            linked += chapter.mihonPath
            row.copy(mihonPath = chapter.mihonPath)
        }
        if (updates.isNotEmpty()) db.localFrChapterDao().upsertAll(updates)
    }

    private fun looseTitle(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    private suspend fun matchFolder(
        sourceDir: String,
        seriesDir: String,
        chapterDirNames: List<String>,
        series: List<MatchableSeries>,
    ): Map<String, ChapterMatch> {
        val candidates = Matcher.findSeriesCandidates(sourceDir, seriesDir, series).map { candidate ->
            candidate to db.chapterDao().getForSeries(candidate.seriesKey)
                .map { MatchableChapter(it.chapterKey, it.name, it.scanlator, it.url, it.number) }
        }
        if (candidates.isEmpty()) {
            val own = MatchableSeries(DownloadOnlySeries.key(sourceDir, seriesDir), sourceDir, seriesDir)
            return chapterDirNames.associateWith { ChapterMatch(own, null) }
        }
        return Matcher.assignChapters(chapterDirNames, candidates)
    }

    private data class ScannedChapter(val entry: MihonDocEntry, val isCbz: Boolean, val pageCount: Int)

    private fun entryNames(entry: MihonDocEntry, isCbz: Boolean): List<String> = when {
        entry.isDirectory -> fs.children(entry.docId).filter { !it.isDirectory }.map { it.name }
        isCbz -> ZipInputStream(fs.openInput(entry.docId)).use { zis ->
            generateSequence { zis.nextEntry }.map { it.name }.toList()
        }
        else -> emptyList()
    }
}
