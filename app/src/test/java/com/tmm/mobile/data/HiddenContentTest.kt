package com.tmm.mobile.data

import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenContentTest {

    private fun dl(source: String, series: String, key: String?) = DownloadedChapterEntity(
        mihonPath = "$source/$series/ch1", sourceDir = source, seriesDir = series, chapterDir = "ch1",
        documentUri = "content://x", isCbz = true, sizeBytes = 1, lastModified = 0, pageCount = 1,
        seriesKey = key, chapterKey = null, number = 1f,
    )

    @Test
    fun rienDeMasque_donneUnEnsembleVide() {
        val h = buildHiddenSet(emptySet(), emptySet(), emptyList(), emptyList(), emptyList())
        assertTrue(h.isEmpty)
        assertFalse(h.hidesPath("src/serie/ch1"))
    }

    @Test
    fun categorieMasquee_masqueSesSeriesEtLeursDossiers() {
        val h = buildHiddenSet(
            hiddenCategories = setOf("Perso"),
            hiddenSeries = setOf("s3"),
            categories = listOf(CategoryEntity(1, "Perso"), CategoryEntity(2, "Public")),
            links = listOf(SeriesCategoryEntity("s1", 1), SeriesCategoryEntity("s2", 2)),
            downloaded = listOf(dl("src", "Serie1", "s1"), dl("src", "Serie2", "s2")),
        )
        assertEquals(setOf("s1", "s3"), h.seriesKeys)
        assertTrue(h.hidesSeries("s1"))
        assertFalse(h.hidesSeries("s2"))
        assertTrue(h.hidesPath("src/Serie1/ch1"))
        assertFalse(h.hidesPath("src/Serie2/ch1"))
        assertTrue(h.hidesDownloaded(dl("src", "Serie1", null)))
    }
}
