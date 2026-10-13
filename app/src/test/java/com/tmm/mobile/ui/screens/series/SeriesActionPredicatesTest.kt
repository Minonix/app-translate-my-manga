package com.tmm.mobile.ui.screens.series

import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.data.status.ChapterFacts
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.deriveStatus
import com.tmm.mobile.protocol.MihonPath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Un chapitre « Pret a installer » dont le PC a l'original doit pouvoir etre retraduit : avant, seul
// « Installer » etait propose et le PC rendait l'ancienne traduction.
class SeriesActionPredicatesTest {
    private fun row(index: PcIndexChapterEntity?, downloaded: Boolean = true, localFr: Boolean = false): ChapterJoinRow {
        val facts = ChapterFacts(downloaded = downloaded, inCatalog = true, index = index, latestJob = null, localFr = localFr)
        return ChapterJoinRow("s", null, null, MihonPath("Src", "S", "Ch 1"), facts, deriveStatus(facts))
    }

    @Test fun readyToInstall_withPcOriginal_canBeRetranslated() {
        val r = row(PcIndexChapterEntity("Src/S/Ch 1", original = true, fr = true))
        assertTrue(canInstall(r))
        assertTrue(canRetranslate(r))
    }

    @Test fun upToDate_withPcOriginal_canBeRetranslated() {
        assertTrue(canRetranslate(row(PcIndexChapterEntity("Src/S/Ch 1", original = true, fr = true), localFr = true)))
    }

    @Test fun readyToInstall_withoutPcOriginal_cannotBeRetranslated() {
        assertFalse(canRetranslate(row(PcIndexChapterEntity("Src/S/Ch 1", original = false, fr = true))))
    }
}
