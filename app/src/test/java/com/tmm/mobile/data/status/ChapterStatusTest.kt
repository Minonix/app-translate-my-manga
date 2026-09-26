package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterStatusTest {

    private fun job(
        phoneState: String,
        pcState: String? = null,
        pcPage: Int? = null,
        pcPages: Int? = null,
    ) = JobEntity(
        jobId = "j1", mode = "translate", pages = null, translate = true,
        mihonPath = "p", sourceDir = "s", seriesDir = "sd", chapterDir = "cd",
        seriesKey = "sk", chapterKey = "ck", phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = pcState, pcStage = null, pcPage = pcPage, pcPages = pcPages, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = 0, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    private fun facts(
        downloaded: Boolean = false,
        inCatalog: Boolean = false,
        index: PcIndexChapterEntity? = null,
        latestJob: JobEntity? = null,
        localFr: Boolean = false,
    ) = ChapterFacts(downloaded, inCatalog, index, latestJob, localFr)

    @Test fun catalogOnly_whenNothingElseKnown() =
        assertEquals(ChapterStatus.CATALOG_ONLY, deriveStatus(facts(inCatalog = true)))

    @Test fun toSend_whenDownloadedOnly() =
        assertEquals(ChapterStatus.TO_SEND, deriveStatus(facts(downloaded = true)))

    @Test fun notTranslated_whenIndexOriginalOnly() =
        assertEquals(ChapterStatus.NOT_TRANSLATED, deriveStatus(facts(index = PcIndexChapterEntity("p", original = true, fr = false))))

    @Test fun toFetch_whenIndexFr() =
        assertEquals(ChapterStatus.TO_FETCH, deriveStatus(facts(index = PcIndexChapterEntity("p", original = true, fr = true))))

    @Test fun upToDate_whenLocalFr() =
        assertEquals(ChapterStatus.UP_TO_DATE, deriveStatus(facts(localFr = true, index = PcIndexChapterEntity("p", original = true, fr = true))))

    @Test fun toFetch_whenJobResultReady() =
        assertEquals(ChapterStatus.TO_FETCH, deriveStatus(facts(latestJob = job(phoneState = "RESULT_READY"))))

    @Test fun toFetch_whenJobDownloading() =
        assertEquals(ChapterStatus.TO_FETCH, deriveStatus(facts(latestJob = job(phoneState = "DOWNLOADING"))))

    @Test fun queued_whenJobSentAndPcNotTranslating() =
        assertEquals(ChapterStatus.QUEUED, deriveStatus(facts(latestJob = job(phoneState = "SENT", pcState = "queued"))))

    @Test fun translating_whenPcTranslating() =
        assertEquals(ChapterStatus.TRANSLATING, deriveStatus(facts(latestJob = job(phoneState = "SENT", pcState = "translating"))))

    @Test fun failed_whenPhoneStateFailed() =
        assertEquals(ChapterStatus.FAILED, deriveStatus(facts(latestJob = job(phoneState = "FAILED"))))

    @Test fun failed_whenPcStateFailedEvenIfPhoneStateSent() =
        assertEquals(ChapterStatus.FAILED, deriveStatus(facts(latestJob = job(phoneState = "SENT", pcState = "failed"))))

    @Test fun cancelledJob_isIgnored_fallsBackToDownloaded() =
        assertEquals(
            ChapterStatus.TO_SEND,
            deriveStatus(facts(downloaded = true, latestJob = job(phoneState = "SENT", pcState = "cancelled"))),
        )

    @Test fun translatingPercent_computesRatio() =
        assertEquals(50, translatingPercent(job(phoneState = "SENT", pcState = "translating", pcPage = 5, pcPages = 10)))

    @Test fun translatingPercent_zeroWhenUnknown() =
        assertEquals(0, translatingPercent(job(phoneState = "SENT")))
}
