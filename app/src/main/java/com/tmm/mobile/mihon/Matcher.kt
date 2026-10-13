package com.tmm.mobile.mihon

// Rapprochement telechargements Mihon / catalogue (CAHIER_DES_CHARGES.md 8.5) :
// 1. source (nom de dossier source vs sourceName de la serie)
// 2. serie (titre nettoye vs dossier serie)
// 3. chapitre (scanlator_nom nettoye, sinon nom seul, vs dossier chapitre)
// 4. echec -> chapitre "hors catalogue" mais reste utilisable via mihonPath.
object Matcher {

    // La langue est ignoree ici : une meme serie suivie dans deux langues d'une meme source
    // ("MangaDex (EN)" et "MangaDex (FR)") est departagee chapitre par chapitre (assignChapters).
    fun matchSourceName(sourceDirName: String, sourceName: String): Boolean {
        val expected = MihonNaming.getSourceDirName(sourceName)
        return sourceDirName == expected || MihonNaming.stripSourceLang(sourceDirName) == expected
    }

    fun matchSeries(seriesDirName: String, seriesTitle: String): Boolean =
        seriesDirName == MihonNaming.getMangaDirName(seriesTitle)

    fun matchChapter(chapterDirName: String, chapterName: String, scanlator: String?, chapterUrl: String): Boolean =
        chapterDirName == MihonNaming.getChapterDirName(chapterName, scanlator, chapterUrl)

    /** Retrouve la SeriesEntity dont le nom de dossier (source + serie) correspond, ou null si "hors catalogue". */
    fun findSeries(sourceDirName: String, seriesDirName: String, series: List<MatchableSeries>): MatchableSeries? =
        findSeriesCandidates(sourceDirName, seriesDirName, series).firstOrNull()

    /**
     * Toutes les series dont le nom de dossier correspond : une serie ajoutee deux fois a la
     * bibliotheque, ou suivie en FR et en EN sur une meme source (la sauvegarde ne porte pas la
     * langue), partage le meme couple source/titre.
     */
    fun findSeriesCandidates(sourceDirName: String, seriesDirName: String, series: List<MatchableSeries>): List<MatchableSeries> =
        series.filter { matchSourceName(sourceDirName, it.sourceName) && matchSeries(seriesDirName, it.title) }

    /**
     * Rattache chaque dossier chapitre d'un dossier serie a l'une des series candidates. Le suffixe
     * de hachage du nom de dossier vient de l'URL du chapitre, propre a chaque copie de la serie :
     * la serie qui le connait l'emporte. Un dossier sans correspondance va a la candidate qui en
     * rattache le plus dans ce dossier (la premiere en cas d'egalite), comme "hors catalogue".
     */
    fun assignChapters(
        chapterDirNames: List<String>,
        candidates: List<Pair<MatchableSeries, List<MatchableChapter>>>,
    ): Map<String, ChapterMatch> {
        if (candidates.isEmpty()) return chapterDirNames.associateWith { ChapterMatch(null, null) }
        val exact = chapterDirNames.associateWith { name ->
            candidates.firstNotNullOfOrNull { (series, chapters) -> findChapter(name, chapters)?.let { ChapterMatch(series, it) } }
        }
        val fallback = candidates.maxBy { (series, _) -> exact.values.count { it?.series == series } }.first
        return exact.mapValues { (_, match) -> match ?: ChapterMatch(fallback, null) }
    }

    /** Retrouve la ChapterEntity dont le nom de dossier chapitre correspond, ou null si "hors catalogue". */
    fun findChapter(chapterDirName: String, chapters: List<MatchableChapter>): MatchableChapter? =
        chapters.firstOrNull { matchChapter(chapterDirName, it.name, it.scanlator, it.url) }
}

data class MatchableSeries(val seriesKey: String, val sourceName: String, val title: String)
data class MatchableChapter(val chapterKey: String, val name: String, val scanlator: String?, val url: String, val number: Float)
data class ChapterMatch(val series: MatchableSeries?, val chapter: MatchableChapter?)
