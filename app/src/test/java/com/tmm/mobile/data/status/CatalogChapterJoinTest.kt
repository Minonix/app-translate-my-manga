package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.mihon.MihonNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

// Un chapitre jamais telecharge sur le telephone mais present sur le PC : le PC l'indexe sous le
// vrai dossier Mihon "MangaDex (EN)", la reconstruction cote telephone ne connait que "MangaDex".
class CatalogChapterJoinTest {
    private val chapter = ChapterEntity(
        chapterKey = "ck1", seriesKey = "sk1", url = "https://x/1", name = "Chapitre 1",
        number = 1f, scanlator = null, read = false, dateUpload = 0L,
    )
    private val chapterDir = MihonNaming.getChapterDirName("Chapitre 1", null, "https://x/1")

    @Test fun catalogChapter_findsPcIndexStoredUnderLangSuffixedSource() {
        val inputs = ChapterJoinInputs(
            chapters = listOf(chapter),
            downloaded = emptyList(),
            jobs = emptyList(),
            localFr = emptyList(),
            pcIndex = listOf(PcIndexChapterEntity("MangaDex (EN)/One Piece/$chapterDir", original = true, fr = false)),
        )
        val rows = buildChapterJoinRows("sk1", "MangaDex", "One Piece", inputs)
        assertEquals(1, rows.size)
        assertNotNull(rows.single().facts.index)
        assertEquals(1, deriveSeriesSummary("sk1", rows.toAggregateInputs(), emptySet()).onPc)
    }

    @Test fun loosePath_stripsOnlySourceLangSuffix() {
        assertEquals("MangaDex/Titre (FR)/Ch 1", loosePath("MangaDex (EN)/Titre (FR)/Ch 1"))
        assertEquals("MangaDex/Titre/Ch 1", loosePath("MangaDex/Titre/Ch 1"))
    }
}
