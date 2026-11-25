package com.tmm.mobile.data

import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.SeriesLinkEntity
import com.tmm.mobile.data.status.ChapterJoinInputs
import com.tmm.mobile.data.status.ChapterStatus
import com.tmm.mobile.data.status.buildChapterJoinRows
import com.tmm.mobile.mihon.DownloadOnlySeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Cas reel (2026-11) : « See You at the Pepper Field! » (AllManga, en bibliotheque, 25-38 traduits dans
// local/See You at the Pepper Field!) et « See You in the Chili Pepper Field! » (Comix, hors bibliotheque,
// Eris Scans 39-42 telecharges). Meme oeuvre, deux titres : liaison manuelle, jamais automatique.
class SeriesLinksTest {
    private val allMangaTitle = "See You at the Pepper Field!"
    private val comixTitle = "See You in the Chili Pepper Field!"
    private val sourceDir = "Comix (EN)"
    private val downloadKey = DownloadOnlySeries.key(sourceDir, comixTitle)

    private fun downloaded(number: Int, seriesKey: String, chapterKey: String? = null) = DownloadedChapterEntity(
        mihonPath = "$sourceDir/$comixTitle/Eris Scans_Chapter $number.cbz", sourceDir = sourceDir, seriesDir = comixTitle,
        chapterDir = "Eris Scans_Chapter $number.cbz", documentUri = "doc:$number", isCbz = true, sizeBytes = 1,
        lastModified = 0, pageCount = 1, seriesKey = seriesKey, chapterKey = chapterKey, number = number.toFloat(),
    )

    private fun fr(number: Int) = LocalFrChapterEntity(
        localPath = "$allMangaTitle/c00$number - Ch. $number.cbz", folderName = allMangaTitle,
        fileName = "c00$number - Ch. $number.cbz", documentUri = "doc:fr$number", lastModified = 0L, jobId = null, mihonPath = null,
    )

    private val pepperFr = (25..42).map(::fr)

    private fun statuses(seriesKey: String, links: List<SeriesLinkEntity>, chapters: List<ChapterEntity> = emptyList()): Map<Float, ChapterStatus> {
        val downloaded = (39..42).map { downloaded(it, seriesKey, chapters.firstOrNull { c -> c.number == it.toFloat() }?.chapterKey) }
        return buildChapterJoinRows(
            seriesKey, "Comix", comixTitle,
            ChapterJoinInputs(chapters, downloaded, emptyList(), pepperFr, emptyList(), links),
        ).associate { (it.chapter?.number ?: it.downloaded!!.number!!) to it.status }
    }

    @Test fun pepperField_withoutLink_staysToTranslate() {
        val status = statuses(downloadKey, emptyList())
        assertEquals(setOf(ChapterStatus.TO_SEND), status.values.toSet())
    }

    @Test fun pepperField_linkedFolder_countsLocalFrForComixDownloads() {
        val link = SeriesLinkEntity(seriesLinkOwnerKey(downloadKey, listOf(sourceDir to comixTitle)), allMangaTitle)
        val status = statuses(downloadKey, listOf(link))
        assertEquals(listOf(39f, 40f, 41f, 42f), status.keys.sorted())
        assertEquals(setOf(ChapterStatus.UP_TO_DATE), status.values.toSet())
    }

    // La serie Comix entre en bibliotheque : nouvelle seriesKey, memes dossiers downloads/, meme liaison.
    @Test fun ownerKey_isStable_whenSeriesEntersLibrary() {
        val dirs = listOf(sourceDir to comixTitle)
        val before = seriesLinkOwnerKey(downloadKey, dirs)
        val after = seriesLinkOwnerKey("123456|/manga/pepper", dirs)
        assertEquals(before, after)

        val link = SeriesLinkEntity(before, allMangaTitle)
        val chapters = (39..42).map {
            ChapterEntity("c$it", "123456|/manga/pepper", "/c/$it", "Chapter $it", it.toFloat(), "Eris Scans", false, 0L)
        }
        val status = statuses("123456|/manga/pepper", listOf(link), chapters)
        assertEquals(setOf(ChapterStatus.UP_TO_DATE), status.values.toSet())
    }

    @Test fun ownerKey_withoutDownloads_isSeriesKey_andStillFoundOnceDownloaded() {
        assertEquals("sk", seriesLinkOwnerKey("sk", emptyList()))
        val links = SeriesLinks(listOf(SeriesLinkEntity("sk", allMangaTitle)))
        assertEquals(allMangaTitle, links.folderForSeries("sk", listOf(sourceDir to comixTitle)))
        assertEquals(allMangaTitle, links.folderFor("sk", sourceDir, comixTitle))
        assertNull(links.folderFor("other", sourceDir, comixTitle))
        assertTrue(seriesLinkOwnerKeys("sk", listOf(sourceDir to comixTitle)).containsAll(listOf("sk", downloadKey)))
    }

    @Test fun suggestions_putCloseTitleFirst_andIgnoreUnrelatedOrSelf() {
        val candidates = listOf("Blue Lock", allMangaTitle, comixTitle, "The Field Guide", "Pepper")
        val suggestions = seriesLinkSuggestions(comixTitle, candidates)
        assertEquals(allMangaTitle, suggestions.first())
        assertTrue("Blue Lock" !in suggestions)
        assertTrue(comixTitle !in suggestions)
    }

    @Test fun candidates_dedupeByFolderKey_keepExistingFolder_andExcludeOwnNames() {
        val folders = seriesLinkCandidates(
            localFolders = listOf(allMangaTitle, "Blue Lock"),
            seriesTitles = listOf("See You at the Pepper Field!", "Blue Lock: Episode Nagi", comixTitle),
            ownNames = listOf(comixTitle),
            folderOfTitle = { it.replace(':', '_') },
        )
        assertEquals(listOf("Blue Lock", "Blue Lock_ Episode Nagi", allMangaTitle), folders)
    }
}
