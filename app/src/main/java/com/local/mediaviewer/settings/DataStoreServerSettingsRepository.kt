package com.local.mediaviewer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.mediaviewer.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")

class DataStoreServerSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : ServerSettingsRepository {
    private object Keys {
        val logicalBaseUrl = stringPreferencesKey("logical_base_url")
        val lastSuccessfulIpv4 = stringPreferencesKey("last_successful_ipv4")
    }

    override val config: Flow<ServerConfig> = dataStore.data.map { preferences ->
        ServerConfig(
            logicalBaseUrl = preferences[Keys.logicalBaseUrl] ?: ServerConfig.DEFAULT_SERVER_URL,
            lastSuccessfulIpv4 = preferences[Keys.lastSuccessfulIpv4],
        )
    }

    override suspend fun current(): ServerConfig = config.first()

    override suspend fun save(config: ServerConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.logicalBaseUrl] = config.logicalBaseUrl
            config.lastSuccessfulIpv4?.let { ipv4 ->
                preferences[Keys.lastSuccessfulIpv4] = ipv4
            } ?: preferences.remove(Keys.lastSuccessfulIpv4)
        }
    }
}
