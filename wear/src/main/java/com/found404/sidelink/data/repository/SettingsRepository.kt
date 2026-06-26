package com.found404.sidelink.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val TARGET_DEVICE_ADDRESS = stringPreferencesKey("target_device_address")

    val targetDeviceAddress: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[TARGET_DEVICE_ADDRESS]
        }

    suspend fun setTargetDeviceAddress(address: String?) {
        context.dataStore.edit { preferences ->
            if (address == null) {
                preferences.remove(TARGET_DEVICE_ADDRESS)
            } else {
                preferences[TARGET_DEVICE_ADDRESS] = address
            }
        }
    }
}
