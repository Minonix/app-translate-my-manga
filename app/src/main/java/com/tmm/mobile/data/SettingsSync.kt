package com.tmm.mobile.data

import androidx.work.WorkManager
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.protocol.CommandResult
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
// REJECTED (R12) : le PC a refuse la commande (invalid_value, bad_request, stale_version deja renvoyee)
// ou l'a laissee sans accuse ; la valeur affichee doit revenir a celle du PC.
enum class SyncBanner { SYNCED, SENDING, APPLIED, WAITING_PC, REJECTED }

// Format local (pas le protocole PC) : la commande elle-meme est ecrite telle quelle sur Drive par
// writeCommand(), "sent" ne sert qu'a eviter de la reecrire a chaque passage d'OutboxWorker tant
// qu'elle n'est pas confirmee. R12 : sentAt date le dernier envoi (reemission si jamais confirmee),
// rebased marque l'unique renvoi apres stale_version, originId garde le cmdId suivi par l'ecran,
// mergedIds les cmdId d'origine des reglages fusionnes dans cette commande (envoi en rafale, R12).
@Serializable
data class PendingCommand(
    val command: SettingsCommand,
    val sent: Boolean = false,
    val sentAt: Long? = null,
    val rebased: Boolean = false,
    val originId: String? = null,
    val mergedIds: List<String> = emptyList(),
)

// cmdId suivis par l'ecran que cette commande resout : le sien (ou celui d'origine) et ceux fusionnes.
private val PendingCommand.trackedIds: List<String> get() = listOf(originId ?: command.cmdId) + mergedIds

