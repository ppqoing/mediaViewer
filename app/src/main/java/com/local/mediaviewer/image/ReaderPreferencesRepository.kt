package com.local.mediaviewer.image

import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
    val defaultMode: Flow<ImageReaderMode>

    suspend fun currentDefaultMode(): ImageReaderMode

    suspend fun setDefaultMode(mode: ImageReaderMode)
}
