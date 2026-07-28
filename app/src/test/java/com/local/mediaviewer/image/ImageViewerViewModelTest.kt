package com.local.mediaviewer.image

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageViewerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `首次失败刷新端点而第二次失败只显示错误`() =
        runTest(dispatcher) {
            val session = ImageFakeSession(
                SessionEndpoint(
                    "http://media.example:8080",
                    "http://192.0.2.2:8080",
                    "192.0.2.2",
                ),
            )
            val viewModel = ImageViewerViewModel(
                logicalUrl =
                    "http://media.example:8080/pik/a.png",
                initialRequestUrl =
                    "http://192.0.2.1:8080/pik/a.png",
                session = session,
            )

            viewModel.onLoadError()
            viewModel.onLoadError()
            assertTrue(
                viewModel.uiState.value.isRefreshingEndpoint,
            )
            assertNull(viewModel.uiState.value.errorMessage)
            advanceUntilIdle()
            assertEquals(
                "http://192.0.2.2:8080/pik/a.png",
                viewModel.uiState.value.requestUrl,
            )
            assertEquals(1, session.refreshCalls)

            viewModel.onLoadError()
            advanceUntilIdle()
            assertEquals(1, session.refreshCalls)
            assertEquals(
                "图片加载失败",
                viewModel.uiState.value.errorMessage,
            )

            viewModel.retry()
            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals(
                2,
                viewModel.uiState.value.requestGeneration,
            )
        }
}

private class ImageFakeSession(
    private val endpoint: SessionEndpoint,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(
            endpoint,
            listOf(endpoint.ipv4),
        ),
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var refreshCalls = 0

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(input: String) =
        error("not used")

    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit

    override suspend fun refreshAfterRequestFailure():
        AppResult<SessionEndpoint> {
        refreshCalls += 1
        return AppResult.Success(endpoint)
    }
}
