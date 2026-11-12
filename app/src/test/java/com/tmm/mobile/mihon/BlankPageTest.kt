package com.tmm.mobile.mihon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankPageTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val grey = 0xFF808080.toInt()

    @Test fun pageBlancheOuNoire_estBlanche() {
        assertTrue(BlankPage.isMostlyBlank(IntArray(100) { white }))
        assertTrue(BlankPage.isMostlyBlank(IntArray(100) { if (it < 98) black else grey }))
    }

    @Test fun pageDeManga_nEstPasBlanche() {
        // 80 % de blanc, des traits et des trames : page ordinaire.
        assertFalse(BlankPage.isMostlyBlank(IntArray(100) { if (it < 80) white else if (it < 90) black else grey }))
        assertFalse(BlankPage.isMostlyBlank(IntArray(0)))
    }

    @Test fun pickCover_sauteLesPagesBlanchesSansDepasserLaLimite() {
        val blank = { b: ByteArray -> b[0] == 0.toByte() }
        val pages = sequenceOf(byteArrayOf(0), byteArrayOf(0), byteArrayOf(3), byteArrayOf(4))
        assertEquals(3, BlankPage.pickCover(pages, { it }, blank)!![0].toInt())
        // Tout blanc : la premiere page reste, plutot que rien.
        val allBlank = generateSequence { byteArrayOf(0) }
        assertEquals(0, BlankPage.pickCover(allBlank, { it }, blank)!![0].toInt())
        assertNull(BlankPage.pickCover(emptySequence<ByteArray>(), { it }, blank))
    }
}