private val pendingSerializer = ListSerializer(PendingCommand.serializer())
private const val CONFIRM_POLL_INTERVAL_MS = 5000L
private const val CONFIRM_MAX_ATTEMPTS = 12 // 12 x 5s = 60s, PLAN_GEMINI.md 5.18
// R12 : commande envoyee mais toujours sans accuse apres ce delai -> reecrite (le PC ignore un cmdId deja traite).
private const val RESEND_UNCONFIRMED_MS = 10 * 60_000L

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
    // Comme pour un reglage, la commande reste dans pending_commands_json jusqu'a son accuse, purgee
    // par reconcile() a chaque settings.json recu (RefreshCoordinator), ecran Reglages ouvert ou non.
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
    // R12 : une seule commande en vol. Le PC traite tout pc/commands en un cycle et rejette en
    // stale_version toute commande dont baseVersion != sa version : c1, c2, c3 envoyees sur la meme base
    // donneraient c1 appliquee, c2 et c3 rejetees, et lastCommandId ne decrit que la derniere. Tant que la
    // commande envoyee n'est pas accusee (reconcile la retire), rien d'autre ne part ; elle est seulement
    // reecrite apres RESEND_UNCONFIRMED_MS (fichier perdu, PC hors ligne au moment de l'envoi direct...).
    suspend fun sendPendingCommands() {
        val pending = readPending()
        val now = System.currentTimeMillis()
        val inFlight = pending.firstOrNull { it.sent }
        if (inFlight != null) {
            if (now - (inFlight.sentAt ?: 0L) < RESEND_UNCONFIRMED_MS) return
            router.active().writeCommand(inFlight.command)
            writePending(readPending().map { if (it.command.cmdId == inFlight.command.cmdId) it.copy(sentAt = now) else it })
            return
        }
        val next = mergeUnsent(pending) ?: return
        router.active().writeCommand(next.command)
        val absorbed = next.mergedIds.toSet()
        writePending(
            readPending().mapNotNull { p ->
                when {
                    p.command.cmdId == next.command.cmdId -> next.copy(sent = true, sentAt = now)
                    p.command.cmdId in absorbed || p.originId in absorbed -> null
                    else -> p
                }
            },
        )
    }

    // R12 : les reglages pas encore envoyes qui suivent la tete de file sont fusionnes en une commande
    // (meme cle : la derniere valeur gagne), sur la version la plus recente connue. Une suppression
    // (type "delete") part toujours seule et arrete la fusion, pour garder l'ordre des actions.
    private suspend fun mergeUnsent(pending: List<PendingCommand>): PendingCommand? {
        val head = pending.firstOrNull { !it.sent } ?: return null
        if (head.command.type != "settings") return head
        val batch = pending.dropWhile { it !== head }.takeWhile { !it.sent && it.command.type == "settings" }
        if (batch.size == 1) return head
        var changes = head.command.changes
        for (p in batch.drop(1)) changes = JsonObject(changes + p.command.changes)
        val base = maxOf(batch.maxOf { it.command.baseVersion }, readSettings()?.version ?: 0)
        return head.copy(
            command = head.command.copy(changes = changes, baseVersion = base),
            mergedIds = head.mergedIds + batch.drop(1).flatMap { it.trackedIds },
        )
    }

    // Appelee par l'ecran Reglages pendant qu'il est ouvert et qu'une commande attend confirmation :
    // relit pc/settings.json toutes les 5s, 60s au plus (PLAN_GEMINI.md 5.18).
    suspend fun awaitConfirmation(cmdId: String): SyncBanner {
        repeat(CONFIRM_MAX_ATTEMPTS) {
            outcomes[cmdId]?.let { return it }
            if (readPending().none { it.command.cmdId == cmdId || cmdId in it.trackedIds }) return SyncBanner.APPLIED
            // Reseau ou session Drive en echec : tentative perdue, pas de crash (appele depuis viewModelScope).
            val settings = try {
                router.active().readSettings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("SettingsSync", "lecture settings.json impossible : ${e.message}")
                null
            }
            if (settings != null) {
                storeIfNewer(settings)
                reconcile(settings)
                outcomes[cmdId]?.let { return it }
            }
            delay(CONFIRM_POLL_INTERVAL_MS)
        }
        return SyncBanner.WAITING_PC
    }

    /**
     * R12 : rapproche les commandes en attente d'un settings.json du PC. Appelee par awaitConfirmation()
     * et par RefreshCoordinator a chaque settings.json recu (purge meme ecran Reglages ferme).
     * - lastCommandId == cmdId et lastCommandResult absent ou "applied" : appliquee, retiree.
     * - rejected/stale_version : relue sur la nouvelle base et renvoyee une seule fois (nouveau cmdId,
     *   le PC ignorant un cmdId deja traite).
     * - autre rejet, ou version du PC deja au-dela de baseVersion sans accuse de cette commande : retiree
     *   sans etre consideree appliquee (le PC la refusera en stale_version ou l'a deja fait).
     */
    suspend fun reconcile(settings: PcSettings) {
        var rebasedAny = false
        val kept = mutableListOf<PendingCommand>()
        for (p in readPending()) {
            val id = p.command.cmdId
            val origin = p.originId ?: id
            val result = settings.lastCommandResult?.takeIf { it.cmdId == id }
            when {
                settings.lastCommandId == id && (result == null || result.status == "applied") -> {
                    // R8 : le PC signale les reglages dont la valeur plus ancienne venue de lui est ecrasee.
                    result?.overwritten?.takeIf { it.isNotEmpty() }?.let { o -> p.trackedIds.forEach { overwrites[it] = o } }
                    resolve(p, SyncBanner.APPLIED, result = result)
                }
                settings.lastCommandId == id && result?.code == "stale_version" && !p.rebased -> {
                    kept += PendingCommand(
                        command = p.command.copy(
                            cmdId = UUID.randomUUID().toString(),
                            baseVersion = settings.version,
                        ),
                        rebased = true,
                        originId = origin,
                        mergedIds = p.mergedIds,
                    )
                    rebasedAny = true
                }
                settings.lastCommandId == id -> {
                    AppLog.d("SettingsSync", "commande refusee par le PC : ${result?.code}")
                    resolve(p, SyncBanner.REJECTED, result?.code)
                }
                // Pas encore envoyee : simplement recalee sur la version connue, sans risque de doublon.
                !p.sent && settings.version > p.command.baseVersion ->
                    kept += p.copy(command = p.command.copy(baseVersion = settings.version))
                settings.version > p.command.baseVersion -> resolve(p, SyncBanner.REJECTED)
                else -> kept += p
            }
        }
        writePending(kept)
        // R12 : la commande en vol vient d'etre accusee -> la suivante part sans attendre un autre changement.
        if (rebasedAny || (kept.isNotEmpty() && kept.none { it.sent })) workManager.enqueueOutbox(prefs.wifiOnly.first())
    }

    private fun resolve(p: PendingCommand, banner: SyncBanner, code: String? = null, result: CommandResult? = null) {
        for (tracked in p.trackedIds) {
            outcomes[tracked] = banner
            if (result != null) commandResults[tracked] = result else commandResults.remove(tracked)
            if (code != null) rejectCodes[tracked] = code else rejectCodes.remove(tracked)
        }
    }

    // Code du refus PC (stale_version, invalid_value, bad_request) d'une commande REJECTED, pour afficher
    // son motif (ErrorCatalog.settingsRejectLabel) ; null si refusee sans accuse explicite.
    fun rejectCode(cmdId: String): String? = rejectCodes[cmdId]

    // R8 : reglages du PC que cette commande a ecrases (vide si aucun), pour le message de l'ecran.
    fun overwritten(cmdId: String): List<String> = overwrites[cmdId].orEmpty()

    // Issue des commandes resolues, par cmdId d'origine (lu par awaitConfirmation()).
    // ponytail: en memoire (SettingsSync est un singleton du conteneur) ; apres un redemarrage l'ecran
    // ne suit de toute facon plus aucune commande.
    private val outcomes = java.util.concurrent.ConcurrentHashMap<String, SyncBanner>()
    private val rejectCodes = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val overwrites = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val commandResults = java.util.concurrent.ConcurrentHashMap<String, CommandResult>()

    // Accuse detaille du PC (compteurs deleted/failed d'une commande « delete »), null sans accuse explicite.
    fun commandResult(cmdId: String): CommandResult? = commandResults[cmdId]

    // Evite d'attendre le prochain refresh() pour que values() reflete l'etat confirme.
    private suspend fun storeIfNewer(settings: PcSettings) {
        val known = readSettings()
        if (known == null || settings.version >= known.version) {
            prefs.setSettingsJson(ProtocolJson.encodeToString(PcSettings.serializer(), settings))
        }
    }

    private suspend fun readSettings(): PcSettings? =
        prefs.settingsJson.first()?.let { runCatching { ProtocolJson.decodeFromString(PcSettings.serializer(), it) }.getOrNull() }

    private suspend fun readPending(): List<PendingCommand> =
        prefs.pendingCommandsJson.first()?.let { ProtocolJson.decodeFromString(pendingSerializer, it) } ?: emptyList()

    private suspend fun writePending(pending: List<PendingCommand>) =
        prefs.setPendingCommandsJson(if (pending.isEmpty()) null else ProtocolJson.encodeToString(pendingSerializer, pending))

    private fun applyPending(base: SettingsValues, pending: List<PendingCommand>): SettingsValues {
        if (pending.isEmpty()) return base
        var merged = ProtocolJson.encodeToJsonElement(SettingsValues.serializer(), base).jsonObject
        for (p in pending) merged = JsonObject(merged + p.command.changes)
        return ProtocolJson.decodeFromJsonElement(SettingsValues.serializer(), merged)
    }
}
