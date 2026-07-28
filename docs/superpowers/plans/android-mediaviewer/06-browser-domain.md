# 目录浏览领域层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现可进入嵌套目录、返回上级、点击面包屑和发出媒体打开事件的浏览状态机。

**Architecture:** Repository 只接受逻辑目录 URL，并在每次读取时用当前 `SessionEndpoint` 映射请求 URL；因此 DDNS 更新不改变浏览身份。网络连接失败时 Repository 恰好刷新端点并重试一次；ViewModel 维护页面栈，不直接调用 DNS、HTTP 或 Navigation。

**Tech Stack:** Kotlin Coroutines、StateFlow/SharedFlow、AndroidX ViewModel、CaddyDirectoryClient。

## Global Constraints

- 两个根入口固定为 `/middle/` 与 `/pik/`。
- 支持任意深度嵌套目录。
- 页面状态为 Loading、Content、Empty、Error。
- 面包屑可返回任意已访问祖先；系统返回键先返回上级，根目录再返回首页。
- 媒体键等于逻辑媒体 URL，不包含当前 IPv4。
- 第一次网络连接失败时重新解析并重试一次，第二次失败直接显示。
- HTTP 403/404/其他状态不触发 DNS 刷新。

---

### Task 6: Browser Repository 与 BrowserViewModel

**Files:**

- Create: `app/src/main/java/com/local/mediaviewer/browser/BrowserModels.kt`
- Create: `app/src/main/java/com/local/mediaviewer/browser/BrowserRepository.kt`
- Create: `app/src/main/java/com/local/mediaviewer/browser/BrowserViewModel.kt`
- Test: `app/src/test/java/com/local/mediaviewer/browser/BrowserRepositoryTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt`

**Interfaces:**

- Consumes:

```kotlin
interface CaddyDirectoryClient {
    suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}

interface ServerSessionManager {
    val state: StateFlow<ServerSessionState>
    suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint>
}
```

- Produces:

```kotlin
data class Breadcrumb(
    val label: String,
    val logicalUrl: String,
)

data class BrowserPage(
    val root: RootShare,
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val breadcrumbs: List<Breadcrumb>,
    val entries: List<DirectoryEntry>,
)

data class MediaLaunchRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)

interface BrowserRepository {
    suspend fun openRoot(root: RootShare): AppResult<BrowserPage>
    suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage>
}

sealed interface BrowserUiState {
    data object Loading : BrowserUiState
    data class Content(val page: BrowserPage) : BrowserUiState
    data class Empty(val page: BrowserPage) : BrowserUiState
    data class Error(val error: AppError) : BrowserUiState
}
```

- [ ] **Step 1: 写 Repository 失败测试**

`BrowserRepositoryTest.kt`：

```kotlin
package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.CaddyDirectoryClient
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

        val page = (repository.openRoot(RootShare.MIDDLE) as AppResult.Success).value

        assertEquals(
            listOf(
                "http://media.example:8080/middle/" to
                    "http://192.168.1.17:8080/middle/",
            ),
            calls,
        )
        assertEquals("MiddleDir", page.breadcrumbs.single().label)
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

        val result = repository.openDirectory(
            root = RootShare.MIDDLE,
            logicalUrl = "http://media.example:8080/middle/sub/",
            breadcrumbs = listOf(
                Breadcrumb("MiddleDir", "http://media.example:8080/middle/"),
                Breadcrumb("sub", "http://media.example:8080/middle/sub/"),
            ),
        )

        assertTrue(result is AppResult.Success)
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
}

private fun endpoint(ip: String) = SessionEndpoint(
    logicalBaseUrl = "http://media.example:8080",
    requestBaseUrl = "http://$ip:8080",
    ipv4 = ip,
)

private class FakeSession(
    initial: SessionEndpoint,
    private val refreshed: SessionEndpoint = initial,
) : ServerSessionManager {
    private val mutableState = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(initial, listOf(initial.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutableState
    var refreshCalls = 0
    override suspend fun connectSaved() = Unit
    override suspend fun testCandidate(input: String) =
        error("not used")
    override suspend fun saveCandidate(
        result: com.local.mediaviewer.network.ConnectionTestResult,
    ) = Unit
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
    ) = call.invoke(logicalDirectoryUrl, requestDirectoryUrl)
}
```

- [ ] **Step 2: 运行 Repository 测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.browser.BrowserRepositoryTest'
```

Expected:

```text
Kotlin compilation fails because browser models and repository are unresolved
```

- [ ] **Step 3: 实现浏览模型**

`BrowserModels.kt`：

```kotlin
package com.local.mediaviewer.browser

import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare

data class Breadcrumb(
    val label: String,
    val logicalUrl: String,
)

data class BrowserPage(
    val root: RootShare,
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val breadcrumbs: List<Breadcrumb>,
    val entries: List<DirectoryEntry>,
)

data class MediaLaunchRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)
```

- [ ] **Step 4: 实现单次重试 Repository**

`BrowserRepository.kt`：

```kotlin
package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.CaddyDirectoryClient
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import okhttp3.HttpUrl.Companion.toHttpUrl

