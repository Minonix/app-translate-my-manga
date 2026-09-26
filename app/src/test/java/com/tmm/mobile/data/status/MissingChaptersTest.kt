package com.tmm.mobile.data.status

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingChaptersTest {

    @Test fun detectsGapsIgnoringNonIntegerChapters() {
        val present = listOf(1f, 2f, 4f, 7.5f, 8f)
        assertEquals(listOf(3, 5, 6, 7), missingChapters(present, ignored = emptySet()))
    }

    @Test fun ignoredGapIsExcluded() {
        val present = listOf(1f, 2f, 4f, 7.5f, 8f)
        assertEquals(listOf(3, 6, 7), missingChapters(present, ignored = setOf(5)))
    }

    @Test fun singleChapter_hasNoGap() {
        assertEquals(emptyList<Int>(), missingChapters(listOf(1f), ignored = emptySet()))
    }

    @Test fun empty_hasNoGap() {
        assertEquals(emptyList<Int>(), missingChapters(emptyList(), ignored = emptySet()))
    }
}
