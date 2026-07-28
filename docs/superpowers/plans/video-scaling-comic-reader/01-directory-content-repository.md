# TODO 01 Shared Directory Content Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把目录 URL 解析、Caddy 请求和单次端点刷新从浏览页面仓库中抽出，形成浏览器与图片阅读器共用的无 UI 状态目录内容仓库。

**Architecture:** `DirectoryContentRepository` 只接收稳定逻辑目录 URL，基于当前 `ServerSessionManager` 生成实际 IPv4 请求 URL，并在网络失败时刷新一次端点。`BrowserRepository` 保留根目录、面包屑和 `BrowserPage` 组装职责。

**Tech Stack:** Kotlin、Coroutines、OkHttp/Caddy 现有接口、JUnit 4、kotlinx-coroutines-test。

## Global Constraints

- 不改变 Caddy JSON、HTTP、DNS A/IPv4 和两固定根目录契约。
- 每次 `load()` 最多自动刷新一次。
- HTTP/解析错误不刷新 DNS。
- 输出条目排序继续由现有 `DefaultDirectoryJsonParser` 决定。
- 本任务只重构，不改变目录页可见行为。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/browser/DirectoryContentRepository.kt`
- Create: `app/src/test/java/com/local/mediaviewer/browser/DirectoryContentRepositoryTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/browser/BrowserRepository.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/browser/BrowserRepositoryTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`

## Interfaces

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
data class DirectoryContent(
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val entries: List<DirectoryEntry>,
)

interface DirectoryContentRepository {
    suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent>
}

class DefaultDirectoryContentRepository(
    private val directoryClient: CaddyDirectoryClient,
    private val session: ServerSessionManager,
) : DirectoryContentRepository
```

Later tasks consume `DirectoryContentRepository.load()` directly from
`ImageReaderViewModel`.

- Updated browser constructor:

```kotlin
class DefaultBrowserRepository(
    private val contentRepository: DirectoryContentRepository,
    private val session: ServerSessionManager,
) : BrowserRepository
```

## Steps

- [ ] **Step 1: Write failing directory content tests**

Create `DirectoryContentRepositoryTest.kt` with five focused tests:

```kotlin
@Test
fun `当前端点生成请求 URL 并返回目录内容`() = runTest {
    val session = FakeSession(endpoint("192.0.2.1"))
    val calls = mutableListOf<Pair<String, String>>()
    val repository = DefaultDirectoryContentRepository(
        directoryClient = FakeDirectoryClient { logical, request ->
            calls += logical to request
            AppResult.Success(listOf(entry("a.jpg")))
        },
        session = session,
    )

    val result = repository.load(
        "http://media.example:8080/pik/chapter/",
    )

    val content =
        (result as AppResult.Success<DirectoryContent>).value
    assertEquals(
        "http://192.0.2.1:8080/pik/chapter/",
        content.requestDirectoryUrl,
    )
    assertEquals(1, content.entries.size)
    assertEquals(0, session.refreshCalls)
}
```

Add tests named:

```text
网络失败刷新一次并使用新端点重试
第二次网络失败不再刷新
HTTP 失败不刷新端点
服务器未连接时不调用目录客户端
```

The retry test must assert exact request order:

```kotlin
assertEquals(
    listOf(
        "http://192.0.2.1:8080/pik/chapter/",
        "http://192.0.2.2:8080/pik/chapter/",
    ),
    requestUrls,
)
assertEquals(1, session.refreshCalls)
```

- [ ] **Step 2: Run the new test and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.browser.DirectoryContentRepositoryTest'
```

Expected: compilation fails because `DirectoryContentRepository` and
`DefaultDirectoryContentRepository` do not exist.

- [ ] **Step 3: Implement the repository**

Create `DirectoryContentRepository.kt`:

```kotlin
package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.CaddyDirectoryClient
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState

data class DirectoryContent(
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val entries: List<DirectoryEntry>,
)

interface DirectoryContentRepository {
    suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent>
}

