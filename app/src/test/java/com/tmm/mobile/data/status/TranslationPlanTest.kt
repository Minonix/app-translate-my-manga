package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.PcIndexChapterEntity
import com.tmm.mobile.protocol.MihonPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Bouton "Traduire"/"Retraduire" (Fiche serie et Bibliotheque) : cibles et libelle.
class TranslationPlanTest {

    private fun job(phoneState: String) = JobEntity(
        jobId = "j1", mode = "translate", pages = null, translate = true,
        mihonPath = "p", sourceDir = "s", seriesDir = "sd", chapterDir = "cd",
        seriesKey = "sk", chapterKey = "ck", phoneState = phoneState,
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = 0, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )

    private fun row(
        chapter: String,
        downloaded: Boolean = false,
        original: Boolean? = null,
        pcFr: Boolean = false,
        localFr: Boolean = false,
        latestJob: JobEntity? = null,
    ): ChapterJoinRow {
        val path = MihonPath("Source", "Serie", chapter)
        val index = original?.let { PcIndexChapterEntity("Source/Serie/$chapter", original = it, fr = pcFr) }
        val facts = ChapterFacts(downloaded = downloaded, inCatalog = true, index = index, latestJob = latestJob, localFr = localFr)
        return ChapterJoinRow("sk", chapter = null, downloaded = null, mihonPath = path, facts = facts, status = deriveStatus(facts))
    }

    @Test fun noneTranslated_labelsTraduire() {
        val plan = planTranslation(listOf(row("c1", original = true), row("c2", downloaded = true, original = true)))
        assertEquals(2, plan.count)
        assertFalse(plan.retranslate)
    }

    @Test fun oneTranslatedAmongSeveral_labelsRetraduire() {
        val plan = planTranslation(
            listOf(
                row("c1", original = true),
                row("c2", original = true),
                row("c3", original = true, localFr = true),
            ),
        )
        assertEquals(3, plan.count)
        assertEquals(1, plan.alreadyTranslated)
        assertTrue(plan.retranslate)
    }

    @Test fun frOnlyOnPc_countsAsTranslated() {
        val plan = planTranslation(listOf(row("c1", original = true, pcFr = true)))
        assertTrue(plan.retranslate)
    }

    @Test fun targets_excludeToSendActiveJobAndAbsentFromPc() {
        val toSend = row("send", downloaded = true)
        val activeJob = row("busy", original = true, latestJob = job("SENT"))
        val resultReady = row("ready", original = true, pcFr = true, latestJob = job("RESULT_READY"))
        val absentFromPc = row("absent", original = false, localFr = true)
        val catalogOnly = row("catalog")
        val notTranslated = row("nt", original = true)
        val failed = row("failed", original = true, latestJob = job("FAILED"))
        assertEquals(ChapterStatus.TO_SEND, toSend.status)

        val plan = planTranslation(listOf(toSend, activeJob, resultReady, absentFromPc, catalogOnly, notTranslated, failed))

        assertEquals(listOf("nt", "failed"), plan.targets.map { it.mihonPath.chapter })
        assertFalse(plan.retranslate)
    }
}
