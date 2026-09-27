package com.tmm.mobile.ui.screens.translationsettings

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.9/5.18 (E10) : TranslationSettingsViewModel charge SettingsValues au demarrage,
// n'ecrit jamais settings_json directement (uniquement via SettingsSync.change avec les cles/valeurs
// du schema), et reste en lecture seule tant qu'aucun settings_json n'a ete recu.
@RunWith(RobolectricTestRunner::class)
class TranslationSettingsViewModelTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun settingsValues() = SettingsValues(
        sourceLanguage = "auto", targetLanguage = "FRA", translationProvider = "gemini",
        autoFailoverToLocalLlm = false, autoVerifyAfterTranslate = true,
        chapterConcurrency = 2, concurrency = 3, pileSize = 1, useGpu = true,
        detector = "default", preferredOcr = "auto", inpainter = "lama_large",
        inpaintingSize = 1280, font = "Anime Ace",
    )

    private suspend fun seedSettings(version: Int = 1, values: SettingsValues = settingsValues()) =
        env.prefs.setSettingsJson(
            ProtocolJson.encodeToString(
                PcSettings.serializer(),
                PcSettings(updatedAt = "2026-01-01T00:00:00Z", version = version, lastCommandId = null, values = values),
            ),
        )

    @Test fun state_loadsMappedValues_fromSettingsSync() = runTest {
        seedSettings()

        val vm = TranslationSettingsViewModel(env.container)
        val state = vm.state.first { !it.isReadOnly }

        assertEquals(SourceLanguage.Auto, state.sourceLanguage)
        assertEquals(TargetLanguage.French, state.targetLanguage)
        assertEquals(TranslationProvider.Gemini, state.translationProvider)
        assertEquals(3, state.concurrency)
        assertTrue(state.useGpu)
        assertTrue(state.autoVerifyAfterTranslate)
        assertEquals(InpaintingSize.Size1280, state.inpaintingSize)
        assertEquals(TranslationFont.AnimeAce, state.font)
    }

    @Test fun state_withoutSettingsJson_isReadOnly() = runTest {
        val vm = TranslationSettingsViewModel(env.container)

        val state = vm.state.first { it.isReadOnly }

        assertEquals(true, state.isReadOnly)
        // Pas de settings_json : les valeurs par defaut (mock) restent affichees telles quelles.
        assertEquals(TranslationSettingsState().sourceLanguage, state.sourceLanguage)
    }

    @Test fun state_readsPcNameFromHeartbeat() = runTest {
        seedSettings()
        val heartbeat = Heartbeat(
            pcName = "PC-BUREAU", appVersion = "1.2.0", updatedAt = Instant.now().toString(),
            slots = 2, queue = QueueCounts(active = 0, waiting = 0, failed = 0),
        )
        env.prefs.setHeartbeatJson(ProtocolJson.encodeToString(Heartbeat.serializer(), heartbeat))

        val vm = TranslationSettingsViewModel(env.container)
        val state = vm.state.first { it.pcName == "PC-BUREAU" }

        assertEquals("PC-BUREAU", state.pcName)
    }

    @Test fun onEvent_selectTargetLanguage_sendsCorrectKeyAndValue_andSetsSending() = runTest {
        seedSettings()
        val vm = TranslationSettingsViewModel(env.container)
        vm.state.first { !it.isReadOnly }

        vm.onEvent(TranslationSettingsEvent.SelectTargetLanguage(TargetLanguage.English))

        // Applique tout de suite en optimiste (5.18) : pas besoin d'attendre le worker.
        assertEquals(TargetLanguage.English, vm.state.value.targetLanguage)
        assertEquals(TranslationSyncStatus.Sending, vm.state.value.syncStatus)

        val pendingJson = env.prefs.pendingCommandsJson.first { it != null }
        assertTrue(requireNotNull(pendingJson).contains("\"targetLanguage\":\"ENG\""))
    }

    @Test fun onEvent_setUseGpu_sendsBooleanAsSchemaString() = runTest {
        seedSettings()
        val vm = TranslationSettingsViewModel(env.container)
        vm.state.first { !it.isReadOnly }

        vm.onEvent(TranslationSettingsEvent.SetUseGpu(false))

        assertEquals(false, vm.state.value.useGpu)
        val pendingJson = env.prefs.pendingCommandsJson.first { it != null }
        assertTrue(requireNotNull(pendingJson).contains("\"useGpu\":\"false\""))
    }

    @Test fun onEvent_whileReadOnly_isIgnored() = runTest {
        val vm = TranslationSettingsViewModel(env.container)
        vm.state.first { it.isReadOnly }

        vm.onEvent(TranslationSettingsEvent.SetUseGpu(true))

        assertEquals(TranslationSyncStatus.Synced, vm.state.value.syncStatus)
        assertEquals(TranslationSettingsState().useGpu, vm.state.value.useGpu)
    }
}
