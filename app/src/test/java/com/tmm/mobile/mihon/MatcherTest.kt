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

    // Scenario reel : CBZ "Chapter 103" telecharge mais URL differente de la sauvegarde -> "Pas telecharge".
    @Test fun assignChapters_hashMismatch_matchesByUniqueNumber() {
        val s = MatchableSeries("s", "MangaDot", "Off to an Invincible Start")
        val ch103 = MatchableChapter("c103", "Chapter 103: Hanlan Pavilion", "BRS Manhua", "/new-url", 103f)
        val ch102 = MatchableChapter("c102", "Chapter 102", "BRS Manhua", "/102", 102f)
        val dir102 = MihonNaming.getChapterDirName("Chapter 102", "BRS Manhua", "/102")
        val dir103 = "BRS Manhua_Chapter 103_ Hanlan Pavilion_46aaec.cbz"
        val result = Matcher.assignChapters(listOf(dir102, dir103, "BRS Manhua_Chapter 102_ copie_ffffff"), listOf(s to listOf(ch102, ch103)))
        assertEquals(ChapterMatch(s, ch103), result[dir103])
        assertEquals(ChapterMatch(s, ch102), result[dir102])
        assertEquals(ChapterMatch(s, null), result["BRS Manhua_Chapter 102_ copie_ffffff"])
    }

    @Test fun assignChapters_noCandidate_horsCatalogue() {
        assertEquals(ChapterMatch(null, null), Matcher.assignChapters(listOf("x"), emptyList())["x"])
    }

    // Le stockage peut rendre un nom japonais decompose (NFD) : « スパ » = ス + ハ + U+309A.
    @Test fun findSeries_matchesDecomposedJapaneseDirName() {
        val japanese = listOf(MatchableSeries("jp", "MangaSource", "スパイ教室"))
        val decomposed = java.text.Normalizer.normalize("スパイ教室", java.text.Normalizer.Form.NFD)
        assertEquals(false, decomposed == "スパイ教室")
        assertEquals("jp", Matcher.findSeries("MangaSource", decomposed, japanese)?.seriesKey)
    }

    @Test fun findChapter_matchesDecomposedChapterDirName() {
        val jp = listOf(MatchableChapter("c1", "第1話 パン", null, "https://x/p1", 1f))
        val dir = java.text.Normalizer.normalize(MihonNaming.getChapterDirName("第1話 パン", null, "https://x/p1"), java.text.Normalizer.Form.NFD)
        assertEquals("c1", Matcher.findChapter(dir, jp)?.chapterKey)
    }
}
