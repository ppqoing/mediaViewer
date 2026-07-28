package com.local.mediaviewer.image

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.readerPreferencesDataStore by preferencesDataStore(
    name = "reader_preferences",
)

class DataStoreReaderPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesRepository {
    private object Keys {
        val defaultMode =
            stringPreferencesKey("default_image_reader_mode")
    }

    override val defaultMode: Flow<ImageReaderMode> =
        dataStore.data.map { preferences ->
            preferences[Keys.defaultMode]
                ?.let { saved ->
                    ImageReaderMode.entries.firstOrNull {
                        it.name == saved
                    }
                }
                ?: ImageReaderMode.COMIC
        }

    override suspend fun currentDefaultMode(): ImageReaderMode =
        defaultMode.first()

    override suspend fun setDefaultMode(
        mode: ImageReaderMode,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.defaultMode] = mode.name
        }
    }
}
