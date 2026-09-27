package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.PcIndexChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesSummaryTest {

    private fun chapter(
        number: Float,
        status: ChapterStatus,
        latestJobPhoneState: String? = null,
        downloaded: Boolean = false,
        inCatalog: Boolean = true,
        localFr: Boolean = false,
        index: PcIndexChapterEntity? = null,
    ) = ChapterAggregateInput(number, status, latestJobPhoneState, downloaded, inCatalog, localFr, index)

    @Test fun badge_busy_takesPriorityOverEverything() {
        val summary = deriveSeriesSummary(
            "s1",
            listOf(chapter(1f, ChapterStatus.TRANSLATING, latestJobPhoneState = "SENT"), chapter(2f, ChapterStatus.TO_SEND, downloaded = true)),
            ignoredGaps = emptySet(),
        )
        assertEquals(SeriesBadge.BUSY, summary.badge())
    }

    @Test fun badge_send_whenToSendAndNothingBusy() {
        val summary = deriveSeriesSummary("s1", listOf(chapter(1f, ChapterStatus.TO_SEND, downloaded = true)), emptySet())
        assertEquals(SeriesBadge.SEND, summary.badge())
    }

    @Test fun badge_get_whenResultReadyJob() {
        val summary = deriveSeriesSummary(
            "s1",
            listOf(chapter(1f, ChapterStatus.TO_FETCH, latestJobPhoneState = "RESULT_READY")),
            emptySet(),
        )
        assertEquals(SeriesBadge.GET, summary.badge())
        assertEquals(1, summary.toFetch)
    }

    @Test fun toFetch_excludesIndexOnlyToFetch() {
        val summary = deriveSeriesSummary(
            "s1",
            listOf(chapter(1f, ChapterStatus.TO_FETCH, index = PcIndexChapterEntity("p1", original = true, fr = true))),
            emptySet(),
        )
        assertEquals(0, summary.toFetch)
    }

    @Test fun badge_absent_whenNothingOnPhone() {
        val summary = deriveSeriesSummary("s1", listOf(chapter(1f, ChapterStatus.CATALOG_ONLY)), emptySet())
        assertEquals(SeriesBadge.ABSENT, summary.badge())
        assertEquals(0, summary.onPhone)
    }

    @Test fun badge_ok_whenUpToDate() {
        val summary = deriveSeriesSummary("s1", listOf(chapter(1f, ChapterStatus.UP_TO_DATE, localFr = true)), emptySet())
        assertEquals(SeriesBadge.OK, summary.badge())
        assertEquals(1, summary.frCount)
        assertEquals(1, summary.onPhone)
    }

    @Test fun onPc_and_verifiable_countIndexOriginal() {
        val summary = deriveSeriesSummary(
            "s1",
            listOf(
                chapter(1f, ChapterStatus.UP_TO_DATE, localFr = true, index = PcIndexChapterEntity("p1", original = true, fr = true)),
                chapter(2f, ChapterStatus.NOT_TRANSLATED, index = PcIndexChapterEntity("p2", original = true, fr = false)),
            ),
            emptySet(),
        )
        assertEquals(2, summary.onPc)
        assertEquals(1, summary.verifiable)
    }

    @Test fun missing_derivedFromPresentChapters() {
        val summary = deriveSeriesSummary(
            "s1",
            listOf(
                chapter(1f, ChapterStatus.UP_TO_DATE, localFr = true),
                chapter(3f, ChapterStatus.TO_SEND, downloaded = true),
            ),
            emptySet(),
        )
        assertEquals(listOf(2), summary.missing)
    }

    @Test fun filters_toTranslate_and_notDownloaded() {
        val busy = deriveSeriesSummary("s1", listOf(chapter(1f, ChapterStatus.TRANSLATING, latestJobPhoneState = "SENT", downloaded = true)), emptySet())
        val absent = deriveSeriesSummary("s2", listOf(chapter(1f, ChapterStatus.CATALOG_ONLY)), emptySet())
        assertEquals(true, busy.matchesToTranslateFilter())
        assertEquals(false, busy.matchesNotDownloadedFilter())
        assertEquals(true, absent.matchesNotDownloadedFilter())
    }
}
