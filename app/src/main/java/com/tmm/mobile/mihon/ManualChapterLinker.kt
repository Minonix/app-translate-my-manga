package com.tmm.mobile.mihon

import androidx.room.withTransaction
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.ManualChapterLinkEntity
import com.tmm.mobile.data.db.TmmDatabase
import java.io.IOException
import kotlinx.coroutines.flow.first

/** Chapitre de la Fiche serie a relier : `chapterKey` null pour une ligne « manquante » (numero seul). */
data class LinkTarget(val seriesKey: String, val chapterKey: String?, val number: Float)

/** Resultat de la verification d'un fichier choisi, avant de le relier. */
sealed interface LinkCheck {
    /** Le fichier peut etre relie (`replacing` : il etait relie a un autre chapitre, a confirmer). */
    data class Ok(val file: PickedFile, val replacing: LinkedElsewhere?) : LinkCheck
    data object OutsideDownloads : LinkCheck
    data object NotCbz : LinkCheck
    data object NoPages : LinkCheck
    data object AlreadyLinked : LinkCheck
}

/** Chapitre auquel le fichier etait deja relie : `seriesTitle` seulement s'il s'agit d'une autre serie. */
data class LinkedElsewhere(val number: Float?, val seriesTitle: String?)

data class PickedFile(val row: DownloadedChapterEntity)

/**
 * Bouton dossier de la Fiche serie : relie a la main un fichier telecharge par Mihon a un chapitre que
 * le rapprochement automatique a rate. Le lien est garde dans manual_chapter_link, que le scan et le
 * rematch appliquent ensuite en priorite (DownloadScanner.withLink).
 *
 * ponytail: les docId SAF du stockage partage (ExternalStorageProvider) sont des chemins
 * ("primary:Mihon/downloads/Source/Serie/Chapitre.cbz") : on en deduit source/serie/fichier par prefixe.
 * Un autre fournisseur (carte SD exotique, Files by Google) est refuse comme « hors du dossier Mihon ».
 */
class ManualChapterLinker(private val fs: MihonFs, private val rootDocId: String, private val db: TmmDatabase) {

    /** Dossier ou ouvrir le selecteur : celui de la serie s'il existe, sinon downloads/. */
    suspend fun startDirDocId(seriesKey: String): String? {
        val downloads = fs.findChild(rootDocId, "downloads") ?: return null
        val known = db.downloadedChapterDao().observeForSeries(seriesKey).first().firstOrNull()
        if (known != null) {
            val parent = known.documentUri.substringBeforeLast('/', "")
            if (parent.startsWith(downloads.docId + "/")) return parent
        }
        val series = db.seriesDao().get(seriesKey) ?: return downloads.docId
        val seriesDirName = MihonNaming.getMangaDirName(series.title)
        val sourceDir = fs.children(downloads.docId)
            .filter { it.isDirectory }
            .firstOrNull { Matcher.matchSourceName(it.name, series.sourceName) && fs.findChild(it.docId, seriesDirName) != null }
            ?: return downloads.docId
        return fs.findChild(sourceDir.docId, seriesDirName)?.docId ?: sourceDir.docId
    }

    suspend fun check(target: LinkTarget, pickedDocId: String): LinkCheck {
        val downloads = fs.findChild(rootDocId, "downloads") ?: return LinkCheck.OutsideDownloads
        val parts = pickedDocId.removePrefix(downloads.docId + "/").split('/')
        if (!pickedDocId.startsWith(downloads.docId + "/") || parts.size != 3) return LinkCheck.OutsideDownloads
        val (sourceDir, seriesDir, fileName) = parts
        if (!fileName.endsWith(".cbz")) return LinkCheck.NotCbz
        val entry = fs.children(pickedDocId.substringBeforeLast('/')).firstOrNull { it.docId == pickedDocId }
            ?: return LinkCheck.OutsideDownloads
        val mihonPath = "$sourceDir/$seriesDir/$fileName"
        val existing = db.downloadedChapterDao().get(mihonPath)
        // Diagnostic : ce que le scan avait fait de ce fichier (absent, sans chapitre, autre chapitre).
        AppLog.d(
            "Lien",
            "fichier choisi : $mihonPath, avant : " +
                (existing?.let { "serie=${it.seriesKey} chapitre=${it.chapterKey} n°${it.number} pages=${it.pageCount}" } ?: "jamais vu par le scan"),
        )
        if (existing != null && isSame(existing, target)) return LinkCheck.AlreadyLinked
        val pageCount = existing?.pageCount?.takeIf { it > 0 }
            ?: try {
                CbzPacker.pageCount(DownloadScanner(fs, db).entryNames(entry, isCbz = true))
            } catch (e: IOException) {
                AppLog.d("Lien", "fichier illisible : ${e.javaClass.simpleName} ${e.message}")
                0
            }
        if (pageCount == 0) return LinkCheck.NoPages
        val replacing = existing?.takeIf { it.chapterKey != null || it.seriesKey == target.seriesKey }?.let {
            val title = it.seriesKey?.takeIf { key -> key != target.seriesKey }?.let { key -> db.seriesDao().get(key)?.title }
            LinkedElsewhere(it.number, title)
        }
        val row = DownloadedChapterEntity(
            mihonPath = mihonPath,
            sourceDir = sourceDir,
            seriesDir = seriesDir,
            chapterDir = fileName,
            documentUri = pickedDocId,
            isCbz = true,
            sizeBytes = entry.size,
            lastModified = entry.lastModified,
            pageCount = pageCount,
            seriesKey = target.seriesKey,
            chapterKey = target.chapterKey,
            number = target.number,
        )
        return LinkCheck.Ok(PickedFile(row), replacing)
    }

    suspend fun link(file: PickedFile) {
        val row = file.row
        db.withTransaction {
            db.manualChapterLinkDao().upsert(ManualChapterLinkEntity(row.mihonPath, row.seriesKey!!, row.chapterKey, row.number!!))
            db.downloadedChapterDao().upsert(row)
        }
        AppLog.d("Lien", "relie : ${row.mihonPath} -> serie=${row.seriesKey} chapitre=${row.chapterKey} n°${row.number}")
    }

    private fun isSame(row: DownloadedChapterEntity, target: LinkTarget): Boolean =
        row.seriesKey == target.seriesKey &&
            if (target.chapterKey != null) row.chapterKey == target.chapterKey else row.chapterKey == null && row.number == target.number
}
