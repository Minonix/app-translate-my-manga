package com.tmm.mobile.mihon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonNamingTest {
    @Test fun buildValidFilename_replacesInvalidChars() {
        assertEquals("a_b_c", MihonNaming.buildValidFilename("a/b:c"))
    }

    @Test fun buildValidFilename_trimsLeadingTrailingDotsAndSpaces() {
        assertEquals("name", MihonNaming.buildValidFilename("  name. "))
    }

    @Test fun truncateToLength_keepsUtf8Valid() {
        val truncated = MihonNaming.truncateToLength("cafécafé", 6)
        assertTrue(truncated.toByteArray(Charsets.UTF_8).size <= 6)
    }

    @Test fun getChapterDirName_appendsMd5HashSuffix() {
        val name = MihonNaming.getChapterDirName("Chapitre 1", "TeamX", "https://example.com/ch1")
        // Format Mihon : "team_nom_hachage", sans espace (fichier reel : "unofficial_Ch. 22_14b780.cbz").
        assertTrue(name.startsWith("TeamX_Chapitre 1_"))
        assertEquals(6, name.substringAfterLast('_').length)
    }

    @Test fun getChapterDirName_noScanlator_usesNameOnly() {
        val name = MihonNaming.getChapterDirName("Chapitre 1", null, "https://example.com/ch1")
        assertTrue(name.startsWith("Chapitre 1_"))
    }

    @Test fun getValidChapterDirNames_acceptsOldColonSeparatorAndNoHash() {
        val names = MihonNaming.getValidChapterDirNames("Chapitre 1", "TeamX", "https://example.com/ch1")
        assertTrue(names.any { it.startsWith("TeamX_ Chapitre 1_") })
        assertTrue("TeamX_Chapitre 1" in names)
    }

    @Test fun isIncompleteChapterDirName_detectsTmpSuffix() {
        assertTrue(MihonNaming.isIncompleteChapterDirName("Chapitre 1_abcdef_tmp"))
        assertTrue(!MihonNaming.isIncompleteChapterDirName("Chapitre 1_abcdef"))
    }
}
