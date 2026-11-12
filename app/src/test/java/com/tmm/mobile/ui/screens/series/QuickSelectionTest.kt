package com.tmm.mobile.ui.screens.series

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSelectionTest {
    private val rows = listOf(
        SeriesRow(1, ChapterKind.InMihon, key = "a1"),
        SeriesRow(2, ChapterKind.ToTranslate, key = "a2"),
        SeriesRow(2, ChapterKind.ToTranslate, key = "b2"),
        SeriesRow(3, ChapterKind.Failed(null), key = "a3"),
        SeriesRow(4, ChapterKind.Ready, key = "a4"),
        SeriesRow(5, ChapterKind.Waiting, key = "a5"),
        SeriesRow(6, ChapterKind.NotDownloaded, key = "a6"),
        SeriesRow(7, ChapterKind.OnPc, key = "a7"),
    )

    // Deux editions du chapitre 2 : une seule cochee.
    private val same: (String) -> Set<String> = { k -> if (k.endsWith("2")) setOf("a2", "b2") else setOf(k) }

    @Test fun tout_ignore_verrouilles_et_non_telecharges_et_garde_une_edition() =
        assertEquals(setOf("a1", "a2", "a3", "a4", "a7"), quickSelection(rows, QuickSelect.All, same))

    @Test fun non_traduits() =
        assertEquals(setOf("a2", "a3", "a7"), quickSelection(rows, QuickSelect.Untranslated, same))

    @Test fun traduits() =
        assertEquals(setOf("a1", "a4"), quickSelection(rows, QuickSelect.Translated, same))
}
