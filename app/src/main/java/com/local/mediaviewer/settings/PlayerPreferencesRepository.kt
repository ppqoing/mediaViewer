package com.local.mediaviewer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.playerPreferencesDataStore by preferencesDataStore(
    name = "player_preferences",
)

interface PlayerPreferencesRepository {
    val hasShownVideoGestures: Flow<Boolean>

    suspend fun markVideoGesturesShown()
}

class DataStorePlayerPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PlayerPreferencesRepository {
    private object Keys {
        val hasShownVideoGestures =
            booleanPreferencesKey("has_shown_video_gestures")
    }

    override val hasShownVideoGestures: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[Keys.hasShownVideoGestures] ?: false
        }

    override suspend fun markVideoGesturesShown() {
        dataStore.edit { preferences ->
            preferences[Keys.hasShownVideoGestures] = true
        }
    }
}
