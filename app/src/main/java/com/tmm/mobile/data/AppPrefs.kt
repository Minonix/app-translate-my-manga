package com.tmm.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tmm.mobile.protocol.ProtocolJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

val Context.tmmDataStore: DataStore<Preferences> by preferencesDataStore(name = "tmm_prefs")

// Hub de synchronisation VPS (Tailscale Serve HTTPS) par defaut : servi sur le port 11443, rien n'ecoute sur 443.
const val DEFAULT_DIRECT_URL = "https://v2202610443888545433.tail8ac2b2.ts.net:11443/api/remote/v1/"

// Ancienne valeur par defaut, sans port (443 implicite, injoignable) : relue comme DEFAULT_DIRECT_URL.
internal const val LEGACY_DEFAULT_DIRECT_URL = "https://v2202610443888545433.tail8ac2b2.ts.net/api/remote/v1/"

enum class Retention { NEVER, DAYS30, AFTER_RECEIVE }

// Installations simultanees (InstallJobWorker) : 10 par defaut, bornees a l'ecriture et a la lecture
// (une valeur hors bornes deja enregistree ne doit ni bloquer la file ni relancer l'OutOfMemoryError).
const val DEFAULT_MAX_CONCURRENT_INSTALLS = 10
val CONCURRENT_INSTALLS_RANGE = 1..20

