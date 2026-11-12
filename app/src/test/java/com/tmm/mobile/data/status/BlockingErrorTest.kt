package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Bandeau d'erreur bloquante : une cle refusee ou un quota epuise cote PC doit se voir tant
// qu'aucun chapitre n'a abouti depuis, sans s'afficher pour une erreur ponctuelle.
class BlockingErrorTest {
    private fun job(id: String, phoneState: String, pcState: String? = null, pcError: String? = null, at: Long) = JobEntity(
        jobId = id, mode = "translate", pages = null, translate = true,
        mihonPath = "p$id", sourceDir = "s", seriesDir = "sd", chapterDir = "cd",
        seriesKey = "sk", chapterKey = "ck$id", phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = pcState, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = pcError, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = at, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    @Test
    fun apiKeyFailure_isBlocking() {
        val info = ErrorCatalog.blockingError(listOf(job("a", "SENT", "failed", "api_key", at = 10)))
        assertEquals("api_key", info?.code)
    }

    @Test
    fun ordinaryFailure_isNotBlocking() {
        assertNull(ErrorCatalog.blockingError(listOf(job("a", "SENT", "failed", "internal", at = 10))))
    }

    @Test
    fun laterSuccess_clearsBanner() {
        val jobs = listOf(
            job("a", "SENT", "failed", "provider_quota", at = 10),
            job("b", "INSTALLED", "done", at = 20),
        )
        assertNull(ErrorCatalog.blockingError(jobs))
    }

    @Test
    fun earlierSuccess_keepsBanner() {
        val jobs = listOf(
            job("b", "INSTALLED", "done", at = 5),
            job("a", "SENT", "failed", "provider_quota", at = 10),
        )
        assertEquals("provider_quota", ErrorCatalog.blockingError(jobs)?.code)
    }
}
