package com.tmm.mobile.mihon

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadOnlySeriesTest {
    @Test fun guessNumber_readsMihonTeamPrefixedName() {
        assertEquals(22f, DownloadOnlySeries.guessNumber("unofficial_Ch. 22_14b780.cbz"))
        assertEquals(29f, DownloadOnlySeries.guessNumber("Nyx Scans_Chapter 29_816b9c"))
    }

    // "ch"/"ep" au milieu d'un mot du titre ne sont pas des mots-cles chapitre.
    @Test fun guessNumber_ignoresKeywordInsideWordsAndLaterEpisode() {
        assertEquals(22f, DownloadOnlySeries.guessNumber("Ch. 22 - The Watch 2_14b780"))
        assertEquals(22f, DownloadOnlySeries.guessNumber("Chapter 22 - Episode 3_14b780"))
    }
}