class DefaultDirectoryContentRepository(
    private val directoryClient: CaddyDirectoryClient,
    private val session: ServerSessionManager,
) : DirectoryContentRepository {
    override suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent> {
        val endpoint = currentEndpoint() ?: return unavailable()
        return loadWith(
            logicalDirectoryUrl = logicalDirectoryUrl,
            endpoint = endpoint,
            allowRefresh = true,
        )
    }

    private suspend fun loadWith(
        logicalDirectoryUrl: String,
        endpoint: SessionEndpoint,
        allowRefresh: Boolean,
    ): AppResult<DirectoryContent> {
        val requestUrl =
            endpoint.requestUrlFor(logicalDirectoryUrl)
        return when (
            val result = directoryClient.listDirectory(
                logicalDirectoryUrl,
                requestUrl,
            )
        ) {
            is AppResult.Success -> AppResult.Success(
                DirectoryContent(
                    logicalDirectoryUrl = logicalDirectoryUrl,
                    requestDirectoryUrl = requestUrl,
                    entries = result.value,
                ),
            )

            is AppResult.Failure -> {
                if (
                    allowRefresh &&
                    result.error is AppError.NetworkFailure
                ) {
                    when (
                        val refreshed =
                            session.refreshAfterRequestFailure()
                    ) {
                        is AppResult.Success -> loadWith(
                            logicalDirectoryUrl,
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
        (session.state.value as? ServerSessionState.Connected)
            ?.endpoint

    private fun unavailable(): AppResult.Failure =
        AppResult.Failure(
            AppError.NetworkFailure("服务器尚未连接"),
        )
}
```

- [ ] **Step 4: Run repository tests**

Run the Step 2 command.

Expected: all five tests pass.

- [ ] **Step 5: Write failing browser adapter tests**

Replace browser repository network retry assertions with adapter assertions:

```kotlin
@Test
fun `浏览仓库用共享内容构造原有页面`() = runTest {
    val content = DirectoryContent(
        logicalDirectoryUrl =
            "http://media.example:8080/middle/sub/",
        requestDirectoryUrl =
            "http://192.0.2.1:8080/middle/sub/",
        entries = listOf(entry("a.jpg")),
    )
    val repository = DefaultBrowserRepository(
        contentRepository =
            FakeDirectoryContentRepository(content),
        session = FakeSession(endpoint("192.0.2.1")),
    )

    val result = repository.openDirectory(
        root = RootShare.MIDDLE,
        logicalUrl = content.logicalDirectoryUrl,
        breadcrumbs = listOf(
            Breadcrumb("MiddleDir", content.logicalDirectoryUrl),
        ),
    )

    val page = (result as AppResult.Success<BrowserPage>).value
    assertEquals(content.requestDirectoryUrl, page.requestDirectoryUrl)
    assertEquals(content.entries, page.entries)
}
```

Keep explicit tests for root URL construction and disconnected server.

- [ ] **Step 6: Refactor `DefaultBrowserRepository`**

Remove direct `CaddyDirectoryClient` retry code. Its private `load()` becomes:

```kotlin
private suspend fun load(
    root: RootShare,
    logicalUrl: String,
    breadcrumbs: List<Breadcrumb>,
): AppResult<BrowserPage> =
    when (val result = contentRepository.load(logicalUrl)) {
        is AppResult.Success -> AppResult.Success(
            BrowserPage(
                root = root,
                logicalDirectoryUrl =
                    result.value.logicalDirectoryUrl,
                requestDirectoryUrl =
                    result.value.requestDirectoryUrl,
                breadcrumbs = breadcrumbs,
                entries = result.value.entries,
            ),
        )

        is AppResult.Failure -> result
    }
```

`openRoot()` continues to use the connected session endpoint only to resolve
`root.path` into a logical URL. `openDirectory()` delegates the supplied
logical URL unchanged.

- [ ] **Step 7: Update dependency wiring and fakes**

In `DefaultAppContainer` construct once:

```kotlin
override val directoryContentRepository:
    DirectoryContentRepository =
    DefaultDirectoryContentRepository(
        directoryClient = directoryClient,
        session = sessionManager,
    )

override val browserRepository: BrowserRepository =
    DefaultBrowserRepository(
        contentRepository = directoryContentRepository,
        session = sessionManager,
    )
```

Add this property to `AppContainer`. `FakeAppContainer` supplies a strict fake
whose `load()` throws until a later image-reader test explicitly configures it.

- [ ] **Step 8: Run focused and regression tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.browser.*'
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest'
```

Expected: all browser tests and the three navigation tests pass.

- [ ] **Step 9: Review and commit**

Run:

```powershell
git diff --check
git status --short
git diff -- app/src/main app/src/test app/src/androidTest
git add app/src/main app/src/test app/src/androidTest
git commit -m "refactor: share directory content loading"
```

Confirm no image reader or video behavior was added in this commit.
