package com.tmm.mobile.data

import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.SettingsValues
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 5.18/CdC 7.5 : SettingsSync (commandes en attente, confirmation via readSettings(),
// timeout 60s en horloge virtuelle kotlinx-coroutines-test, lecture seule sans settings_json).
@RunWith(RobolectricTestRunner::class)
class SettingsSyncTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    private fun settingsValues() = SettingsValues(
        sourceLanguage = "auto", targetLanguage = "FRA", translationProvider = "gemini",
        autoFailoverToLocalLlm = false, autoVerifyAfterTranslate = false,
        chapterConcurrency = 1, concurrency = 1, pileSize = 1, useGpu = false,
        detector = "default", preferredOcr = "auto", inpainter = "lama_large",
        inpaintingSize = 1024, font = "Anime Ace",
    )

    private fun pcSettings(version: Int, lastCommandId: String? = null, values: SettingsValues = settingsValues()) =
        PcSettings(updatedAt = "2026-01-01T00:00:00Z", version = version, lastCommandId = lastCommandId, values = values)

    private suspend fun seedSettings(version: Int) =
        env.prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), pcSettings(version)))

    @Test fun change_writesCommand_withCorrectBaseVersionAndSingleKey() = runTest {
        seedSettings(version = 3)

        val cmdId = env.container.settingsSync.change("targetLanguage", "ENG")
        env.container.settingsSync.sendPendingCommands()

        val commandsId = requireNotNull(env.prefs.driveCommandsId.first())
        val file = requireNotNull(env.drive.childOf(commandsId, "$cmdId.json"))
        val command = ProtocolJson.decodeFromString(SettingsCommand.serializer(), String(file.bytes))
        assertEquals(3, command.baseVersion)
        assertEquals(setOf("targetLanguage"), command.changes.keys)
        assertEquals("\"ENG\"", command.changes.getValue("targetLanguage").toString())
    }

    // Le champ "delete" (commandes de suppression) est omis du JSON d'un reglage, pas ecrit a null.
    @Test fun change_commandJson_hasNoDeleteKey() = runTest {
        seedSettings(version = 3)

        val cmdId = env.container.settingsSync.change("targetLanguage", "ENG")
        env.container.settingsSync.sendPendingCommands()

        val commandsId = requireNotNull(env.prefs.driveCommandsId.first())
        val file = requireNotNull(env.drive.childOf(commandsId, "$cmdId.json"))
        val json = ProtocolJson.parseToJsonElement(String(file.bytes)).jsonObject
        assertFalse(json.containsKey("delete"))
        assertEquals("\"settings\"", json.getValue("type").toString())
    }

    @Test fun awaitConfirmation_appliedByFakePc_returnsApplied() = runTest {
        seedSettings(version = 3)
        val cmdId = env.container.settingsSync.change("targetLanguage", "ENG")

        env.pc.writeSettings(pcSettings(version = 4, lastCommandId = cmdId, values = settingsValues().copy(targetLanguage = "ENG")))

        val banner = env.container.settingsSync.awaitConfirmation(cmdId)

        assertEquals(SyncBanner.APPLIED, banner)
        assertNull(env.prefs.pendingCommandsJson.first())
    }

    @Test fun awaitConfirmation_noReplyWithin60s_returnsWaitingPc() = runTest {
        seedSettings(version = 3)
        val cmdId = env.container.settingsSync.change("targetLanguage", "ENG")
        // Le PC ne repond jamais : settings.json sur Drive reste a version 3, sans lastCommandId.

        val banner = env.container.settingsSync.awaitConfirmation(cmdId)

        assertEquals(SyncBanner.WAITING_PC, banner)
        assertTrue(requireNotNull(env.prefs.pendingCommandsJson.first()).contains(cmdId))
    }

    @Test fun change_rejectsOutOfSchemaValue_beforeSending() = runTest {
        seedSettings(version = 3)

        try {
            env.container.settingsSync.change("targetLanguage", "ITA")
            fail("valeur hors schema acceptee")
        } catch (e: IllegalArgumentException) {
            // attendu
        }
        assertNull(env.prefs.pendingCommandsJson.first())
    }

    @Test fun readOnly_withoutSettingsJson() = runTest {
        assertTrue(env.container.settingsSync.readOnly())
        assertNull(env.container.settingsSync.values())
    }
}
