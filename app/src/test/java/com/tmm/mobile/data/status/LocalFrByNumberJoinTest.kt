package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.mihon.MihonNaming
import org.junit.Assert.assertEquals
import org.junit.Test

// Cas reel (telephone, 2026-11) : « The Frontier Lord Begins with Zero Subjects » (source Comix).
// Les FR de local/ ont ete traduits depuis des originaux du PC, pas depuis le telephone : aucun
// mihonPath. TMM affichait « A traduire » ou « Pas telecharge » pour 51, 60, 63, 64, 65.
class LocalFrByNumberJoinTest {
    private val title = "The Frontier Lord Begins with Zero Subjects - Tales of Blue Dias and the Onikin Alna"
    private val source = "Comix"
    private val sourceDir = "Comix (EN)"

    private fun chapter(number: Float, scanlator: String) = ChapterEntity(
        chapterKey = "$scanlator-$number", seriesKey = "sk", url = "/$scanlator/$number",
        name = "Chapter ${number.toString().removeSuffix(".0")}", number = number, scanlator = scanlator,
        read = false, dateUpload = 0L,
    )

    private fun downloaded(c: ChapterEntity): DownloadedChapterEntity {
        val dir = MihonNaming.getChapterDirName(c.name, c.scanlator, c.url) + ".cbz"
        return DownloadedChapterEntity(
            mihonPath = "$sourceDir/$title/$dir", sourceDir = sourceDir, seriesDir = title, chapterDir = dir,
            documentUri = "doc:$dir", isCbz = true, sizeBytes = 1, lastModified = 0, pageCount = 1,
            seriesKey = "sk", chapterKey = c.chapterKey, number = c.number,
        )
    }

    private fun fr(fileName: String, folder: String = title) = LocalFrChapterEntity(
        localPath = "$folder/$fileName", folderName = folder, fileName = fileName,
        documentUri = "doc:$fileName", lastModified = 0L, jobId = null, mihonPath = null,
    )

    private val k51 = chapter(51f, "KaynScan")
    private val o51 = chapter(51f, "Official")
    private val k60 = chapter(60f, "KaynScan")
    private val o61 = chapter(61f, "Official")
    private val k61a = chapter(61.1f, "KaynScan")
    private val k61b = chapter(61.2f, "KaynScan")
    private val o63 = chapter(63f, "Official")
    private val j63 = chapter(63f, "J-Novel Club")
    private val o64 = chapter(64f, "Official")
    private val o65 = chapter(65f, "Official")
    private val k66a = chapter(66.1f, "KaynScan")
    private val k66b = chapter(66.2f, "KaynScan")
    private val k74a = chapter(74.1f, "KaynScan")
    private val k74b = chapter(74.2f, "KaynScan")
    private val o75 = chapter(75.5f, "Official")
    private val catalog = listOf(k51, o51, k60, o61, k61a, k61b, o63, j63, o64, o65, k66a, k66b, k74a, k74b, o75)

    private fun rows(localFr: List<LocalFrChapterEntity>): List<ChapterJoinRow> = buildChapterJoinRows(
        "sk", source, title,
        ChapterJoinInputs(
            chapters = catalog,
            downloaded = listOf(downloaded(k51), downloaded(k61a), downloaded(k61b), downloaded(k66a), downloaded(k66b), downloaded(k74a)),
            jobs = emptyList(),
            localFr = localFr,
            pcIndex = emptyList(),
        ),
    )

    private val frontierFr = listOf(
        fr("c0051 - Chapter 51.cbz"), fr("c0060 - Chapter 60.cbz"), fr("c0061 - Chapter 61.cbz"),
        fr("c0063 - Chapter 63.cbz"), fr("c0064 - Chapter 64.cbz"), fr("c0065 - Chapter 65.cbz"),
        fr("c0066.1 - Chapter 66.1.cbz"), fr("c0075 - Chapter 75.cbz"), fr("cover.jpg"),
    )

    private fun statusByNumber(rows: List<ChapterJoinRow>): Map<Float, ChapterStatus> =
        rows.associate { (it.chapter?.number ?: it.downloaded!!.number!!) to it.status }

    @Test fun frInLocalWithoutMihonPath_marksCatalogAndDownloadedChaptersUpToDate() {
        val status = statusByNumber(rows(frontierFr))
        // 51 telecharge, 60/63/64/65 seulement au catalogue : la FR existe dans local/.
        for (n in listOf(51f, 60f, 63f, 64f, 65f)) assertEquals("ch. $n", ChapterStatus.UP_TO_DATE, status[n])
    }

    // Le PC a traduit l'edition entiere (Official 61) ; KaynScan a decoupe ce chapitre en 61.1 / 61.2.
    @Test fun wholeFr_coversSplitParts_partFrCoversOnlyItself() {
        val status = statusByNumber(rows(frontierFr))
        assertEquals(ChapterStatus.UP_TO_DATE, status[61f])
        assertEquals(ChapterStatus.UP_TO_DATE, status[61.1f])
        assertEquals(ChapterStatus.UP_TO_DATE, status[61.2f])
        // FR 66.1 seule : 66.2 reste a traduire.
        assertEquals(ChapterStatus.UP_TO_DATE, status[66.1f])
        assertEquals(ChapterStatus.TO_SEND, status[66.2f])
        // Aucune FR 74 : rien ne change.
        assertEquals(ChapterStatus.TO_SEND, status[74.1f])
        assertEquals(ChapterStatus.CATALOG_ONLY, status[74.2f])
        // 75.5 est un hors-serie, pas une partie de 75.
        assertEquals(ChapterStatus.CATALOG_ONLY, status[75.5f])
    }

    // Plusieurs editions du 63 (Official, J-Novel) devenues « A jour » ne doivent pas apparaitre deux fois.
    @Test fun frByNumber_doesNotAddRows() {
        assertEquals(rows(emptyList()).size, rows(frontierFr).size)
    }

    // Dossier local/ ecrit avec une autre ponctuation ou casse que le titre de la sauvegarde.
    @Test fun localFolderNameComparedLoosely() {
        val loose = listOf(fr("c0060 - Chapter 60.cbz", folder = "the frontier lord begins with zero subjects_ tales of blue dias and the onikin alna"))
        assertEquals(ChapterStatus.UP_TO_DATE, statusByNumber(rows(loose))[60f])
    }

    @Test fun frOfAnotherSeries_isIgnored() {
        val other = listOf(fr("c0060 - Chapter 60.cbz", folder = "See You at the Pepper Field!"))
        assertEquals(ChapterStatus.CATALOG_ONLY, statusByNumber(rows(other))[60f])
    }
}
