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
}
