package com.tmm.mobile.mihon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatcherTest {
    private val series = listOf(
        MatchableSeries("sk1", "MangaSource", "One Piece"),
        MatchableSeries("sk2", "MangaSource", "Naruto"),
    )
    private val chapters = listOf(
        MatchableChapter("ck1", "Chapitre 1", null, "https://x/1", 1f),
        MatchableChapter("ck2", "Chapitre 2", "TeamX", "https://x/2", 2f),
    )

    @Test fun findSeries_matchesOnSourceAndTitle() {
        val result = Matcher.findSeries("MangaSource", "One Piece", series)
        assertEquals("sk1", result?.seriesKey)
    }

    @Test fun findSeries_returnsNull_horsCatalogue() {
        assertNull(Matcher.findSeries("MangaSource", "Bleach", series))
    }

    @Test fun findChapter_matchesByGeneratedDirName() {
        val dirName = MihonNaming.getChapterDirName("Chapitre 2", "TeamX", "https://x/2")
        val result = Matcher.findChapter(dirName, chapters)
        assertEquals("ck2", result?.chapterKey)
    }

    @Test fun findChapter_returnsNull_whenNoDirMatches() {
        assertNull(Matcher.findChapter("dossier-inconnu", chapters))
    }

    // Mihon range ses telechargements sous "Nom (LANGUE)" alors que la sauvegarde ne porte que "Nom".
    @Test fun findSeries_ignoresMihonLangSuffixOnSourceDir() {
        assertEquals("sk1", Matcher.findSeries("MangaSource (EN)", "One Piece", series)?.seriesKey)
        assertEquals("sk2", Matcher.findSeries("MangaSource (PT-BR)", "Naruto", series)?.seriesKey)
        assertEquals("sk1", Matcher.findSeries("MangaSource (ALL)", "One Piece", series)?.seriesKey)
        assertEquals("sk1", Matcher.findSeries("MangaSource (ES-419)", "One Piece", series)?.seriesKey)
    }

    @Test fun findSeries_otherSourceWithSuffix_stillNull() {
        assertNull(Matcher.findSeries("AutreSource (EN)", "One Piece", series))
    }

    @Test fun findSeries_sourceNameWithParentheses_exactMatchFirst() {
        val withParens = listOf(MatchableSeries("sk3", "Source (beta)", "One Piece"))
        assertEquals("sk3", Matcher.findSeries("Source (beta)", "One Piece", withParens)?.seriesKey)
        assertEquals("sk3", Matcher.findSeries("Source (beta) (FR)", "One Piece", withParens)?.seriesKey)
    }

    @Test fun assignChapters_unmatchedFolderGoesToCandidateWithMostMatches() {
        val a = MatchableSeries("a", "MangaSource", "One Piece")
        val b = MatchableSeries("b", "MangaSource", "One Piece")
        val chB = MatchableChapter("b1", "Chapitre 1", null, "/b/1", 1f)
        val dir = MihonNaming.getChapterDirName("Chapitre 1", null, "/b/1")
        val result = Matcher.assignChapters(listOf(dir, "inconnu"), listOf(a to emptyList(), b to listOf(chB)))
        assertEquals(ChapterMatch(b, chB), result[dir])
        assertEquals(ChapterMatch(b, null), result["inconnu"])
    }

    @Test fun assignChapters_noCandidate_horsCatalogue() {
        assertEquals(ChapterMatch(null, null), Matcher.assignChapters(listOf("x"), emptyList())["x"])
    }
}
