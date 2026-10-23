package com.tmm.mobile.mihon

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class CbzPackerTest {
    @Test fun naturalOrder_sortsNumbersNumerically() {
        val sorted = listOf("10.jpg", "2.jpg", "1.jpg").sortedWith(CbzPacker.NATURAL_ORDER)
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), sorted)
    }

    @Test fun isEligible_excludesHiddenAndTmpFiles() {
        assertEquals(false, CbzPacker.isEligible(".nomedia"))
        assertEquals(false, CbzPacker.isEligible("page1.jpg.tmp"))
        assertEquals(true, CbzPacker.isEligible("page1.jpg"))
    }

    @Test fun pageCount_countsOnlyImageExtensions_caseInsensitive() {
        val names = listOf("1.JPG", "2.png", "ComicInfo.xml", "3.webp", ".hidden.jpg")
        assertEquals(3, CbzPacker.pageCount(names))
    }

    @Test fun pack_writesStoredEntriesInNaturalOrder() {
        val entries = mapOf(
            "10.jpg" to byteArrayOf(1, 2, 3),
            "2.jpg" to byteArrayOf(4, 5),
            ".hidden" to byteArrayOf(9),
        )
        val output = ByteArrayOutputStream()
        CbzPacker.pack(entries, output)

        val names = mutableListOf<String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zis ->
            generateSequence { zis.nextEntry }.forEach { names.add(it.name) }
        }
        assertEquals(listOf("2.jpg", "10.jpg"), names)
    }

    @Test fun firstPage_skipsNonImagesAndHiddenFiles() {
        val out = java.io.ByteArrayOutputStream()
        CbzPacker.pack(mapOf("ComicInfo.xml" to byteArrayOf(0), "002.png" to byteArrayOf(2), "001.jpg" to byteArrayOf(1)), out)
        val page = CbzPacker.firstPage(out.toByteArray().inputStream())
        org.junit.Assert.assertEquals("001.jpg", page?.first)
        org.junit.Assert.assertEquals(listOf(1), page?.second?.map { it.toInt() })
    }
}
