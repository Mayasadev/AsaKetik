package com.asaketik

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    private companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val LAST_CONNECTED_HID_ADDRESS = stringPreferencesKey("last_hid_address")
        val SPP_ENABLED = booleanPreferencesKey("spp_enabled")
        val UDP_ENABLED = booleanPreferencesKey("udp_enabled")
        val UDP_TARGET_IP = stringPreferencesKey("udp_target_ip")
        val UDP_TARGET_PORT = intPreferencesKey("udp_target_port")
    }

    val darkThemeFlow: Flow<Boolean> = context.dataStore.data.map { it[DARK_THEME] ?: false }
    val autoReconnectFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_RECONNECT] ?: true }
    val lastHidAddressFlow: Flow<String> = context.dataStore.data.map { it[LAST_CONNECTED_HID_ADDRESS] ?: "" }
    val sppEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[SPP_ENABLED] ?: false }
    val udpEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[UDP_ENABLED] ?: false }
    val udpTargetIpFlow: Flow<String> = context.dataStore.data.map { it[UDP_TARGET_IP] ?: "192.168.1.100" }
    val udpTargetPortFlow: Flow<Int> = context.dataStore.data.map { it[UDP_TARGET_PORT] ?: 8888 }

    suspend fun setDarkTheme(value: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = value }
    }

    suspend fun setAutoReconnect(value: Boolean) {
        context.dataStore.edit { it[AUTO_RECONNECT] = value }
    }

    suspend fun setLastHidAddress(value: String) {
        context.dataStore.edit { it[LAST_CONNECTED_HID_ADDRESS] = value }
    }

    suspend fun setSppEnabled(value: Boolean) {
        context.dataStore.edit { it[SPP_ENABLED] = value }
    }

    suspend fun setUdpEnabled(value: Boolean) {
        context.dataStore.edit { it[UDP_ENABLED] = value }
    }

    suspend fun setUdpTargetIp(value: String) {
        context.dataStore.edit { it[UDP_TARGET_IP] = value }
    }

    suspend fun setUdpTargetPort(value: Int) {
        context.dataStore.edit { it[UDP_TARGET_PORT] = value }
    }
}
