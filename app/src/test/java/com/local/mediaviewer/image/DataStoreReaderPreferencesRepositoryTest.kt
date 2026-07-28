package com.local.mediaviewer.image

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreReaderPreferencesRepositoryTest {
    @Test
    fun `缺少键时默认条漫并可保存单图`() = runTest {
        val store = ReaderInMemoryPreferencesDataStore()
        val repository =
            DataStoreReaderPreferencesRepository(store)

        assertEquals(
            ImageReaderMode.COMIC,
            repository.currentDefaultMode(),
        )

        repository.setDefaultMode(ImageReaderMode.SINGLE)

        assertEquals(
            ImageReaderMode.SINGLE,
            repository.currentDefaultMode(),
        )
    }

    @Test
    fun `未知保存值回退到条漫`() = runTest {
        val store = ReaderInMemoryPreferencesDataStore()
        store.edit { preferences ->
            preferences[
                stringPreferencesKey(
                    "default_image_reader_mode",
                ),
            ] = "unsupported"
        }
        val repository =
            DataStoreReaderPreferencesRepository(store)

        assertEquals(
            ImageReaderMode.COMIC,
            repository.currentDefaultMode(),
        )
    }
}

private class ReaderInMemoryPreferencesDataStore :
    DataStore<Preferences> {
    private val state =
        MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