class AppPrefs(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val GOOGLE_EMAIL = stringPreferencesKey("google_email")
        val AUTH_EXPIRED = booleanPreferencesKey("auth_expired")
        val DRIVE_APP_ID = stringPreferencesKey("drive_app_id")
        val DRIVE_ROOT_ID = stringPreferencesKey("drive_root_id")
        val DRIVE_JOBS_ID = stringPreferencesKey("drive_jobs_id")
        val DRIVE_PC_ID = stringPreferencesKey("drive_pc_id")
        val DRIVE_COMMANDS_ID = stringPreferencesKey("drive_commands_id")
        val DRIVE_FILE_IDS = stringPreferencesKey("drive_file_ids")
        val LAST_CHANGES_CURSOR = stringPreferencesKey("last_changes_cursor")
        val LAST_REFRESH_AT = longPreferencesKey("last_refresh_at")
        val HEARTBEAT_JSON = stringPreferencesKey("heartbeat_json")
        val HEARTBEAT_SEEN_AT = longPreferencesKey("heartbeat_seen_at")
        val DONE_CLEARED_AT = longPreferencesKey("done_cleared_at")
        val INDEX_UPDATED_AT = stringPreferencesKey("index_updated_at")
        val SETTINGS_JSON = stringPreferencesKey("settings_json")
        val PENDING_COMMANDS_JSON = stringPreferencesKey("pending_commands_json")
        val MIHON_TREE_URI = stringPreferencesKey("mihon_tree_uri")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        val RETENTION = stringPreferencesKey("retention")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val MAX_CONCURRENT_INSTALLS = intPreferencesKey("max_concurrent_installs")
        val PROTOCOL_TOO_NEW = booleanPreferencesKey("protocol_too_new")
        // R5 : 403 storageQuotaExceeded vu cote telephone, leve au prochain envoi reussi.
        val DRIVE_FULL = booleanPreferencesKey("drive_full")
        val PC_DRIVE_DOWN = booleanPreferencesKey("pc_drive_down")
        val DIRECT_URL = stringPreferencesKey("direct_url")
        val DIRECT_TOKEN_ENC = stringPreferencesKey("direct_token_enc")
        val DIRECT_DEVICE_ID = stringPreferencesKey("direct_device_id")
        val DIRECT_PC_NAME = stringPreferencesKey("direct_pc_name")
        val DIRECT_PAIRING_EXPIRED = booleanPreferencesKey("direct_pairing_expired")
        // PC en connexion directe, en secours du Hub : memes champs que l'emplacement "direct_*".
        val PC_DIRECT_URL = stringPreferencesKey("pc_direct_url")
        val PC_DIRECT_TOKEN_ENC = stringPreferencesKey("pc_direct_token_enc")
        val PC_DIRECT_PC_NAME = stringPreferencesKey("pc_direct_pc_name")
        val PC_DIRECT_PAIRING_EXPIRED = booleanPreferencesKey("pc_direct_pairing_expired")
        val MATCHER_VERSION = intPreferencesKey("matcher_version")
        val DRIVE_LAYOUT_VERSION = intPreferencesKey("drive_layout_version")
        val JOB_RESET_VERSION = intPreferencesKey("job_reset_version")
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    suspend fun setOnboardingDone(value: Boolean) = set(Keys.ONBOARDING_DONE, value)

    val googleEmail: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_EMAIL] }
    suspend fun setGoogleEmail(value: String?) = setOrRemove(Keys.GOOGLE_EMAIL, value)

    val authExpired: Flow<Boolean> = dataStore.data.map { it[Keys.AUTH_EXPIRED] ?: false }
    suspend fun setAuthExpired(value: Boolean) = set(Keys.AUTH_EXPIRED, value)

    val driveAppId: Flow<String?> = dataStore.data.map { it[Keys.DRIVE_APP_ID] }
    suspend fun setDriveAppId(value: String?) = setOrRemove(Keys.DRIVE_APP_ID, value)

    val driveRootId: Flow<String?> = dataStore.data.map { it[Keys.DRIVE_ROOT_ID] }
    suspend fun setDriveRootId(value: String?) = setOrRemove(Keys.DRIVE_ROOT_ID, value)

    val driveJobsId: Flow<String?> = dataStore.data.map { it[Keys.DRIVE_JOBS_ID] }
    suspend fun setDriveJobsId(value: String?) = setOrRemove(Keys.DRIVE_JOBS_ID, value)

    val drivePcId: Flow<String?> = dataStore.data.map { it[Keys.DRIVE_PC_ID] }
    suspend fun setDrivePcId(value: String?) = setOrRemove(Keys.DRIVE_PC_ID, value)

    val driveCommandsId: Flow<String?> = dataStore.data.map { it[Keys.DRIVE_COMMANDS_ID] }
    suspend fun setDriveCommandsId(value: String?) = setOrRemove(Keys.DRIVE_COMMANDS_ID, value)

    val driveFileIds: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[Keys.DRIVE_FILE_IDS]?.let {
            ProtocolJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
        } ?: emptyMap()
    }
    suspend fun setDriveFileIds(value: Map<String, String>) =
        set(Keys.DRIVE_FILE_IDS, ProtocolJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), value))

    val lastChangesCursor: Flow<String?> = dataStore.data.map { it[Keys.LAST_CHANGES_CURSOR] }
    suspend fun setLastChangesCursor(value: String?) = setOrRemove(Keys.LAST_CHANGES_CURSOR, value)

    val lastRefreshAt: Flow<Long> = dataStore.data.map { it[Keys.LAST_REFRESH_AT] ?: 0L }
    suspend fun setLastRefreshAt(value: Long) = set(Keys.LAST_REFRESH_AT, value)

    val heartbeatJson: Flow<String?> = dataStore.data.map { it[Keys.HEARTBEAT_JSON] }
    suspend fun setHeartbeatJson(value: String?) = setOrRemove(Keys.HEARTBEAT_JSON, value)

    // R9 : instant (horloge du telephone) ou le PC a ecrit son dernier heartbeat, deduit de l'age cote
    // serveur (en-tete Date Drive - modifiedTime, ou reception en direct) : insensible au decalage d'horloge.
    val heartbeatSeenAt: Flow<Long?> = dataStore.data.map { it[Keys.HEARTBEAT_SEEN_AT] }
    suspend fun setHeartbeatSeenAt(value: Long) = set(Keys.HEARTBEAT_SEEN_AT, value)

    // « Vider » de l'onglet Terminees : masque ce qui est termine avant cet instant, sans supprimer
    // de job (R7 : un chapitre installe puis supprime ne doit pas repartir avec le suivi).
    val doneClearedAt: Flow<Long?> = dataStore.data.map { it[Keys.DONE_CLEARED_AT] }
    suspend fun setDoneClearedAt(value: Long) = set(Keys.DONE_CLEARED_AT, value)

    val indexUpdatedAt: Flow<String?> = dataStore.data.map { it[Keys.INDEX_UPDATED_AT] }
    suspend fun setIndexUpdatedAt(value: String?) = setOrRemove(Keys.INDEX_UPDATED_AT, value)

    val settingsJson: Flow<String?> = dataStore.data.map { it[Keys.SETTINGS_JSON] }
    suspend fun setSettingsJson(value: String?) = setOrRemove(Keys.SETTINGS_JSON, value)

    val pendingCommandsJson: Flow<String?> = dataStore.data.map { it[Keys.PENDING_COMMANDS_JSON] }
    suspend fun setPendingCommandsJson(value: String?) = setOrRemove(Keys.PENDING_COMMANDS_JSON, value)

    val mihonTreeUri: Flow<String?> = dataStore.data.map { it[Keys.MIHON_TREE_URI] }
    suspend fun setMihonTreeUri(value: String?) = setOrRemove(Keys.MIHON_TREE_URI, value)

    val lastScanAt: Flow<Long> = dataStore.data.map { it[Keys.LAST_SCAN_AT] ?: 0L }
    suspend fun setLastScanAt(value: Long) = set(Keys.LAST_SCAN_AT, value)

    val retention: Flow<Retention> = dataStore.data.map {
        when (it[Keys.RETENTION]) {
            "days30" -> Retention.DAYS30
            "after_receive" -> Retention.AFTER_RECEIVE
            else -> Retention.NEVER
        }
    }
    suspend fun setRetention(value: Retention) = set(
        Keys.RETENTION,
        when (value) {
            Retention.NEVER -> "never"
            Retention.DAYS30 -> "days30"
            Retention.AFTER_RECEIVE -> "after_receive"
        },
    )

    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[Keys.WIFI_ONLY] ?: false }
    suspend fun setWifiOnly(value: Boolean) = set(Keys.WIFI_ONLY, value)

    val maxConcurrentInstalls: Flow<Int> = dataStore.data.map {
        (it[Keys.MAX_CONCURRENT_INSTALLS] ?: DEFAULT_MAX_CONCURRENT_INSTALLS).coerceIn(CONCURRENT_INSTALLS_RANGE)
    }
    suspend fun setMaxConcurrentInstalls(value: Int) =
        set(Keys.MAX_CONCURRENT_INSTALLS, value.coerceIn(CONCURRENT_INSTALLS_RANGE))

    val protocolTooNew: Flow<Boolean> = dataStore.data.map { it[Keys.PROTOCOL_TOO_NEW] ?: false }
    suspend fun setProtocolTooNew(value: Boolean) = set(Keys.PROTOCOL_TOO_NEW, value)
    val driveFull: Flow<Boolean> = dataStore.data.map { it[Keys.DRIVE_FULL] ?: false }
    suspend fun setDriveFull(value: Boolean) = set(Keys.DRIVE_FULL, value)

    // Heartbeat driveOk == false : c'est le Drive du PC qui est en panne (quota ou reseau), pas le
    // compte du telephone ; bandeau neutre, jamais « Reconnecter ».
    val pcDriveDown: Flow<Boolean> = dataStore.data.map { it[Keys.PC_DRIVE_DOWN] ?: false }
    suspend fun setPcDriveDown(value: Boolean) = set(Keys.PC_DRIVE_DOWN, value)

    /**
     * Un emplacement de connexion directe : adresse, jeton chiffre (jamais en clair, voir DirectTokenStore),
     * nom du serveur et drapeau « appairage expire » (vrai apres un 401, efface au prochain appairage).
     * Chaque emplacement a son propre jeton : un 401 de l'un n'efface jamais l'autre.
     */
    inner class DirectSlot internal constructor(
        val id: String,
        private val urlKey: Preferences.Key<String>,
        private val tokenKey: Preferences.Key<String>,
        private val nameKey: Preferences.Key<String>,
        private val expiredKey: Preferences.Key<Boolean>,
        private val migrateUrl: (String) -> String = { it },
    ) {
        val url: Flow<String?> = dataStore.data.map { prefs -> prefs[urlKey]?.let(migrateUrl) }
        suspend fun setUrl(value: String?) = setOrRemove(urlKey, value)

        val tokenEnc: Flow<String?> = dataStore.data.map { it[tokenKey] }
        suspend fun setTokenEnc(value: String?) = setOrRemove(tokenKey, value)

        val pcName: Flow<String?> = dataStore.data.map { it[nameKey] }
        suspend fun setPcName(value: String?) = setOrRemove(nameKey, value)

        val pairingExpired: Flow<Boolean> = dataStore.data.map { it[expiredKey] ?: false }
        suspend fun setPairingExpired(value: Boolean) = set(expiredKey, value)
    }

    // Hub VPS 24/7, prioritaire : garde les cles historiques "direct_*" pour ne pas perdre l'appairage
    // deja fait (y compris celui d'un PC associe la avant l'arrivee du Hub).
    // Migration douce a la lecture : l'URL du Hub enregistree sans port (ancienne valeur par defaut) est lue
    // avec :11443 ; meme hote, meme serveur, le jeton reste valable. L'ecriture suivante (appairage) la remplace.
    // ponytail: la valeur stockee n'est pas reecrite ; a retirer quand plus aucune install n'a l'ancienne URL.
    val hubSlot = DirectSlot(
        "hub", Keys.DIRECT_URL, Keys.DIRECT_TOKEN_ENC, Keys.DIRECT_PC_NAME, Keys.DIRECT_PAIRING_EXPIRED,
        migrateUrl = { if (it == LEGACY_DEFAULT_DIRECT_URL) DEFAULT_DIRECT_URL else it },
    )

    // PC en direct (Tailscale), en secours quand le Hub ne repond pas ou que son etat est en retard.
    val pcSlot = DirectSlot("pc", Keys.PC_DIRECT_URL, Keys.PC_DIRECT_TOKEN_ENC, Keys.PC_DIRECT_PC_NAME, Keys.PC_DIRECT_PAIRING_EXPIRED)

    // Raccourcis historiques vers l'emplacement Hub.
    val directUrl: Flow<String?> get() = hubSlot.url
    suspend fun setDirectUrl(value: String?) = hubSlot.setUrl(value)

    val directTokenEnc: Flow<String?> get() = hubSlot.tokenEnc
    suspend fun setDirectTokenEnc(value: String?) = hubSlot.setTokenEnc(value)

    // Identite du telephone, commune aux deux emplacements : c'est le meme appareil qui s'associe au Hub et au PC.
    val directDeviceId: Flow<String?> = dataStore.data.map { it[Keys.DIRECT_DEVICE_ID] }
    suspend fun setDirectDeviceId(value: String?) = setOrRemove(Keys.DIRECT_DEVICE_ID, value)

    val directPcName: Flow<String?> get() = hubSlot.pcName
    suspend fun setDirectPcName(value: String?) = hubSlot.setPcName(value)

    // Vrai apres un 401 du Hub : le jeton a ete efface, l'ecran Reglages affiche "Appairage expire".
    val directPairingExpired: Flow<Boolean> get() = hubSlot.pairingExpired
    suspend fun setDirectPairingExpired(value: Boolean) = hubSlot.setPairingExpired(value)

    // Version des regles de rapprochement (Matcher) deja appliquees aux telechargements connus :
    // Startup refait un rematch() une fois quand elle augmente (le cache de scan ne les relit pas).
    val matcherVersion: Flow<Int> = dataStore.data.map { it[Keys.MATCHER_VERSION] ?: 0 }
    suspend fun setMatcherVersion(value: Int) = set(Keys.MATCHER_VERSION, value)

    // Version de l'arborescence Drive (APP/TranslateMyManga a la 1, App/TranslateMyManga depuis la 2) : RefreshCoordinator oublie
    // une fois les ids de dossiers en cache quand elle augmente, pour les rechercher a nouveau.
    val driveLayoutVersion: Flow<Int> = dataStore.data.map { it[Keys.DRIVE_LAYOUT_VERSION] ?: 0 }
    suspend fun setDriveLayoutVersion(value: Int) = set(Keys.DRIVE_LAYOUT_VERSION, value)

    // Version de la remise a plat des jobs deja appliquee (JobRepository.resetPendingJobsOnce).
    val jobResetVersion: Flow<Int> = dataStore.data.map { it[Keys.JOB_RESET_VERSION] ?: 0 }
    suspend fun setJobResetVersion(value: Int) = set(Keys.JOB_RESET_VERSION, value)

    /** Oublie les ids de dossiers/fichiers Drive et le curseur de poll (nouvelle arborescence). */
    suspend fun resetDriveLayout() {
        dataStore.edit {
            for (key in listOf(
                Keys.DRIVE_APP_ID, Keys.DRIVE_ROOT_ID, Keys.DRIVE_JOBS_ID, Keys.DRIVE_PC_ID,
                Keys.DRIVE_COMMANDS_ID, Keys.DRIVE_FILE_IDS, Keys.LAST_CHANGES_CURSOR,
            )) {
                it.remove(key)
            }
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private suspend fun setOrRemove(key: Preferences.Key<String>, value: String?) {
        dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}
