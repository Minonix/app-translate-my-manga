package com.tmm.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tmm.mobile.protocol.ProtocolJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

val Context.tmmDataStore: DataStore<Preferences> by preferencesDataStore(name = "tmm_prefs")

enum class Retention { NEVER, DAYS30, AFTER_RECEIVE }

class AppPrefs(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val GOOGLE_EMAIL = stringPreferencesKey("google_email")
        val AUTH_EXPIRED = booleanPreferencesKey("auth_expired")
        val DRIVE_ROOT_ID = stringPreferencesKey("drive_root_id")
        val DRIVE_JOBS_ID = stringPreferencesKey("drive_jobs_id")
        val DRIVE_PC_ID = stringPreferencesKey("drive_pc_id")
        val DRIVE_COMMANDS_ID = stringPreferencesKey("drive_commands_id")
        val DRIVE_FILE_IDS = stringPreferencesKey("drive_file_ids")
        val LAST_CHANGES_CURSOR = stringPreferencesKey("last_changes_cursor")
        val LAST_REFRESH_AT = longPreferencesKey("last_refresh_at")
        val HEARTBEAT_JSON = stringPreferencesKey("heartbeat_json")
        val INDEX_UPDATED_AT = stringPreferencesKey("index_updated_at")
        val SETTINGS_JSON = stringPreferencesKey("settings_json")
        val PENDING_COMMANDS_JSON = stringPreferencesKey("pending_commands_json")
        val MIHON_TREE_URI = stringPreferencesKey("mihon_tree_uri")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        val RETENTION = stringPreferencesKey("retention")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val TRANSLATE_ON_ARRIVAL = booleanPreferencesKey("translate_on_arrival")
        val PROTOCOL_TOO_NEW = booleanPreferencesKey("protocol_too_new")
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    suspend fun setOnboardingDone(value: Boolean) = set(Keys.ONBOARDING_DONE, value)

    val googleEmail: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_EMAIL] }
    suspend fun setGoogleEmail(value: String?) = setOrRemove(Keys.GOOGLE_EMAIL, value)

    val authExpired: Flow<Boolean> = dataStore.data.map { it[Keys.AUTH_EXPIRED] ?: false }
    suspend fun setAuthExpired(value: Boolean) = set(Keys.AUTH_EXPIRED, value)

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

    val translateOnArrival: Flow<Boolean> = dataStore.data.map { it[Keys.TRANSLATE_ON_ARRIVAL] ?: true }
    suspend fun setTranslateOnArrival(value: Boolean) = set(Keys.TRANSLATE_ON_ARRIVAL, value)

    val protocolTooNew: Flow<Boolean> = dataStore.data.map { it[Keys.PROTOCOL_TOO_NEW] ?: false }
    suspend fun setProtocolTooNew(value: Boolean) = set(Keys.PROTOCOL_TOO_NEW, value)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private suspend fun setOrRemove(key: Preferences.Key<String>, value: String?) {
        dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}
