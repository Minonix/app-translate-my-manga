package com.tmm.mobile.data

import androidx.work.WorkManager
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.protocol.DeletePayload
import com.tmm.mobile.protocol.MihonPath
import com.tmm.mobile.protocol.PcSettings
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.SettingsCommand
import com.tmm.mobile.protocol.SettingsSchema
import com.tmm.mobile.protocol.SettingsValues
import com.tmm.mobile.transport.TransportRouter
import com.tmm.mobile.workers.enqueueOutbox
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

// PLAN_GEMINI.md 5.18/CdC 7.5.
enum class SyncBanner { SYNCED, SENDING, APPLIED, WAITING_PC }

// Format local (pas le protocole PC) : la commande elle-meme est ecrite telle quelle sur Drive par
// writeCommand(), "sent" ne sert qu'a eviter de la reecrire a chaque passage d'OutboxWorker tant
// qu'elle n'est pas confirmee.
@Serializable
data class PendingCommand(val command: SettingsCommand, val sent: Boolean = false)

private val pendingSerializer = ListSerializer(PendingCommand.serializer())
private const val CONFIRM_POLL_INTERVAL_MS = 5000L
private const val CONFIRM_MAX_ATTEMPTS = 12 // 12 x 5s = 60s, PLAN_GEMINI.md 5.18

class SettingsSync(
    private val prefs: AppPrefs,
    private val router: TransportRouter,
    private val workManager: WorkManager,
) {
    suspend fun readOnly(): Boolean = prefs.settingsJson.first() == null

    suspend fun values(): SettingsValues? {
        val settings = readSettings() ?: return null
        return applyPending(settings.values, readPending())
    }

    // Cree et persiste la commande, met en file OutboxWorker (qui l'ecrira reellement sur Drive via
    // sendPendingCommands()), retourne le cmdId pour que l'appelant puisse suivre sa confirmation.
    suspend fun change(key: String, value: String): String {
        require(SettingsSchema.isAllowed(key, value)) { "valeur non autorisee pour $key : $value" }
        val settings = requireNotNull(readSettings()) { "pas de settings_json : ecran en lecture seule" }
        val command = SettingsCommand(
            cmdId = UUID.randomUUID().toString(),
            createdAt = Instant.now().toString(),
            baseVersion = settings.version,
            changes = JsonObject(mapOf(key to JsonPrimitive(value))),
        )
        writePending(readPending() + PendingCommand(command))
        workManager.enqueueOutbox(prefs.wifiOnly.first())
        return command.cmdId
    }

    // Suppression des fichiers PC (original et traduction) de ces chapitres : meme canal pc/commands
    // que les reglages, type "delete" et changes vide (applyPending n'y change donc rien). Le PC acquitte
    // via settings.json (version+1) et republie index.json : rien n'est marque supprime cote telephone
    // avant ce nouvel index (un vieux PC ignore la commande). Retourne null sans rien mettre en file
    // si la liste est vide ou sans settings_json (aucune baseVersion connue).
    // ponytail: comme pour un reglage, la commande reste dans pending_commands_json (sent = true)
    // jusqu'au prochain awaitConfirmation()/removeConfirmed() de l'ecran Reglages ; sans effet sur
    // values() puisque changes est vide. A nettoyer a la reception de settings.json si la liste grossit.
    suspend fun enqueueDelete(chapters: List<MihonPath>): String? {
        if (chapters.isEmpty()) return null
        val settings = readSettings() ?: return null
        val command = SettingsCommand(
            cmdId = UUID.randomUUID().toString(),
            createdAt = Instant.now().toString(),
            type = "delete",
            baseVersion = settings.version,
            changes = JsonObject(emptyMap()),
            delete = DeletePayload(chapters = chapters, original = true, fr = true),
        )
        writePending(readPending() + PendingCommand(command))
        workManager.enqueueOutbox(prefs.wifiOnly.first())
        return command.cmdId
    }

    // Appelee par OutboxWorker : ecrit sur le canal actif (direct si le PC repond, sinon Drive) les commandes en attente pas encore envoyees.
    // ponytail: lecture-modification-ecriture non atomique sur pending_commands_json - suffisant tant
    // qu'un seul worker OutboxWorker tourne a la fois (WorkManager le garantit via OUTBOX_WORK_NAME).
    suspend fun sendPendingCommands() {
        for (p in readPending()) {
            if (p.sent) continue
            router.active().writeCommand(p.command)
            writePending(readPending().map { if (it.command.cmdId == p.command.cmdId) it.copy(sent = true) else it })
        }
    }

    // Appelee par l'ecran Reglages pendant qu'il est ouvert et qu'une commande attend confirmation :
    // relit pc/settings.json toutes les 5s, 60s au plus (PLAN_GEMINI.md 5.18).
    suspend fun awaitConfirmation(cmdId: String): SyncBanner {
        repeat(CONFIRM_MAX_ATTEMPTS) {
            val target = readPending().find { it.command.cmdId == cmdId } ?: return SyncBanner.APPLIED
            // Reseau ou session Drive en echec : tentative perdue, pas de crash (appele depuis viewModelScope).
            val settings = try {
                router.active().readSettings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("SettingsSync", "lecture settings.json impossible : ${e.message}")
                null
            }
            if (settings != null && isConfirmed(settings, target.command)) {
                removeConfirmed(settings)
                return SyncBanner.APPLIED
            }
            delay(CONFIRM_POLL_INTERVAL_MS)
        }
        return SyncBanner.WAITING_PC
    }

    private suspend fun readSettings(): PcSettings? =
        prefs.settingsJson.first()?.let { ProtocolJson.decodeFromString(PcSettings.serializer(), it) }

    private suspend fun readPending(): List<PendingCommand> =
        prefs.pendingCommandsJson.first()?.let { ProtocolJson.decodeFromString(pendingSerializer, it) } ?: emptyList()

    private suspend fun writePending(pending: List<PendingCommand>) =
        prefs.setPendingCommandsJson(if (pending.isEmpty()) null else ProtocolJson.encodeToString(pendingSerializer, pending))

    // lastCommandId == cmdId (confirmee), ou une commande de version anterieure a celle desormais
    // appliquee par le PC (supplantee par une commande plus recente) : les deux sont resolues.
    private fun isConfirmed(settings: PcSettings, command: SettingsCommand): Boolean =
        settings.lastCommandId == command.cmdId || settings.version > command.baseVersion

    private suspend fun removeConfirmed(settings: PcSettings) {
        val pending = readPending().filterNot { it.command.cmdId == settings.lastCommandId || it.command.baseVersion < settings.version }
        writePending(pending)
        // Evite d'attendre le prochain refresh() pour que values() reflete l'etat confirme.
        prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), settings))
    }

    private fun applyPending(base: SettingsValues, pending: List<PendingCommand>): SettingsValues {
        if (pending.isEmpty()) return base
        var merged = ProtocolJson.encodeToJsonElement(SettingsValues.serializer(), base).jsonObject
        for (p in pending) merged = JsonObject(merged + p.command.changes)
        return ProtocolJson.decodeFromJsonElement(SettingsValues.serializer(), merged)
    }
}
