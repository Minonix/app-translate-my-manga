package com.tmm.mobile.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// PLAN_GEMINI.md E10.4 : masquage e-mail/Authorization dans l'export des journaux, fonction pure.
class AppLogJournalTest {

    @Test fun journalExportFileName_isIsoDate() {
        assertEquals("tmm-journaux-2026-09-27.txt", journalExportFileName(LocalDate.of(2026, 9, 27)))
    }

    @Test fun maskSensitive_masksEmail_andAuthorizationHeader() {
        val text = maskSensitive("contact martiniclement06@gmail.com puis Authorization: Bearer abc.def.ghi")
        assertTrue(text.contains("***@***"))
        assertFalse(text.contains("martiniclement06@gmail.com"))
        assertTrue(text.contains("Authorization: ***"))
        assertFalse(text.contains("Bearer abc.def.ghi"))
    }

    @Test fun maskSensitive_leavesUnrelatedTextUntouched() {
        assertEquals("DriveApi: GET /files -> 200", maskSensitive("DriveApi: GET /files -> 200"))
    }

    @Test fun journalExportText_keepsOnlyLast7Days_andMasks() {
        val dayMs = 24 * 60 * 60 * 1000L
        val now = 10_000_000_000L
        val entries = listOf(
            AppLog.Entry(now - 8 * dayMs, "Old", "trop vieux, ignore"),
            AppLog.Entry(now - 1 * dayMs, "Recent", "contact martiniclement06@gmail.com"),
        )
        val text = journalExportText(entries, now)
        assertFalse(text.contains("trop vieux"))
        assertTrue(text.contains("Recent: contact ***@***"))
    }
}
