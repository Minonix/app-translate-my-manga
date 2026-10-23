package com.tmm.mobile.mihon

import com.tmm.mobile.core.nfc

// Rapprochement telechargements Mihon / catalogue (CAHIER_DES_CHARGES.md 8.5) :
// 1. source (nom de dossier source vs sourceName de la serie)
// 2. serie (titre nettoye vs dossier serie)
// 3. chapitre (scanlator_nom nettoye, sinon nom seul, vs dossier chapitre)
// 4. echec -> chapitre "hors catalogue" mais reste utilisable via mihonPath.
// Les noms sont compares en NFC : le stockage peut rendre « スパ » decompose (NFD) alors que la sauvegarde
// le garde compose, et la serie n'etait jamais rattachee.
object Matcher {

    // La langue est ignoree ici : une meme serie suivie dans deux langues d'une meme source
    // ("MangaDex (EN)" et "MangaDex (FR)") est departagee chapitre par chapitre (assignChapters).
    fun matchSourceName(sourceDirName: String, sourceName: String): Boolean {
        val expected = nfc(MihonNaming.getSourceDirName(sourceName))
        val dir = nfc(sourceDirName)
        return dir == expected || MihonNaming.stripSourceLang(dir) == expected
    }

    fun matchSeries(seriesDirName: String, seriesTitle: String): Boolean =
        nfc(seriesDirName) == nfc(MihonNaming.getMangaDirName(seriesTitle))

    fun matchChapter(chapterDirName: String, chapterName: String, scanlator: String?, chapterUrl: String): Boolean =
        nfc(chapterDirName) in MihonNaming.getValidChapterDirNames(nfc(chapterName), scanlator?.let(::nfc), chapterUrl)

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
        // Rattrapage : un chapitre dont l'URL ou le titre a change depuis la sauvegarde (hachage different)
        // restait "Pas telecharge" alors que son CBZ est la. On le rattache par numero, seulement si un seul
        // chapitre du catalogue porte ce numero et qu'aucun autre dossier ne l'a deja pris.
        val taken = exact.values.mapNotNullTo(HashSet()) { it?.chapter?.chapterKey }
        val fallbackChapters = candidates.first { it.first == fallback }.second
        return exact.mapValues { (name, match) ->
            match ?: ChapterMatch(fallback, findByNumber(name, fallbackChapters, taken)?.also { taken += it.chapterKey })
        }
    }

    private fun findByNumber(dirName: String, chapters: List<MatchableChapter>, taken: Set<String>): MatchableChapter? {
        val number = DownloadOnlySeries.guessNumber(dirName) ?: return null
        return chapters.filter { it.number == number }.singleOrNull()?.takeIf { it.chapterKey !in taken }
    }

    /** Retrouve la ChapterEntity dont le nom de dossier chapitre correspond, ou null si "hors catalogue". */
    fun findChapter(chapterDirName: String, chapters: List<MatchableChapter>): MatchableChapter? =
        chapters.firstOrNull { matchChapter(chapterDirName, it.name, it.scanlator, it.url) }
}

data class MatchableSeries(val seriesKey: String, val sourceName: String, val title: String)
data class MatchableChapter(val chapterKey: String, val name: String, val scanlator: String?, val url: String, val number: Float)
data class ChapterMatch(val series: MatchableSeries?, val chapter: MatchableChapter?)
