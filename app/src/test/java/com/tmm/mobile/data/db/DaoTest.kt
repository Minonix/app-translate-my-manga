package com.tmm.mobile.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.protocol.QualityAlert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: TmmDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TmmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test fun series_upsertAndObserve() = runTest {
        val series = SeriesEntity("sk1", "src", "url", "Titre", null, 0, null, "MangaSource", 0L, null)
        db.seriesDao().upsert(series)
        assertEquals(listOf(series), db.seriesDao().observeAll().first())
    }

    @Test fun chapter_upsertAndObserveForSeries() = runTest {
        val chapter = ChapterEntity("ck1", "sk1", "url", "Ch 1", 1f, null, false, 0L)
        db.chapterDao().upsertAll(listOf(chapter))
        assertEquals(listOf(chapter), db.chapterDao().observeForSeries("sk1").first())
    }

    @Test fun job_withIntListConverter_roundTrips() = runTest {
        val job = job(pages = listOf(1, 2, 3))
        db.jobDao().upsert(job)
        assertEquals(listOf(1, 2, 3), db.jobDao().get("j1")?.pages)
    }

    @Test fun chapterQuality_withAlertListConverter_roundTrips() = runTest {
        val quality = ChapterQualityEntity(
            mihonPath = "p1", totalPages = 10, translatedBubbles = 5, untranslatedBubbles = 1,
            sfxKept = 2, verifiedAt = null, alerts = listOf(QualityAlert("source_text", 3)), jobId = "j1",
        )
        db.chapterQualityDao().upsert(quality)
        assertEquals(listOf(QualityAlert("source_text", 3)), db.chapterQualityDao().observe("p1").first()?.alerts)
    }

    @Test fun pcIndexChapter_replaceAll_isTransactionalFullReplace() = runTest {
        db.pcIndexChapterDao().replaceAll(listOf(PcIndexChapterEntity("p1", original = true, fr = false)))
        db.pcIndexChapterDao().replaceAll(listOf(PcIndexChapterEntity("p2", original = true, fr = true)))
        val all = db.pcIndexChapterDao().observeAll().first()
        assertEquals(listOf(PcIndexChapterEntity("p2", original = true, fr = true)), all)
    }

    @Test fun ignoredGap_upsertAndDelete() = runTest {
        val gap = IgnoredGapEntity("sk1", 5)
        db.ignoredGapDao().upsert(gap)
        assertEquals(listOf(gap), db.ignoredGapDao().observeForSeries("sk1").first())
        db.ignoredGapDao().delete(gap)
        assertEquals(emptyList<IgnoredGapEntity>(), db.ignoredGapDao().observeForSeries("sk1").first())
    }

    @Test fun seriesSettings_defaultsToFra() = runTest {
        val settings = SeriesSettingsEntity(seriesKey = "sk1", autoTranslate = true)
        db.seriesSettingsDao().upsert(settings)
        assertEquals("FR", db.seriesSettingsDao().observe("sk1").first()?.targetLang)
    }

    private fun job(pages: List<Int>?) = JobEntity(
        jobId = "j1", mode = "translate", pages = pages, translate = true,
        mihonPath = "p1", sourceDir = "s", seriesDir = "sd", chapterDir = "cd",
        seriesKey = "sk1", chapterKey = "ck1", phoneState = "PREPARING",
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = null, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = null, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = 0, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )
}
