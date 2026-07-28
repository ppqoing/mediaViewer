package com.local.mediaviewer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import com.local.mediaviewer.model.ServerConfig
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreServerSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `首次读取返回默认地址并可保存逻辑地址与最近 IPv4`() = runTest {
        val dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreServerSettingsRepository(store)

        try {
            assertEquals(ServerConfig(), repository.current())

            val saved = ServerConfig(
                logicalBaseUrl = "http://media.example.com:8080",
                lastSuccessfulIpv4 = "203.0.113.8",
            )
            repository.save(saved)

            assertEquals(saved, repository.current())
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun `保存空最近地址会清除旧 IPv4`() = runTest {
        val store = InMemoryPreferencesDataStore()
        val repository = DataStoreServerSettingsRepository(store)

        repository.save(
            ServerConfig(
                logicalBaseUrl = "http://media.example.com",
                lastSuccessfulIpv4 = "198.51.100.12",
            ),
        )

        val cleared = ServerConfig(logicalBaseUrl = "http://media.example.com")
        repository.save(cleared)

        assertEquals(cleared, repository.current())
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
