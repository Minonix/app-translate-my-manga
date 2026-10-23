package com.tmm.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageCoverRefTest {
    @Test fun roundTrip_keepsDocIdWithColons() {
        val docId = "primary:Mihon/downloads/MangaDex (EN)/スパ/Ch. 1_abc123.cbz"
        assertEquals(docId to true, parsePageCoverRef(pageCoverRef(docId, isCbz = true)))
        assertEquals(docId to false, parsePageCoverRef(pageCoverRef(docId, isCbz = false)))
    }

    @Test fun otherValues_areNotPageCovers() {
        assertNull(parsePageCoverRef(null))
        assertNull(parsePageCoverRef("https://cdn/x.jpg"))
        assertNull(parsePageCoverRef("tmm-page:zip:x"))
    }
}