interface BrowserRepository {
    suspend fun openRoot(root: RootShare): AppResult<BrowserPage>
    suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage>
}

class DefaultBrowserRepository(
    private val directoryClient: CaddyDirectoryClient,
    private val session: ServerSessionManager,
) : BrowserRepository {
    override suspend fun openRoot(root: RootShare): AppResult<BrowserPage> {
        val endpoint = currentEndpoint()
            ?: return unavailable()
        val logical = endpoint.logicalBaseUrl.toHttpUrl()
            .resolve(root.path)!!
            .toString()
        return load(
            root = root,
            logicalUrl = logical,
            breadcrumbs = listOf(Breadcrumb(root.displayName, logical)),
            endpoint = endpoint,
            allowRefresh = true,
        )
    }

    override suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> {
        val endpoint = currentEndpoint()
            ?: return unavailable()
        return load(root, logicalUrl, breadcrumbs, endpoint, allowRefresh = true)
    }

    private suspend fun load(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
        endpoint: SessionEndpoint,
        allowRefresh: Boolean,
    ): AppResult<BrowserPage> {
        val requestUrl = endpoint.requestUrlFor(logicalUrl)
        return when (
            val result = directoryClient.listDirectory(logicalUrl, requestUrl)
        ) {
            is AppResult.Success -> AppResult.Success(
                BrowserPage(
                    root = root,
                    logicalDirectoryUrl = logicalUrl,
                    requestDirectoryUrl = requestUrl,
                    breadcrumbs = breadcrumbs,
                    entries = result.value,
                ),
            )
            is AppResult.Failure -> {
                if (
                    allowRefresh &&
                    result.error is AppError.NetworkFailure
                ) {
                    when (val refreshed = session.refreshAfterRequestFailure()) {
                        is AppResult.Success -> load(
                            root,
                            logicalUrl,
                            breadcrumbs,
                            refreshed.value,
                            allowRefresh = false,
                        )
                        is AppResult.Failure -> refreshed
                    }
                } else {
                    result
                }
            }
        }
    }

    private fun currentEndpoint(): SessionEndpoint? =
        (session.state.value as? ServerSessionState.Connected)?.endpoint

    private fun unavailable() = AppResult.Failure(
        AppError.NetworkFailure("服务器尚未连接"),
    )
}
```

- [ ] **Step 5: 运行 Repository 测试并确认通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.browser.BrowserRepositoryTest'
```

Expected:

```text
All three repository tests pass
```

- [ ] **Step 6: 写 ViewModel 导航失败测试**

`BrowserViewModelTest.kt`：

```kotlin
package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `根目录进入子目录发出媒体事件并返回上级`() = runTest(dispatcher) {
        val rootUrl = "http://media.example:8080/middle/"
        val subUrl = "${rootUrl}sub/"
        val video = entry(
            name = "movie.mp4",
            logicalUrl = "${subUrl}movie.mp4",
            requestUrl = "http://192.0.2.1:8080/middle/sub/movie.mp4",
            kind = MediaKind.VIDEO,
        )
        val rootPage = page(
            rootUrl,
            listOf(entry("sub", subUrl, "", MediaKind.DIRECTORY)),
        )
        val subPage = page(
            subUrl,
            listOf(video),
            listOf(
                Breadcrumb("MiddleDir", rootUrl),
                Breadcrumb("sub", subUrl),
            ),
        )
        val pages = ArrayDeque(
            listOf(rootPage, subPage, subPage),
        )
        val viewModel = BrowserViewModel(
            root = RootShare.MIDDLE,
            repository = QueueBrowserRepository(pages),
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is BrowserUiState.Content)

        viewModel.open(
            (viewModel.uiState.value as BrowserUiState.Content)
                .page.entries.single(),
        )
        advanceUntilIdle()
        val mediaDeferred = async { viewModel.mediaLaunches.first() }
        viewModel.open(video)
        assertEquals(video.logicalUrl, mediaDeferred.await().mediaKey)

        viewModel.openBreadcrumb(0)
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertFalse(viewModel.goBack())

        viewModel.open(currentPage(viewModel).entries.single())
        advanceUntilIdle()
        assertEquals(subUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertTrue(viewModel.goBack())
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertFalse(viewModel.goBack())
    }
}

private class QueueBrowserRepository(
    private val pages: ArrayDeque<BrowserPage>,
) : BrowserRepository {
    override suspend fun openRoot(root: RootShare) =
        AppResult.Success(pages.removeFirst())
    override suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ) = AppResult.Success(pages.removeFirst())
}

private fun page(
    logicalUrl: String,
    entries: List<DirectoryEntry>,
    breadcrumbs: List<Breadcrumb> =
        listOf(Breadcrumb("MiddleDir", logicalUrl)),
) = BrowserPage(
    root = RootShare.MIDDLE,
    logicalDirectoryUrl = logicalUrl,
    requestDirectoryUrl = logicalUrl.replace("media.example", "192.0.2.1"),
    breadcrumbs = breadcrumbs,
    entries = entries,
)

private fun entry(
    name: String,
    logicalUrl: String,
    requestUrl: String,
    kind: MediaKind,
) = DirectoryEntry(
    name = name,
    size = 1,
    modifiedAt = Instant.EPOCH,
    mode = 420,
    isDirectory = kind == MediaKind.DIRECTORY,
    isSymlink = false,
    logicalUrl = logicalUrl,
    requestUrl = requestUrl,
    kind = kind,
)

private fun currentPage(viewModel: BrowserViewModel) =
    when (val state = viewModel.uiState.value) {
        is BrowserUiState.Content -> state.page
        is BrowserUiState.Empty -> state.page
        else -> error("No page: $state")
    }
```

