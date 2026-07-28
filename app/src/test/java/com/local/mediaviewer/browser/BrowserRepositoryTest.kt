package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.CaddyDirectoryClient
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRepositoryTest {
    @Test
    fun `打开根目录同时构造逻辑和请求 URL`() = runTest {
        val session = FakeSession(endpoint("192.168.1.17"))
        val calls = mutableListOf<Pair<String, String>>()
        val client = FakeDirectoryClient { logical, request ->
            calls += logical to request
            AppResult.Success(emptyList())
        }
        val repository = DefaultBrowserRepository(client, session)

        val result = repository.openRoot(RootShare.MIDDLE)
        val page = (result as AppResult.Success<BrowserPage>).value

        assertEquals(
            listOf(
                "http://media.example:8080/middle/" to
                    "http://192.168.1.17:8080/middle/",
            ),
            calls,
        )
        assertEquals("MiddleDir", page.breadcrumbs.single().label)
        assertEquals(
            "http://media.example:8080/middle/",
            page.logicalDirectoryUrl,
        )
    }

    @Test
    fun `网络失败只刷新一次并按新 IPv4 重试逻辑 URL`() = runTest {
        val session = FakeSession(
            endpoint("192.0.2.1"),
            refreshed = endpoint("192.0.2.2"),
        )
        val requests = mutableListOf<String>()
        val client = FakeDirectoryClient { _, request ->
            requests += request
            if (requests.size == 1) {
                AppResult.Failure(AppError.NetworkFailure("timeout"))
            } else {
                AppResult.Success(emptyList())
            }
        }
        val repository = DefaultBrowserRepository(client, session)
        val breadcrumbs = listOf(
            Breadcrumb("MiddleDir", "http://media.example:8080/middle/"),
            Breadcrumb("sub", "http://media.example:8080/middle/sub/"),
        )

        val result = repository.openDirectory(
            root = RootShare.MIDDLE,
            logicalUrl = "http://media.example:8080/middle/sub/",
            breadcrumbs = breadcrumbs,
        )

        val page = (result as AppResult.Success<BrowserPage>).value
        assertEquals(breadcrumbs, page.breadcrumbs)
        assertEquals(
            listOf(
                "http://192.0.2.1:8080/middle/sub/",
                "http://192.0.2.2:8080/middle/sub/",
            ),
            requests,
        )
        assertEquals(1, session.refreshCalls)
    }

    @Test
    fun `重试仍为网络失败时不再刷新`() = runTest {
        val session = FakeSession(
            endpoint("192.0.2.1"),
            refreshed = endpoint("192.0.2.2"),
        )
        var calls = 0
        val repository = DefaultBrowserRepository(
            FakeDirectoryClient { _, _ ->
                calls += 1
                AppResult.Failure(AppError.NetworkFailure("timeout-$calls"))
            },
            session,
        )

        val result = repository.openRoot(RootShare.MIDDLE)

        assertEquals(
            AppError.NetworkFailure("timeout-2"),
            (result as AppResult.Failure).error,
        )
        assertEquals(2, calls)
        assertEquals(1, session.refreshCalls)
    }

    @Test
    fun `HTTP 404 不刷新 DNS`() = runTest {
        val session = FakeSession(endpoint("192.0.2.1"))
        val repository = DefaultBrowserRepository(
            FakeDirectoryClient { _, _ ->
                AppResult.Failure(AppError.HttpFailure(404))
            },
            session,
        )

        val result = repository.openRoot(RootShare.PIK)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, session.refreshCalls)
    }

    @Test
    fun `服务器未连接时不发出目录请求`() = runTest {
        var calls = 0
        val repository = DefaultBrowserRepository(
            FakeDirectoryClient { _, _ ->
                calls += 1
                AppResult.Success(emptyList())
            },
            FakeSession(endpoint("192.0.2.1"), connected = false),
        )

        val result = repository.openRoot(RootShare.MIDDLE)

        assertEquals(
            AppError.NetworkFailure("服务器尚未连接"),
            (result as AppResult.Failure).error,
        )
        assertEquals(0, calls)
    }
}

private fun endpoint(ip: String) = SessionEndpoint(
    logicalBaseUrl = "http://media.example:8080",
    requestBaseUrl = "http://$ip:8080",
    ipv4 = ip,
)

private class FakeSession(
    initial: SessionEndpoint,
    private val refreshed: SessionEndpoint = initial,
    connected: Boolean = true,
) : ServerSessionManager {
    private val mutableState = MutableStateFlow<ServerSessionState>(
        if (connected) {
            ServerSessionState.Connected(initial, listOf(initial.ipv4))
        } else {
            ServerSessionState.Failed(
                AppError.NetworkFailure("offline"),
                emptyList(),
            )
        },
    )
    override val state: StateFlow<ServerSessionState> = mutableState
    var refreshCalls = 0
        private set

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> = error("not used")

    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> {
        refreshCalls += 1
        mutableState.value = ServerSessionState.Connected(
            refreshed,
            listOf(refreshed.ipv4),
        )
        return AppResult.Success(refreshed)
    }
}

private fun interface DirectoryCall {
    suspend fun invoke(
        logical: String,
        request: String,
    ): AppResult<List<DirectoryEntry>>
}

private class FakeDirectoryClient(
    private val call: DirectoryCall,
) : CaddyDirectoryClient {
    override suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>> =
        call.invoke(logicalDirectoryUrl, requestDirectoryUrl)
}
