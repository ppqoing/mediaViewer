package com.local.mediaviewer.settings

import com.local.mediaviewer.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ServerSettingsRepository {
    val config: Flow<ServerConfig>
    suspend fun current(): ServerConfig
    suspend fun save(config: ServerConfig)
}
