package com.local.mediaviewer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
