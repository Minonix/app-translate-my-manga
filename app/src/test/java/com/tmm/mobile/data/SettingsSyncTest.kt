package com.tmm.mobile.data

import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.SettingsValues
import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
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

    private suspend fun pending() = env.prefs.pendingCommandsJson.first()
        ?.let { ProtocolJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(PendingCommand.serializer()), it) }
        .orEmpty()

    // R12 : lastCommandId == cmdId avec lastCommandResult "applied" -> appliquee et retiree.
    @Test fun reconcile_applied_removesCommand() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val cmdId = sync.change("targetLanguage", "ENG")
        sync.sendPendingCommands()

        env.pc.acknowledgeCommand(pcSettings(version = 3), cmdId, status = "applied", version = 4)

        assertEquals(SyncBanner.APPLIED, sync.awaitConfirmation(cmdId))
        assertTrue(pending().isEmpty())
    }

    // R12 : stale_version -> relue sur la nouvelle base et renvoyee UNE seule fois (nouveau cmdId) ;
    // un second stale_version la rejette.
    @Test fun reconcile_staleVersion_rebasesOnce_thenRejects() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val cmdId = sync.change("targetLanguage", "ENG")
        sync.sendPendingCommands()

        sync.reconcile(pcSettings(version = 5).copy(lastCommandId = cmdId,
            lastCommandResult = com.tmm.mobile.protocol.CommandResult(cmdId, "rejected", "stale_version")))

        val rebased = pending().single()
        assertTrue(rebased.command.cmdId != cmdId)
        assertEquals(5, rebased.command.baseVersion)
        assertTrue(rebased.rebased)
        assertEquals(cmdId, rebased.originId)
        assertFalse(rebased.sent)

        sync.sendPendingCommands()
        val newId = rebased.command.cmdId
        sync.reconcile(pcSettings(version = 6).copy(lastCommandId = newId,
            lastCommandResult = com.tmm.mobile.protocol.CommandResult(newId, "rejected", "stale_version")))

        assertTrue(pending().isEmpty())
        assertEquals(SyncBanner.REJECTED, sync.awaitConfirmation(cmdId))
    }

    // R8 : le PC signale la valeur qu'il a ecrasee -> l'ecran peut le dire ; un refus "superseded"
    // (valeur du PC plus recente) est rejete avec son motif, sans renvoi.
    @Test fun reconcile_appliedWithOverwrite_exposesOverwrittenKeys() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val cmdId = sync.change("targetLanguage", "ENG")
        sync.sendPendingCommands()

        sync.reconcile(pcSettings(version = 4).copy(lastCommandId = cmdId,
            lastCommandResult = com.tmm.mobile.protocol.CommandResult(cmdId, "applied", overwritten = listOf("targetLanguage"))))

        assertEquals(SyncBanner.APPLIED, sync.awaitConfirmation(cmdId))
        assertEquals(listOf("targetLanguage"), sync.overwritten(cmdId))
    }

    @Test fun reconcile_superseded_rejectsWithoutResend() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val cmdId = sync.change("targetLanguage", "ENG")
        sync.sendPendingCommands()

        sync.reconcile(pcSettings(version = 4).copy(lastCommandId = cmdId,
            lastCommandResult = com.tmm.mobile.protocol.CommandResult(cmdId, "rejected", "superseded")))

        assertTrue(pending().isEmpty())
        assertEquals(SyncBanner.REJECTED, sync.awaitConfirmation(cmdId))
        assertEquals("superseded", sync.rejectCode(cmdId))
    }

    // R8 : un renvoi apres stale_version garde la date de la modification (c'est elle qui departage cote PC).
    @Test fun reconcile_staleVersion_rebaseKeepsCreatedAt() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val cmdId = sync.change("targetLanguage", "ENG")
        val createdAt = pending().let { ProtocolJson.decodeFromString(ListSerializer(PendingCommand.serializer()), it!!) }.single().command.createdAt
        sync.sendPendingCommands()

        sync.reconcile(pcSettings(version = 5).copy(lastCommandId = cmdId,
            lastCommandResult = com.tmm.mobile.protocol.CommandResult(cmdId, "rejected", "stale_version")))

        val rebased = ProtocolJson.decodeFromString(ListSerializer(PendingCommand.serializer()), pending()!!).single()
        assertEquals(createdAt, rebased.command.createdAt)
    }

    // R12 : invalid_value -> rejetee sans renvoi, jamais consideree appliquee.
    @Test fun reconcile_invalidValue_rejectsWithoutResend() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val cmdId = sync.change("targetLanguage", "ENG")
        sync.sendPendingCommands()

        env.pc.acknowledgeCommand(pcSettings(version = 3), cmdId, status = "rejected", code = "invalid_value")

        assertEquals(SyncBanner.REJECTED, sync.awaitConfirmation(cmdId))
        assertTrue(pending().isEmpty())
    }

    private suspend fun commandFiles(): Int {
        val commandsId = env.prefs.driveCommandsId.first() ?: return 0
        return env.drive.files.values.count { commandsId in it.parents }
    }

    // Cycle complet telephone -> PC -> telephone : envoi, traitement PC (bridge.py), rapprochement.
    private suspend fun cycle(pc: PcSettings): PcSettings {
        env.container.settingsSync.sendPendingCommands()
        val next = env.pc.processCommands(pc)
        env.container.settingsSync.reconcile(next)
        return next
    }

    // R12 rafale : 3 changements avant le cycle PC -> une seule commande fusionnee, les 3 appliques,
    // aucun marque REJECTED (avant : c1 appliquee mais marquee rejetee, c2 perdue).
    @Test fun burst_threeChangesBeforePcCycle_allApplied() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val c1 = sync.change("targetLanguage", "ENG")
        val c2 = sync.change("concurrency", "2")
        val c3 = sync.change("targetLanguage", "DEU")

        sync.sendPendingCommands()
        assertEquals(1, commandFiles())
        val pc = env.pc.processCommands(pcSettings(version = 3))
        sync.reconcile(pc)

        assertEquals(4, pc.version)
        assertEquals("DEU", pc.values.targetLanguage)
        assertEquals(2, pc.values.concurrency)
        assertTrue(pending().isEmpty())
        for (id in listOf(c1, c2, c3)) assertEquals(SyncBanner.APPLIED, sync.awaitConfirmation(id))
    }

    // R12 : une commande en vol bloque les suivantes ; l'accuse de la precedente (lastCommandId = c1,
    // version deja au-dela de la base initiale de c2) ne fait pas marquer c2 rejetee.
    @Test fun burst_secondChangeWaitsForAck_andIsNotRejectedByPreviousAck() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val c1 = sync.change("targetLanguage", "ENG")
        sync.sendPendingCommands()
        val c2 = sync.change("concurrency", "3")
        sync.sendPendingCommands()
        assertEquals(1, commandFiles())

        var pc = cycle(pcSettings(version = 3))
        assertEquals(SyncBanner.APPLIED, sync.awaitConfirmation(c1))
        assertEquals(4, pending().single().command.baseVersion)

        sync.sendPendingCommands()
        // settings.json relu avant le passage du PC : toujours l'accuse de c1.
        sync.reconcile(pc)
        assertEquals(c2, pending().single().command.cmdId)

        pc = env.pc.processCommands(pc)
        sync.reconcile(pc)
        assertEquals(SyncBanner.APPLIED, sync.awaitConfirmation(c2))
        assertEquals("ENG", pc.values.targetLanguage)
        assertEquals(3, pc.values.concurrency)
        assertEquals(5, pc.version)
    }

    // R12 : lastCommandId d'une autre commande et version inchangee -> la commande reste en attente.
    @Test fun reconcile_otherCommandAck_keepsPending() = runTest {
        seedSettings(version = 3)
        val sync = env.container.settingsSync
        val cmdId = sync.change("targetLanguage", "ENG")
        sync.sendPendingCommands()

        sync.reconcile(pcSettings(version = 3, lastCommandId = "autre"))

        assertEquals(cmdId, pending().single().command.cmdId)
    }
}
