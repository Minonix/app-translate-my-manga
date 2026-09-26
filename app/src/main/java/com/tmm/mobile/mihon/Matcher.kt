package com.tmm.mobile.mihon

// Rapprochement telechargements Mihon / catalogue (CAHIER_DES_CHARGES.md 8.5) :
// 1. source (nom de dossier source vs sourceName de la serie)
// 2. serie (titre nettoye vs dossier serie)
// 3. chapitre (scanlator_nom nettoye, sinon nom seul, vs dossier chapitre)
// 4. echec -> chapitre "hors catalogue" mais reste utilisable via mihonPath.
object Matcher {

    fun matchSourceName(sourceDirName: String, sourceName: String): Boolean =
        sourceDirName == MihonNaming.getSourceDirName(sourceName)

    fun matchSeries(seriesDirName: String, seriesTitle: String): Boolean =
        seriesDirName == MihonNaming.getMangaDirName(seriesTitle)

    fun matchChapter(chapterDirName: String, chapterName: String, scanlator: String?, chapterUrl: String): Boolean =
        chapterDirName == MihonNaming.getChapterDirName(chapterName, scanlator, chapterUrl)

    /** Retrouve la SeriesEntity dont le nom de dossier (source + serie) correspond, ou null si "hors catalogue". */
    fun findSeries(sourceDirName: String, seriesDirName: String, series: List<MatchableSeries>): MatchableSeries? =
        series.firstOrNull { matchSourceName(sourceDirName, it.sourceName) && matchSeries(seriesDirName, it.title) }

    /** Retrouve la ChapterEntity dont le nom de dossier chapitre correspond, ou null si "hors catalogue". */
    fun findChapter(chapterDirName: String, chapters: List<MatchableChapter>): MatchableChapter? =
        chapters.firstOrNull { matchChapter(chapterDirName, it.name, it.scanlator, it.url) }
}

data class MatchableSeries(val seriesKey: String, val sourceName: String, val title: String)
data class MatchableChapter(val chapterKey: String, val name: String, val scanlator: String?, val url: String, val number: Float)
