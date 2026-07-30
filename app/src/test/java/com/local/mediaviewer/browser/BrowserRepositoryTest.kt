package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ShareAuthenticationMode
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserRepositoryTest {
    @Test
    fun `打开根目录构造逻辑 URL 并委托共享内容仓库`() = runTest {
        val logicalUrl = "http://media.example:8080/middle/"
        val requestUrl = "http://192.0.2.1:8080/middle/"
        val contentRepository = RecordingContentRepository(
            DirectoryContent(
                logicalDirectoryUrl = logicalUrl,
                requestDirectoryUrl = requestUrl,
                entries = emptyList(),
            ),
        )
        val repository = DefaultBrowserRepository(
            contentRepository = contentRepository,
            session = BrowserSession(browserEndpoint("192.0.2.1")),
        )

        val result = repository.openRoot(MIDDLE_SHARE)

        val page = (result as AppResult.Success<BrowserPage>).value
        assertEquals(listOf(logicalUrl), contentRepository.logicalUrls)
        assertEquals("MiddleDir", page.breadcrumbs.single().label)
        assertEquals(logicalUrl, page.logicalDirectoryUrl)
        assertEquals(requestUrl, page.requestDirectoryUrl)
    }

    @Test
    fun `浏览仓库用共享内容构造原有页面`() = runTest {
        val logicalUrl =
            "http://media.example:8080/middle/sub/"
        val requestUrl =
            "http://192.0.2.1:8080/middle/sub/"
        val entries = listOf(browserEntry("a.jpg"))
        val content = DirectoryContent(
            logicalDirectoryUrl = logicalUrl,
            requestDirectoryUrl = requestUrl,
            entries = entries,
        )
        val contentRepository =
            RecordingContentRepository(content)
        val repository = DefaultBrowserRepository(
            contentRepository = contentRepository,
            session = BrowserSession(browserEndpoint("192.0.2.1")),
        )
        val breadcrumbs = listOf(
            Breadcrumb("MiddleDir", logicalUrl),
        )

        val result = repository.openDirectory(
            root = MIDDLE_SHARE,
            logicalUrl = logicalUrl,
            breadcrumbs = breadcrumbs,
        )

        val page = (result as AppResult.Success<BrowserPage>).value
        assertEquals(listOf(logicalUrl), contentRepository.logicalUrls)
        assertEquals(MIDDLE_SHARE, page.root)
        assertEquals(logicalUrl, page.logicalDirectoryUrl)
        assertEquals(requestUrl, page.requestDirectoryUrl)
        assertEquals(breadcrumbs, page.breadcrumbs)
        assertEquals(entries, page.entries)
    }

    @Test
    fun `共享内容错误原样返回`() = runTest {
        val failure = AppResult.Failure(
            AppError.InvalidDirectoryResponse,
        )
        val repository = DefaultBrowserRepository(
            contentRepository = RecordingContentRepository(failure),
            session = BrowserSession(browserEndpoint("192.0.2.1")),
        )

        val result = repository.openDirectory(
            root = PIK_SHARE,
            logicalUrl = "http://media.example:8080/pik/",
            breadcrumbs = emptyList(),
        )

        assertEquals(failure, result)
    }

    @Test
    fun `服务器未连接时打开根目录不调用内容仓库`() = runTest {
        val contentRepository = RecordingContentRepository(
            result = AppResult.Failure(
                AppError.NetworkFailure("不应调用"),
            ),
        )
        val repository = DefaultBrowserRepository(
            contentRepository = contentRepository,
            session = BrowserSession(
                initial = browserEndpoint("192.0.2.1"),
                connected = false,
            ),
        )

        val result = repository.openRoot(MIDDLE_SHARE)

        assertEquals(
            AppError.NetworkFailure("服务器尚未连接"),
            (result as AppResult.Failure).error,
        )
        assertEquals(emptyList<String>(), contentRepository.logicalUrls)
    }
}

private val MIDDLE_SHARE = ServerShare(
    id = "4f01061d-9b75-4f7d-96db-49c801e96188",
    displayName = "MiddleDir",
    urlPrefix = "middle",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.ANONYMOUS,
)

private val PIK_SHARE = ServerShare(
    id = "0447a975-eccb-4802-a8f5-5f574971876c",
    displayName = "pik",
    urlPrefix = "pik",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.ANONYMOUS,
)

private fun browserEndpoint(ipv4: String) = SessionEndpoint(
    logicalBaseUrl = "http://media.example:8080",
    requestBaseUrl = "http://$ipv4:8080",
    ipv4 = ipv4,
)

private fun browserEntry(name: String) = DirectoryEntry(
    name = name,
    size = 2_048L,
    modifiedAt = Instant.parse("2026-07-28T00:00:00Z"),
    mode = 420L,
    isDirectory = false,
    isSymlink = false,
    logicalUrl =
        "http://media.example:8080/middle/sub/$name",
    requestUrl =
        "http://192.0.2.1:8080/middle/sub/$name",
    kind = MediaKind.IMAGE,
)

private class RecordingContentRepository(
    private val result: AppResult<DirectoryContent>,
) : DirectoryContentRepository {
    val logicalUrls = mutableListOf<String>()

    constructor(content: DirectoryContent) :
        this(AppResult.Success(content))

    override suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent> {
        logicalUrls += logicalDirectoryUrl
        return result
    }
}

private class BrowserSession(
    initial: SessionEndpoint,
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

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> = error("not used: $input")

    override suspend fun saveCandidate(
        result: ConnectionTestResult,
    ) = error("not used: ${result.endpoint.logicalBaseUrl}")

    override suspend fun refreshAfterRequestFailure():
        AppResult<SessionEndpoint> = error("not used")
}
