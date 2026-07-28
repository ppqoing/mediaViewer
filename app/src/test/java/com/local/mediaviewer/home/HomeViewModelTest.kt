package com.local.mediaviewer.home

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
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
    fun `创建时连接并映射选中 IPv4`() = runTest(dispatcher) {
        val session = HomeFakeSession()
        val viewModel = HomeViewModel(session)

        advanceUntilIdle()

        assertEquals(1, session.connectCalls)
        assertEquals(
            HomeUiState.Connected("192.168.1.17"),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `失败状态显示中文错误且重试再次连接`() = runTest(dispatcher) {
        val session = HomeFakeSession(failFirst = true)
        val viewModel = HomeViewModel(session)
        advanceUntilIdle()

        assertEquals(
            HomeUiState.Error("网络连接失败：timeout"),
            viewModel.uiState.value,
        )

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, session.connectCalls)
        assertEquals(
            HomeUiState.Connected("192.168.1.17"),
            viewModel.uiState.value,
        )
    }
}

private class HomeFakeSession(
    private val failFirst: Boolean = false,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connecting,
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var connectCalls = 0
        private set

    override suspend fun connectSaved() {
        connectCalls += 1
        mutable.value = if (failFirst && connectCalls == 1) {
            ServerSessionState.Failed(
                AppError.NetworkFailure("timeout"),
                emptyList(),
            )
        } else {
            ServerSessionState.Connected(
                SessionEndpoint(
                    "http://media.example:8080",
                    "http://192.168.1.17:8080",
                    "192.168.1.17",
                ),
                listOf("192.168.1.17"),
            )
        }
    }

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> = error("not used")

    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Failure(AppError.NetworkFailure("not used"))
}
