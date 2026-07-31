package com.local.mediaviewer.app

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.ServerSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `app session connects once and retains the last connected snapshot`() =
        runTest(dispatcher) {
            val old = connected("192.0.2.10")
            val replacement = connected("192.0.2.11")
            val session = FakeServerSession(ServerSessionState.Connecting)
            val viewModel = AppSessionViewModel(
                session = session,
                settings = FakeServerSettings(
                    ServerConfig(lastSuccessfulIpv4 = "192.0.2.10"),
                ),
            )

            advanceUntilIdle()
            assertEquals(1, session.connectCalls)
            session.emit(old)
            runCurrent()
            session.emit(ServerSessionState.Connecting)
            runCurrent()
            assertEquals(old, viewModel.uiState.value.lastConnected)
            assertEquals(
                ServerSessionState.Connecting,
                viewModel.uiState.value.current,
            )

            session.emit(replacement)
            runCurrent()
            assertEquals(replacement, viewModel.uiState.value.lastConnected)
        }

    @Test
    fun `first failure without a successful endpoint requests configuration`() =
        runTest(dispatcher) {
            val session = FakeServerSession(ServerSessionState.Connecting)
            val viewModel = AppSessionViewModel(
                session = session,
                settings = FakeServerSettings(
                    ServerConfig(lastSuccessfulIpv4 = null),
                ),
            )
            advanceUntilIdle()

            session.emit(
                ServerSessionState.Failed(
                    error = AppError.NetworkFailure("offline"),
                    resolvedIpv4s = emptyList(),
                ),
            )
            runCurrent()

            assertTrue(viewModel.uiState.value.needsConfiguration)
            assertNull(viewModel.uiState.value.lastConnected)
        }
}

private class FakeServerSession(
    initial: ServerSessionState,
) : ServerSessionManager {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ServerSessionState> = mutableState
    var connectCalls = 0
        private set

    override suspend fun connectSaved() {
        connectCalls += 1
    }

    fun emit(next: ServerSessionState) {
        mutableState.value = next
    }

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> =
        error("unused testCandidate: $input")

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        error("unused saveCandidate: ${result.server.logicalBaseUrl}")
    }

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Failure(AppError.NetworkFailure("unused"))
}

private class FakeServerSettings(
    initial: ServerConfig,
) : ServerSettingsRepository {
    private val mutableConfig = MutableStateFlow(initial)
    override val config: Flow<ServerConfig> = mutableConfig

    override suspend fun current(): ServerConfig = mutableConfig.value

    override suspend fun save(config: ServerConfig) {
        mutableConfig.value = config
    }
}

private fun connected(ipv4: String) = ServerSessionState.Connected(
    endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://$ipv4:8080",
        ipv4 = ipv4,
    ),
    resolvedIpv4s = listOf(ipv4),
)
