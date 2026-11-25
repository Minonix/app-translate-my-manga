package com.tmm.mobile.mihon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFrNumbersTest {
    @Test fun chapterNumber_readsPcFileNamesOnly() {
        assertEquals(33f, localFrChapterNumber("c0033 - Ch. 33.cbz"))
        assertEquals(14.1f, localFrChapterNumber("c0014.1 - Ch. 14.1.cbz"))
        assertEquals(0f, localFrChapterNumber("c0000 - Chapter 0.cbz"))
        assertNull(localFrChapterNumber("cover.jpg"))
        assertNull(localFrChapterNumber("notes.cbz"))
        assertNull(localFrChapterNumber(".c0033 - Ch. 33.cbz.tmm-j1.tmp"))
    }

    @Test fun covers_exactWholeAndSplitParts() {
        val fr = setOf(61f, 66.1f)
        assertTrue(localFrCovers(fr, 61f))
        assertTrue(localFrCovers(fr, 61.1f))
        assertTrue(localFrCovers(fr, 61.2f))
        assertTrue(localFrCovers(fr, 61.4f))
        // Hors-serie, pas une partie.
        assertFalse(localFrCovers(fr, 61.5f))
        assertFalse(localFrCovers(fr, 61.25f))
        // Une FR partielle ne couvre qu'elle-meme.
        assertTrue(localFrCovers(fr, 66.1f))
        assertFalse(localFrCovers(fr, 66.2f))
        assertFalse(localFrCovers(fr, 66f))
        assertFalse(localFrCovers(fr, -1f))
    }

    @Test fun numbersByFolder_keyedByLooseFolderName() {
        val index = LocalFrNumbers(
            localFrNumbersByFolder(
                listOf(
                    "See You at the Pepper Field!" to "c0025 - Chapter 25.cbz",
                    "See You at the Pepper Field!" to "cover.jpg",
                ),
            ),
        )
        assertTrue(index.covers(25f, "see you at the pepper field"))
        assertTrue(index.covers(25f, null, "See You at the Pepper Field_"))
        assertFalse(index.covers(25f, "See You in the Chili Pepper Field!"))
        assertFalse(index.covers(26f, "See You at the Pepper Field!"))
        assertFalse(index.covers(null, "See You at the Pepper Field!"))
    }

    // Un accent decompose (NFD, stockage) et compose (NFC, sauvegarde) donnent la meme cle.
    @Test fun folderKey_isNfcNormalized() {
        val nfd = java.text.Normalizer.normalize("Réincarné", java.text.Normalizer.Form.NFD)
        assertEquals(localFolderKey("Réincarné"), localFolderKey(nfd))
    }

    @Test fun safCopySuffix_strippedOnlyAfterMihonHash() {
        assertEquals("KaynScan_Chapter 5_a1b2c3.cbz", MihonNaming.stripSafCopySuffix("KaynScan_Chapter 5_a1b2c3 (1).cbz"))
        assertEquals("KaynScan_Chapter 5_a1b2c3", MihonNaming.stripSafCopySuffix("KaynScan_Chapter 5_a1b2c3 (12)"))
        assertEquals("Titre (2)", MihonNaming.stripSafCopySuffix("Titre (2)"))
        assertTrue(MihonNaming.isSafCopy("x_a1b2c3 (1).cbz"))
        assertFalse(MihonNaming.isSafCopy("x_a1b2c3.cbz"))
    }
}
