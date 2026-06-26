package com.found404.sidelink.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val RECONNECTION_TIMEOUT = longPreferencesKey("reconnection_timeout")
    private val BLACKLISTED_PACKAGES = stringSetPreferencesKey("blacklisted_packages")
    private val TARGET_DEVICE_ADDRESS = stringPreferencesKey("target_device_address")
    private val BATTERY_OPT_PROMPT_SHOWN = booleanPreferencesKey("battery_opt_prompt_shown")
    private val ALBUM_ART_QUALITY = intPreferencesKey("album_art_quality")
    private val MIRROR_MEDIA = booleanPreferencesKey("mirror_media")
    private val MIRROR_NOTIFICATION_ICONS = booleanPreferencesKey("mirror_notification_icons")
    private val SHOW_WATCH_BATTERY = booleanPreferencesKey("show_watch_battery")
    private val MAX_MIRRORED_NOTIFICATIONS = intPreferencesKey("max_mirrored_notifications")
    private val KEEP_ALIVE_INTERVAL_SEC = intPreferencesKey("keep_alive_interval_sec")
    private val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")

    val reconnectionTimeout: Flow<Long> = context.dataStore.data
        .map { it[RECONNECTION_TIMEOUT] ?: 800L }

    val albumArtQuality: Flow<Int> = context.dataStore.data
        .map { it[ALBUM_ART_QUALITY] ?: 240 }

    val blacklistedPackages: Flow<Set<String>> = context.dataStore.data
        .map { it[BLACKLISTED_PACKAGES] ?: emptySet() }

    val targetDeviceAddress: Flow<String?> = context.dataStore.data
        .map { it[TARGET_DEVICE_ADDRESS] }

    val batteryOptPromptShown: Flow<Boolean> = context.dataStore.data
        .map { it[BATTERY_OPT_PROMPT_SHOWN] ?: false }

    val mirrorMediaEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[MIRROR_MEDIA] ?: true }

    val mirrorNotificationIcons: Flow<Boolean> = context.dataStore.data
        .map { it[MIRROR_NOTIFICATION_ICONS] ?: true }

    val showWatchBattery: Flow<Boolean> = context.dataStore.data
        .map { it[SHOW_WATCH_BATTERY] ?: true }

    val maxMirroredNotifications: Flow<Int> = context.dataStore.data
        .map { it[MAX_MIRRORED_NOTIFICATIONS] ?: 50 }

    val keepAliveIntervalSec: Flow<Int> = context.dataStore.data
        .map { it[KEEP_ALIVE_INTERVAL_SEC] ?: 45 }

    val autoReconnectEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[AUTO_RECONNECT] ?: true }

    suspend fun setBatteryOptPromptShown() {
        context.dataStore.edit { it[BATTERY_OPT_PROMPT_SHOWN] = true }
    }

    suspend fun setReconnectionTimeout(timeout: Long) {
        context.dataStore.edit { it[RECONNECTION_TIMEOUT] = timeout }
    }

    suspend fun setTargetDeviceAddress(address: String?) {
        context.dataStore.edit { preferences ->
            if (address == null) preferences.remove(TARGET_DEVICE_ADDRESS)
            else preferences[TARGET_DEVICE_ADDRESS] = address
        }
    }

    suspend fun togglePackageBlacklist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_PACKAGES] ?: emptySet()
            preferences[BLACKLISTED_PACKAGES] =
                if (current.contains(packageName)) current - packageName
                else current + packageName
        }
    }

    suspend fun addToBlacklist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_PACKAGES] ?: emptySet()
            preferences[BLACKLISTED_PACKAGES] = current + packageName
        }
    }

    suspend fun setAlbumArtQuality(quality: Int) {
        context.dataStore.edit { it[ALBUM_ART_QUALITY] = quality }
    }

    suspend fun setMirrorMediaEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MIRROR_MEDIA] = enabled }
    }

    suspend fun setMirrorNotificationIcons(enabled: Boolean) {
        context.dataStore.edit { it[MIRROR_NOTIFICATION_ICONS] = enabled }
    }

    suspend fun setShowWatchBattery(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_WATCH_BATTERY] = enabled }
    }

    suspend fun setMaxMirroredNotifications(max: Int) {
        context.dataStore.edit { it[MAX_MIRRORED_NOTIFICATIONS] = max.coerceIn(10, 100) }
    }

    suspend fun setKeepAliveIntervalSec(seconds: Int) {
        context.dataStore.edit { it[KEEP_ALIVE_INTERVAL_SEC] = seconds.coerceIn(15, 120) }
    }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_RECONNECT] = enabled }
    }
}
