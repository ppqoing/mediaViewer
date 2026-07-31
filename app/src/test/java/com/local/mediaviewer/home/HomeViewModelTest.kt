package com.local.mediaviewer.home

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ShareAuthenticationMode
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `creating home does not connect and still maps session state`() = runTest(dispatcher) {
        val session = HomeFakeSession()
        val viewModel = HomeViewModel(session)
        advanceUntilIdle()
        assertEquals(0, session.connectCalls)

        session.emit(connected("192.168.1.17"))
        runCurrent()
        assertEquals(
            HomeUiState.Connected("192.168.1.17", listOf(HOME_SHARE)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `explicit home retry connects exactly once`() = runTest(dispatcher) {
        val session = HomeFakeSession()
        val viewModel = HomeViewModel(session)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, session.connectCalls)
    }
}

private class HomeFakeSession : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connecting,
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var connectCalls = 0
        private set

    override suspend fun connectSaved() {
        connectCalls += 1
    }

    fun emit(state: ServerSessionState) {
        mutable.value = state
    }

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> = error("not used")

    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Failure(AppError.NetworkFailure("not used"))
}

private fun connected(ipv4: String) = ServerSessionState.Connected(
    endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://$ipv4:8080",
        ipv4 = ipv4,
    ),
    resolvedIpv4s = listOf(ipv4),
    shares = listOf(HOME_SHARE),
)

private val HOME_SHARE = ServerShare(
    id = "4f01061d-9b75-4f7d-96db-49c801e96188",
    displayName = "家庭相册",
    urlPrefix = "家庭相册",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.ANONYMOUS,
)
