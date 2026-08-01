package com.local.mediaviewer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPreferencesRepositoryTest {
    @Test
    fun `首次提示默认未展示并可持久化为已展示`() = runTest {
        val repository = DataStorePlayerPreferencesRepository(
            InMemoryPreferencesDataStore(),
        )

        assertFalse(repository.hasShownVideoGestures.first())

        repository.markVideoGesturesShown()

        assertTrue(repository.hasShownVideoGestures.first())
    }

    @Test
    fun `自动隐藏默认三秒且五个选项均可持久化`() = runTest {
        val repository = DataStorePlayerPreferencesRepository(
            InMemoryPreferencesDataStore(),
        )

        assertEquals(
            VideoControlsAutoHide.THREE_SECONDS,
            repository.videoControlsAutoHide.first(),
        )

        VideoControlsAutoHide.entries.forEach { value ->
            repository.setVideoControlsAutoHide(value)
            assertEquals(
                value,
                repository.videoControlsAutoHide.first(),
            )
        }
    }

    @Test
    fun `未知自动隐藏存储值回退到三秒`() = runTest {
        val store = InMemoryPreferencesDataStore()
        val repository = DataStorePlayerPreferencesRepository(store)
        val key = stringPreferencesKey("video_controls_auto_hide")

        store.edit { preferences ->
            preferences[key] = "UNKNOWN_FUTURE_VALUE"
        }

        assertEquals(
            VideoControlsAutoHide.THREE_SECONDS,
            repository.videoControlsAutoHide.first(),
        )
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        return transform(state.value).also { state.value = it }
    }
}
