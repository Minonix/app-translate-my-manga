package com.tmm.mobile.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Recopie exacte des schemas du CdC 7.2 et 7.5. Noms JSON identiques aux noms Kotlin.

@Serializable
data class SeriesRef(
    val title: String,
    val sourceName: String,
    val sourceId: String?,
    val url: String?,
)

@Serializable
data class ChapterRef(
    val name: String,
    val number: Double,
    val scanlator: String?,
    val url: String?,
)

@Serializable
data class MihonPath(
    val source: String,
    val series: String,
    val chapter: String,
)

@Serializable
data class FileRef(
    val name: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class RequestOptions(
    val mode: String,
    val pages: List<Int>? = null,
    val translate: Boolean = true,
    val sourceLang: String = "auto",
    val targetLang: String = "FR",
    // docs/ERREURS_PROTOCOLE.md §2 : mode translate sans fichier, « renvoie-moi la traduction que tu as
    // deja ». Omis du JSON quand null (meme raison que SettingsCommand.delete).
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val redeliver: Boolean? = null,
)

@Serializable
data class RequestJson(
    val protocol: Int = 1,
    val jobId: String,
    val createdAt: String,
    val device: String,
    val series: SeriesRef,
    val chapter: ChapterRef,
    val mihonPath: MihonPath,
    val file: FileRef?,
    val options: RequestOptions,
)

@Serializable
data class JobError(
    val code: String,
    val message: String,
    val page: Int? = null,
)

@Serializable
data class StatusJson(
    val protocol: Int = 1,
    val jobId: String,
    val updatedAt: String,
    val mode: String,
    val state: String,
    val stage: String? = null,
    val page: Int? = null,
    val pages: Int? = null,
    val engine: String? = null,
    val queuePosition: Int? = null,
    val error: JobError? = null,
)

@Serializable
data class Quality(
    val totalPages: Int,
    val translatedBubbles: Int,
    val untranslatedBubbles: Int,
    val sfxKept: Int,
    val verifiedAt: String?,
    val alerts: List<QualityAlert> = emptyList(),
)

@Serializable
data class ResultJson(
    val protocol: Int = 1,
    val jobId: String,
    val finishedAt: String,
    val mode: String,
    val file: FileRef?,
    val localFolderName: String,
    val chapterFileName: String,
    val pages: Int,
    val engine: String? = null,
    val quality: Quality? = null,
)

@Serializable
data class CancelJson(
    val jobId: String,
    val requestedAt: String,
)

@Serializable
data class ReceivedJson(
    val jobId: String,
    val receivedAt: String,
    val installedPath: String,
)

@Serializable
data class QueueCounts(
    val active: Int,
    val waiting: Int,
    val failed: Int,
)

@Serializable
data class Heartbeat(
    val protocol: Int = 1,
    val pcName: String,
    val appVersion: String,
    val updatedAt: String,
    val slots: Int,
    val activeJobIds: List<String> = emptyList(),
    val queue: QueueCounts,
    // §2 (optionnels) : sante du pont Drive vue par le PC (lastErrorCode : null ou drive_quota, jamais auth ;
    // driveOk = false signale le Drive du PC, pas l'acces du telephone).
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val driveOk: Boolean? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lastErrorCode: String? = null,
)

@Serializable
data class PcIndexChapter(
    val mihonPath: MihonPath,
    val original: Boolean,
    val fr: Boolean,
)

@Serializable
data class PcIndex(
    val protocol: Int = 1,
    val updatedAt: String,
    val chapters: List<PcIndexChapter> = emptyList(),
)

@Serializable
data class SettingsValues(
    val sourceLanguage: String,
    val targetLanguage: String,
    val translationProvider: String,
    val autoFailoverToLocalLlm: Boolean,
    val autoVerifyAfterTranslate: Boolean,
    val chapterConcurrency: Int,
    val concurrency: Int,
    val pileSize: Int,
    val useGpu: Boolean,
    val detector: String,
    val preferredOcr: String,
    val inpainter: String,
    val inpaintingSize: Int,
    val font: String,
)

@Serializable
data class PcSettings(
    val protocol: Int = 1,
    val updatedAt: String,
    val version: Int,
    val lastCommandId: String?,
    val values: SettingsValues,
    // §2 / R12 (optionnel) : accuse explicite de la derniere commande.
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lastCommandResult: CommandResult? = null,
)

// §2 : status "applied" | "rejected" ; code de rejet stale_version | invalid_value | bad_request ;
// deleted/failed : compteurs d'une commande "delete".
@Serializable
data class CommandResult(
    val cmdId: String,
    val status: String,
    val code: String? = null,
    val message: String? = null,
    val deleted: Int? = null,
    val failed: Int? = null,
    // R8 : reglages ecrases (valeur du PC remplacee) ou conserves (valeur du PC plus recente).
    val overwritten: List<String>? = null,
    val kept: List<String>? = null,
)

@Serializable
data class SettingsCommand(
    val protocol: Int = 1,
    val cmdId: String,
    val createdAt: String,
    val type: String = "settings",
    val baseVersion: Int,
    val changes: JsonObject,
    // Commande type "delete" seulement. Omis du JSON quand null : ProtocolJson ecrit les null
    // explicites (explicitNulls) et les valeurs par defaut (encodeDefaults), d'ou EncodeDefault.NEVER.
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val delete: DeletePayload? = null,
)

// Charge utile d'une commande "delete" : fichiers PC a supprimer pour ces chapitres (original et/ou
// traduction). Le PC acquitte comme un reglage (settings.json) puis republie index.json.
@Serializable
data class DeletePayload(
    val chapters: List<MihonPath>,
    val original: Boolean,
    val fr: Boolean,
)

@Serializable
data class ProtocolMarker(
    val protocol: Int = 1,
)

// Connexion directe (Tailscale) : memes contrats que Drive, plus ces quelques objets propres au HTTP.
// pc/remote.json (Drive) : url == null signifie acces direct coupe cote PC.
@Serializable
data class RemoteInfo(
    val protocol: Int = 1,
    val url: String? = null,
    val updatedAt: String,
)

@Serializable
data class HealthResponse(
    val protocol: Int = 1,
    val pcName: String,
    val appVersion: String,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class PairRequest(
    val protocol: Int = 1,
    val code: String,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
data class PairResponse(
    val token: String,
    val pcName: String,
)

@Serializable
data class InventorySeries(
    val seriesKey: String,
    val title: String,
    val sourceName: String,
    val chapters: Int,
    val downloaded: Int,
    val read: Int,
    val fr: Int,
    val downloadedPaths: List<MihonPath> = emptyList(),
)

@Serializable
data class PhoneInventory(
    val protocol: Int = 1,
    val deviceName: String,
    val updatedAt: String,
    val series: List<InventorySeries> = emptyList(),
)

// Reponse d'erreur du PC : {"error":{"code","message"}}.
@Serializable
data class RemoteErrorBody(val error: RemoteErrorDetail? = null)

@Serializable
data class RemoteErrorDetail(val code: String? = null, val message: String? = null)

// Corps de GET poll (miroir de PollResult, cote PC direct_poll).
@Serializable
data class DirectPollResponse(
    val cursor: String? = null,
    val statuses: List<StatusJson> = emptyList(),
    val results: List<ResultJson> = emptyList(),
    val heartbeat: Heartbeat? = null,
    val index: PcIndex? = null,
    val settings: PcSettings? = null,
)

// CdC 7.5, tableau des valeurs autorisees. Utilise pour construire les feuilles de choix
// (settings) et pour refuser une valeur hors liste avant envoi d'une SettingsCommand.
object SettingsSchema {
    val allowedValues: Map<String, List<String>> = mapOf(
        "sourceLanguage" to listOf("auto", "JPN", "KOR", "CHS"),
        "targetLanguage" to listOf("FRA", "ENG", "SPA", "DEU"),
        "translationProvider" to listOf(
            "gemini", "openai", "claude", "deepl", "groq", "openrouter", "tokenhub", "offline", "local_llm",
        ),
        "detector" to listOf("default", "ctd", "paddle"),
        "preferredOcr" to listOf("auto", "mocr", "48px", "32px"),
        "inpainter" to listOf("lama_large", "lama_mpe", "sd", "none"),
        "inpaintingSize" to listOf("1024", "1280", "1800", "2048"),
        "font" to listOf("Anime Ace", "Manga Temple"),
        "chapterConcurrency" to (1..4).map { it.toString() },
        "concurrency" to (1..4).map { it.toString() },
        "pileSize" to (1..3).map { it.toString() },
        "useGpu" to listOf("true", "false"),
        "autoVerifyAfterTranslate" to listOf("true", "false"),
        "autoFailoverToLocalLlm" to listOf("true", "false"),
    )

    fun isAllowed(key: String, value: String): Boolean = allowedValues[key]?.contains(value) ?: false
}
