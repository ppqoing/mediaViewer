package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.CaddyDirectoryClient
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryContentRepositoryTest {
    @Test
    fun `当前端点生成请求 URL 并返回目录内容`() = runTest {
        val session = ContentSession(endpoint("192.0.2.1"))
        val calls = mutableListOf<Pair<String, String>>()
        val repository = DefaultDirectoryContentRepository(
            directoryClient = ContentDirectoryClient { logical, request ->
                calls += logical to request
                AppResult.Success(listOf(contentEntry("a.jpg")))
            },
            session = session,
        )

        val result = repository.load(
            "http://media.example:8080/pik/chapter/",
        )

        val content =
            (result as AppResult.Success<DirectoryContent>).value
        assertEquals(
            listOf(
                "http://media.example:8080/pik/chapter/" to
                    "http://192.0.2.1:8080/pik/chapter/",
            ),
            calls,
        )
        assertEquals(
            "http://media.example:8080/pik/chapter/",
            content.logicalDirectoryUrl,
        )
        assertEquals(
            "http://192.0.2.1:8080/pik/chapter/",
            content.requestDirectoryUrl,
        )
        assertEquals(listOf(contentEntry("a.jpg")), content.entries)
        assertEquals(0, session.refreshCalls)
    }

    @Test
    fun `网络失败刷新一次并使用新端点重试`() = runTest {
        val session = ContentSession(
            initial = endpoint("192.0.2.1"),
            refreshed = endpoint("192.0.2.2"),
        )
        val requestUrls = mutableListOf<String>()
        val repository = DefaultDirectoryContentRepository(
            directoryClient = ContentDirectoryClient { _, request ->
                requestUrls += request
                if (requestUrls.size == 1) {
                    AppResult.Failure(
                        AppError.NetworkFailure("timeout"),
                    )
                } else {
                    AppResult.Success(listOf(contentEntry("b.png")))
                }
            },
            session = session,
        )

        val result = repository.load(
            "http://media.example:8080/pik/chapter/",
        )

        assertTrue(result is AppResult.Success<*>)
        assertEquals(
            listOf(
                "http://192.0.2.1:8080/pik/chapter/",
                "http://192.0.2.2:8080/pik/chapter/",
            ),
            requestUrls,
        )
        assertEquals(1, session.refreshCalls)
    }

    @Test
    fun `第二次网络失败不再刷新`() = runTest {
        val session = ContentSession(
            initial = endpoint("192.0.2.1"),
            refreshed = endpoint("192.0.2.2"),
        )
        var directoryCalls = 0
        val repository = DefaultDirectoryContentRepository(
            directoryClient = ContentDirectoryClient { _, _ ->
                directoryCalls += 1
                AppResult.Failure(
                    AppError.NetworkFailure("timeout-$directoryCalls"),
                )
            },
            session = session,
        )

        val result = repository.load(
            "http://media.example:8080/middle/",
        )

        assertEquals(
            AppError.NetworkFailure("timeout-2"),
            (result as AppResult.Failure).error,
        )
        assertEquals(2, directoryCalls)
        assertEquals(1, session.refreshCalls)
    }

    @Test
    fun `HTTP 失败不刷新端点`() = runTest {
        val session = ContentSession(endpoint("192.0.2.1"))
        val repository = DefaultDirectoryContentRepository(
            directoryClient = ContentDirectoryClient { _, _ ->
                AppResult.Failure(AppError.HttpFailure(404))
            },
            session = session,
        )

        val result = repository.load(
            "http://media.example:8080/pik/",
        )

        assertEquals(
            AppError.HttpFailure(404),
            (result as AppResult.Failure).error,
        )
        assertEquals(0, session.refreshCalls)
    }

    @Test
    fun `服务器未连接时不调用目录客户端`() = runTest {
        var directoryCalls = 0
        val repository = DefaultDirectoryContentRepository(
            directoryClient = ContentDirectoryClient { _, _ ->
                directoryCalls += 1
                AppResult.Success(emptyList())
            },
            session = ContentSession(
                initial = endpoint("192.0.2.1"),
                connected = false,
            ),
        )

        val result = repository.load(
            "http://media.example:8080/middle/",
        )

        assertEquals(
            AppError.NetworkFailure("服务器尚未连接"),
            (result as AppResult.Failure).error,
        )
        assertEquals(0, directoryCalls)
    }
}

private fun endpoint(ipv4: String) = SessionEndpoint(
    logicalBaseUrl = "http://media.example:8080",
    requestBaseUrl = "http://$ipv4:8080",
    ipv4 = ipv4,
)

private fun contentEntry(name: String) = DirectoryEntry(
    name = name,
    size = 1_024L,
    modifiedAt = Instant.parse("2026-07-28T00:00:00Z"),
    mode = 420L,
    isDirectory = false,
    isSymlink = false,
    logicalUrl = "http://media.example:8080/pik/chapter/$name",
    requestUrl = "http://192.0.2.1:8080/pik/chapter/$name",
    kind = MediaKind.IMAGE,
)

private class ContentSession(
    initial: SessionEndpoint,
    private val refreshed: SessionEndpoint = initial,
    connected: Boolean = true,
) : ServerSessionManager {
    private val mutableState = MutableStateFlow<ServerSessionState>(
        if (connected) {
            ServerSessionState.Connected(
                initial,
                listOf(initial.ipv4),
            )
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
    ): AppResult<ConnectionTestResult> = error("not used: $input")

    override suspend fun saveCandidate(
        result: ConnectionTestResult,
    ) = error("not used: ${result.endpoint.logicalBaseUrl}")

    override suspend fun refreshAfterRequestFailure():
        AppResult<SessionEndpoint> {
        refreshCalls += 1
        mutableState.value = ServerSessionState.Connected(
            refreshed,
            listOf(refreshed.ipv4),
        )
        return AppResult.Success(refreshed)
    }
}

private fun interface ContentDirectoryCall {
    suspend fun invoke(
        logical: String,
        request: String,
    ): AppResult<List<DirectoryEntry>>
}

private class ContentDirectoryClient(
    private val call: ContentDirectoryCall,
) : CaddyDirectoryClient {
    override suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>> =
        call.invoke(logicalDirectoryUrl, requestDirectoryUrl)
}