- [ ] **Step 7: 运行 ViewModel 测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.browser.BrowserViewModelTest'
```

Expected:

```text
Kotlin compilation fails because BrowserViewModel and BrowserUiState are unresolved
```

- [ ] **Step 8: 实现浏览状态机**

`BrowserViewModel.kt`：

```kotlin
package com.local.mediaviewer.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BrowserUiState {
    data object Loading : BrowserUiState
    data class Content(val page: BrowserPage) : BrowserUiState
    data class Empty(val page: BrowserPage) : BrowserUiState
    data class Error(val error: AppError) : BrowserUiState
}

class BrowserViewModel(
    private val root: RootShare,
    private val repository: BrowserRepository,
) : ViewModel() {
    private val pages = mutableListOf<BrowserPage>()
    private var pendingLoad: suspend () -> AppResult<BrowserPage> =
        { repository.openRoot(root) }

    private val mutableUiState =
        MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)
    val uiState: StateFlow<BrowserUiState> = mutableUiState.asStateFlow()

    private val mutableMediaLaunches = MutableSharedFlow<MediaLaunchRequest>()
    val mediaLaunches: SharedFlow<MediaLaunchRequest> =
        mutableMediaLaunches.asSharedFlow()

    init {
        load(pendingLoad, replaceFromIndex = 0)
    }

    fun open(entry: DirectoryEntry) {
        if (entry.kind != MediaKind.DIRECTORY) {
            viewModelScope.launch {
                mutableMediaLaunches.emit(
                    MediaLaunchRequest(
                        name = entry.name,
                        logicalUrl = entry.logicalUrl,
                        requestUrl = entry.requestUrl,
                        mediaKey = entry.logicalUrl,
                        kind = entry.kind,
                    ),
                )
            }
            return
        }
        val current = pages.lastOrNull() ?: return
        val breadcrumbs = current.breadcrumbs +
            Breadcrumb(entry.name, entry.logicalUrl)
        val loader = suspend {
            repository.openDirectory(root, entry.logicalUrl, breadcrumbs)
        }
        pendingLoad = loader
        load(loader, replaceFromIndex = pages.size)
    }

    fun openBreadcrumb(index: Int) {
        if (index !in pages.indices) return
        pages.subList(index + 1, pages.size).clear()
        show(pages[index])
    }

    fun goBack(): Boolean {
        if (pages.size <= 1) return false
        pages.removeLast()
        show(pages.last())
        return true
    }

    fun retry() {
        load(pendingLoad, replaceFromIndex = pages.size)
    }

    private fun load(
        loader: suspend () -> AppResult<BrowserPage>,
        replaceFromIndex: Int,
    ) {
        viewModelScope.launch {
            mutableUiState.value = BrowserUiState.Loading
            when (val result = loader()) {
                is AppResult.Success -> {
                    while (pages.size > replaceFromIndex) pages.removeLast()
                    if (
                        pages.lastOrNull()?.logicalDirectoryUrl !=
                        result.value.logicalDirectoryUrl
                    ) {
                        pages += result.value
                    }
                    show(result.value)
                }
                is AppResult.Failure ->
                    mutableUiState.value = BrowserUiState.Error(result.error)
            }
        }
    }

    private fun show(page: BrowserPage) {
        mutableUiState.value = if (page.entries.isEmpty()) {
            BrowserUiState.Empty(page)
        } else {
            BrowserUiState.Content(page)
        }
    }
}
```

- [ ] **Step 9: 修正并验证面包屑索引契约**

`openBreadcrumb` 的 UI 索引对应页面栈索引。Step 6 中的完整测试已经：

```kotlin
viewModel.openBreadcrumb(0)
assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
assertFalse(viewModel.goBack())

viewModel.open(currentPage(viewModel).entries.single())
advanceUntilIdle()
assertEquals(subUrl, currentPage(viewModel).logicalDirectoryUrl)
assertTrue(viewModel.goBack())
assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
assertFalse(viewModel.goBack())
```

测试只使用面包屑索引，不用标签反查页面，因此同名目录不影响导航。

- [ ] **Step 10: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.browser.*'
.\gradlew.bat lintDebug
```

Expected:

```text
Browser repository and ViewModel tests pass
Lint reports 0 errors
```

- [ ] **Step 11: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/browser `
  app/src/test/java/com/local/mediaviewer/browser
git commit -m "feat: add nested directory browsing state"
```
