package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
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

    // Catalogue "official" + "unofficial" (65 entrees pour 43 numeros) : la Bibliotheque comptait 65,
    // la Fiche serie 43. La jointure commune ne garde qu'une ligne par numero.
    @Test fun duplicateCatalogNumbers_countedOnce() {
        val other = chapter.copy(chapterKey = "ck1b", url = "https://x/1b", scanlator = "official")
        val inputs = ChapterJoinInputs(listOf(chapter, other), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(1, buildChapterJoinRows("sk1", "MangaDex", "One Piece", inputs).size)
    }

    // Mihon telecharge en .cbz : le PC indexe "Chapter 47_8a1b52.cbz" et le job/la FR installee portent
    // ce nom. Une fois le telechargement EN supprime (lu, « supprimer apres lecture »), la reconstruction
    // sans ".cbz" ne retrouvait plus rien : « Non telechargee » au lieu de « Pret a installer » ou
    // « Dans Mihon », et le chapitre ne pouvait plus etre installe (Invincible at the Start, ch. 33/47/58).
    @Test fun catalogChapter_findsPcIndexStoredUnderCbzName() {
        val inputs = ChapterJoinInputs(
            chapters = listOf(chapter),
            downloaded = emptyList(),
            jobs = emptyList(),
            localFr = emptyList(),
            pcIndex = listOf(PcIndexChapterEntity("MangaDex (EN)/One Piece/$chapterDir.cbz", original = false, fr = true)),
        )
        val row = buildChapterJoinRows("sk1", "MangaDex", "One Piece", inputs).single()
        assertEquals(ChapterStatus.TO_FETCH, row.status)
        // « Installer » redemande le chapitre au PC sous le chemin qu'il connait, pas la reconstruction.
        assertEquals("MangaDex (EN)/One Piece/$chapterDir.cbz", row.flatPath)
    }

    @Test fun catalogChapter_findsInstalledFrLinkedToCbzName() {
        val fr = LocalFrChapterEntity(
            localPath = "One Piece (FR)/c0001.cbz", folderName = "One Piece (FR)", fileName = "c0001.cbz",
            documentUri = "doc", lastModified = 0L, jobId = "j1", mihonPath = "MangaDex (EN)/One Piece/$chapterDir.cbz",
        )
        val inputs = ChapterJoinInputs(listOf(chapter), emptyList(), emptyList(), listOf(fr), emptyList())
        assertEquals(ChapterStatus.UP_TO_DATE, buildChapterJoinRows("sk1", "MangaDex", "One Piece", inputs).single().status)
    }

    @Test fun loosePath_stripsOnlySourceLangSuffix() {
        assertEquals("MangaDex/Titre (FR)/Ch 1", loosePath("MangaDex (EN)/Titre (FR)/Ch 1"))
        assertEquals("MangaDex/Titre/Ch 1", loosePath("MangaDex/Titre/Ch 1"))
        assertEquals("MangaDex/Titre/Ch 1_ab12cd", loosePath("MangaDex (EN)/Titre/Ch 1_ab12cd.cbz"))
    }
}
