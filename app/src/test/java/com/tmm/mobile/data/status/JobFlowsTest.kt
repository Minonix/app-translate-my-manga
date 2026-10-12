package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.JobEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// ignoringProgress : les ticks de progression d'envoi ne doivent pas relancer les calculs lourds.
class JobFlowsTest {

    private fun job(phoneState: String = "UPLOADING", transferred: Long = 0, updatedAt: Long = 0) = JobEntity(
        jobId = "j1", mode = "translate", pages = null, translate = true,
        mihonPath = "p", sourceDir = "s", seriesDir = "sd", chapterDir = "cd",
        seriesKey = "sk", chapterKey = "ck", phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = transferred, transferTotal = 100,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = updatedAt, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    @Test fun progressOnlyChanges_areDropped() = runTest {
        val emitted = flowOf(
            listOf(job(transferred = 0, updatedAt = 1)),
            listOf(job(transferred = 40, updatedAt = 2)),
            listOf(job(transferred = 80, updatedAt = 3)),
        ).ignoringProgress().toList()
        assertEquals(1, emitted.size)
    }

    @Test fun realChange_isEmittedWithFreshProgress() = runTest {
        val emitted = flowOf(
            listOf(job(transferred = 0, updatedAt = 1)),
            listOf(job(transferred = 40, updatedAt = 2)),
            listOf(job(phoneState = "SENT", transferred = 100, updatedAt = 3)),
        ).ignoringProgress().toList()
        assertEquals(2, emitted.size)
        assertEquals(100L, emitted.last().single().transferredBytes)
    }

    @Test fun addedJob_isEmitted() = runTest {
        val emitted = flowOf(listOf(job()), listOf(job(), job().copy(jobId = "j2"))).ignoringProgress().toList()
        assertEquals(2, emitted.size)
    }
}
