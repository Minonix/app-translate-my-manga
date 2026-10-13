package com.tmm.mobile.mihon

import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.SeriesEntity

/**
 * Serie vue seulement dans downloads/ : absente de la sauvegarde Mihon (ajoutee apres la derniere
 * sauvegarde auto, ou jamais mise en bibliotheque). Elle recoit sa propre SeriesEntity, cle par couple
 * de dossiers : la version EN et la version FR d'une meme serie ne se melangent jamais, et une
 * sauvegarde qui la contient ensuite la remplace (DownloadScanner.rematch).
 *
 * ponytail: a ce remplacement, les jobs et reglages (suivi, langue cible) poses sous la cle
 * "downloads|" ne sont pas reportes sur la vraie serie ; a migrer si cela gene en pratique.
 */
object DownloadOnlySeries {
    // Une vraie cle commence par l'id numerique de la source : aucune collision possible.
    private const val PREFIX = "downloads|"

    fun key(sourceDir: String, seriesDir: String): String = "$PREFIX$sourceDir|$seriesDir"

    fun isDownloadOnly(seriesKey: String?): Boolean = seriesKey?.startsWith(PREFIX) == true

    /** sourceId/url vides : SendJobWorker les envoie a null, comme pour un chapitre hors catalogue. */
    fun entity(rows: List<DownloadedChapterEntity>): SeriesEntity {
        val first = rows.first()
        return SeriesEntity(
            seriesKey = key(first.sourceDir, first.seriesDir),
            sourceId = "",
            url = "",
            title = first.seriesDir,
            author = null,
            status = 0,
            thumbnailUrl = null,
            sourceName = first.sourceDir,
            dateAdded = rows.maxOf { it.lastModified },
            mangaType = null,
        )
    }

    private val HASH_SUFFIX = Regex("_[0-9a-f]{6}$")
    private val KEYWORD_NUMBER = Regex("""(?i)(?:chap(?:ter|itre)?|ch|ep(?:isode)?)\.?\s*(\d+(?:[.,]\d+)?)""")
    private val ANY_NUMBER = Regex("""\d+(?:[.,]\d+)?""")

    /**
     * Numero lu dans le nom de dossier ("Nyx Scans_Chapter 29_816b9c" -> 29), sans catalogue pour le
     * donner. ponytail: heuristique simple (mot-cle chapitre, sinon dernier nombre) ; reprendre
     * ChapterRecognition de Mihon si des noms exotiques donnent de mauvais numeros.
     */
    fun guessNumber(chapterDirName: String): Float? {
        val name = chapterDirName.removeSuffix(".cbz").replace(HASH_SUFFIX, "")
        val raw = KEYWORD_NUMBER.findAll(name).lastOrNull()?.groupValues?.get(1)
            ?: ANY_NUMBER.findAll(name).lastOrNull()?.value
        return raw?.replace(',', '.')?.toFloatOrNull()
    }

    /**
     * Scantrad lue dans le nom de dossier : ce qui precede le mot-cle chapitre ("Nyx Scans_Chapter 29_816b9c"
     * -> "Nyx Scans", le ":" de Mihon devenu "_"). Null sans mot-cle ou sans prefixe ("Chapter 5_ Titre").
     */
    fun guessScanlator(chapterDirName: String): String? {
        val name = chapterDirName.removeSuffix(".cbz").replace(HASH_SUFFIX, "")
        val start = KEYWORD_NUMBER.find(name)?.range?.first ?: return null
        return name.substring(0, start).trim('_', ' ').takeIf { it.isNotEmpty() }
    }
}
