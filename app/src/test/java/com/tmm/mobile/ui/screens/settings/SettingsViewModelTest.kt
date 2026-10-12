package com.tmm.mobile.ui.screens.settings

import com.tmm.mobile.data.Retention
import com.tmm.mobile.data.db.BackupImportEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.protocol.Heartbeat
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.QueueCounts
import com.tmm.mobile.protocol.SettingsValues
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.8 (E10) : SettingsViewModel derive son etat de AppPrefs/Room/SettingsSync -
// aucune ecriture directe, seuls container.prefs.set*() (reglages locaux) et
// container.settingsSync.change() (reglages PC) sont utilises.
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun series(key: String, title: String) = SeriesEntity(
        seriesKey = key, sourceId = "src", url = "url/$key", title = title, author = null,
        status = 0, thumbnailUrl = null, sourceName = "Mihon", dateAdded = 0L, mangaType = null,
    )

    private fun settingsValues() = SettingsValues(
        sourceLanguage = "auto", targetLanguage = "FRA", translationProvider = "gemini",
        autoFailoverToLocalLlm = false, autoVerifyAfterTranslate = false,
        chapterConcurrency = 2, concurrency = 1, pileSize = 1, useGpu = false,
        detector = "default", preferredOcr = "auto", inpainter = "lama_large",
        inpaintingSize = 1024, font = "Anime Ace",
    )

    @Test fun state_derivesFromHeartbeatAccountRetentionAndBackup() = runTest {
        val now = env.clock.millis
        val heartbeat = Heartbeat(
            pcName = "PC-BUREAU", appVersion = "1.2.0",
            updatedAt = Instant.ofEpochMilli(now - 2 * 60_000L).toString(),
            slots = 2, queue = QueueCounts(active = 0, waiting = 0, failed = 0),
        )
        env.prefs.setHeartbeatJson(ProtocolJson.encodeToString(Heartbeat.serializer(), heartbeat))
        env.prefs.setGoogleEmail("martiniclement06@gmail.com")
        env.prefs.setRetention(Retention.DAYS30)
        env.prefs.setWifiOnly(true)
        env.db.seriesDao().upsertAll(listOf(series("frieren", "Frieren"), series("one-piece", "One Piece")))
        env.db.backupImportDao().upsert(
            BackupImportEntity(name = "backup.tachibk", size = 1L, lastModified = 0L, importedAt = now, source = "auto"),
        )
        env.prefs.setSettingsJson(
            ProtocolJson.encodeToString(
                PcSettings.serializer(),
                PcSettings(updatedAt = "2026-01-01T00:00:00Z", version = 1, lastCommandId = null, values = settingsValues()),
            ),
        )

        val vm = SettingsViewModel(env.container)
        val state = vm.state.first { it.pcName == "PC-BUREAU" && it.isPcOnline && it.wifiOnly && it.driveRetention == DriveRetention.Days30 && it.googleAccount.isNotEmpty() }

        assertEquals("PC-BUREAU", state.pcName)
        assertTrue(state.pcVersionProtocol.contains("1.2.0"))
        assertTrue(state.isPcOnline)
        assertTrue(state.pcLastSignalText.startsWith("Allumé · vu "))
        assertEquals("martiniclement06@gmail.com", state.googleAccount)
        assertEquals(DriveRetention.Days30, state.driveRetention)
        assertTrue(state.wifiOnly)

        val backupState = vm.state.first { it.lastBackupInfo.contains("séries") }
        assertTrue(backupState.lastBackupInfo.contains("Aujourd'hui"))
        assertTrue(backupState.lastBackupInfo.contains("2 séries"))

        val translationState = vm.state.first { !it.pcTranslationSummary.contains("non reçus") }
        assertTrue(translationState.pcTranslationSummary.contains("Gemini"))
        assertTrue(translationState.pcTranslationSummary.contains("détection auto"))
    }

    @Test fun state_withoutHeartbeatOrSettings_isOfflineAndReadOnlySummary() = runTest {
        val vm = SettingsViewModel(env.container)

        val state = vm.state.first { !it.isPcOnline }

        assertFalse(state.isPcOnline)
        assertEquals("Aucun signal reçu", state.pcLastSignalText)
        val translationState = vm.state.first { it.pcTranslationSummary.isNotEmpty() }
        assertEquals("Réglages non reçus du PC", translationState.pcTranslationSummary)
    }

    @Test fun onEvent_cycleDriveRetention_persistsToPrefs() = runTest {
        val vm = SettingsViewModel(env.container)
        vm.state.first()

        vm.onEvent(SettingsEvent.CycleDriveRetention)

        val expected = DriveRetention.Never.next()
        assertEquals(expected, vm.state.value.driveRetention)
        val persisted = env.prefs.retention.first { it != Retention.NEVER }
        assertEquals(
            when (expected) {
                DriveRetention.Never -> Retention.NEVER
                DriveRetention.Days30 -> Retention.DAYS30
                DriveRetention.AfterDownload -> Retention.AFTER_RECEIVE
            },
            persisted,
        )
    }

    @Test fun onEvent_setWifiOnly_persistsToPrefs() = runTest {
        val vm = SettingsViewModel(env.container)
        vm.state.first()

        vm.onEvent(SettingsEvent.SetWifiOnly(true))

        assertTrue(vm.state.value.wifiOnly)
        assertTrue(env.prefs.wifiOnly.first { it })
    }
}
