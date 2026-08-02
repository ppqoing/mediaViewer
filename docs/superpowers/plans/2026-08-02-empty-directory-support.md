# 空目录兼容 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Android 应用把 Caddy 的顶层 JSON `null` 识别为成功空目录，进入目标路径并在屏幕中央显示“空文件夹”。

**Architecture:** 兼容只发生在 `DefaultDirectoryJsonParser` 的协议边界，统一输出非空 Kotlin 列表；现有仓库和 `BrowserViewModel` 继续根据空列表生成 `BrowserUiState.Empty`。界面只修改空状态文案，不改变错误状态、页面栈或目录列表结构。

**Tech Stack:** Kotlin 2.3.21、kotlinx.serialization 1.11.0、OkHttp/MockWebServer 5.3.0、AndroidX ViewModel、Jetpack Compose、JUnit 4。

## Global Constraints

- Caddy 顶层 JSON `null` 和 `[]` 都必须返回 `AppResult.Success(emptyList())`。
- 空目录中央文案必须精确为“空文件夹”。
- 空字符串、JSON 对象、缺字段、非法时间、非法 URL 和非 JSON 正文仍返回 `AppError.InvalidDirectoryResponse`。
- 成功进入空子目录后必须保留目标路径、面包屑和返回父目录的能力。
- 不修改 Caddy 服务端、目录仓库接口或浏览状态类型。
- 只运行本计划新增并曾经失败的定向测试；不运行完整测试套件。

---

### Task 1: 兼容 Caddy 的 `null` 空目录响应

**Files:**
- Modify: `app/src/test/java/com/local/mediaviewer/network/DirectoryJsonParserTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/network/CaddyDirectoryClientTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/network/DirectoryJsonParser.kt:30-45`

**Interfaces:**
- Consumes: `DirectoryJsonParser.parse(json: String, logicalDirectoryUrl: String, requestDirectoryUrl: String): AppResult<List<DirectoryEntry>>`。
- Produces: 顶层 `null` 和 `[]` 都返回 `AppResult.Success<List<DirectoryEntry>>(emptyList())`；其他校验规则不变。

- [ ] **Step 1: 写 parser 和 HTTP 客户端的失败测试**

在 `DirectoryJsonParserTest` 增加：

```kotlin
@Test
fun `Caddy null 和空数组都解析为空目录而其他空结构失败`() {
    val logicalUrl = "http://media.example/middle/"
    val requestUrl = "http://192.0.2.1/middle/"

    listOf("null", "[]").forEach { body ->
        val result = parser.parse(body, logicalUrl, requestUrl)
        assertTrue(result is AppResult.Success)
        assertEquals(
            emptyList<DirectoryEntry>(),
            (
                result as
                    AppResult.Success<List<DirectoryEntry>>
            ).value,
        )
    }

    listOf("", "{}", "\"\"").forEach { body ->
        assertTrue(
            parser.parse(body, logicalUrl, requestUrl) is
                AppResult.Failure,
        )
    }
}
```

在 `CaddyDirectoryClientTest` 增加：

```kotlin
@Test
fun `Caddy null 正文经过完整客户端链路返回空目录`() = runTest {
    server.enqueue(
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body("null")
            .build(),
    )
    val client = DefaultCaddyDirectoryClient(
        client = OkHttpClient(),
        parser = DefaultDirectoryJsonParser(),
        dispatchers = dispatchers,
    )

    val result = client.listDirectory(
        logicalDirectoryUrl = "http://media.example/middle/",
        requestDirectoryUrl = server.url("/middle/").toString(),
    )

    assertTrue(result is AppResult.Success)
    assertEquals(
        emptyList<DirectoryEntry>(),
        (
            result as
                AppResult.Success<List<DirectoryEntry>>
        ).value,
    )
}
```

- [ ] **Step 2: 运行两个新增测试并确认 RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*DirectoryJsonParserTest*Caddy null*' `
  --tests '*CaddyDirectoryClientTest*Caddy null*'
```

Expected: 两个测试都因 `null` 被映射为 `AppError.InvalidDirectoryResponse` 而失败；`[]` 分支仍成功。

- [ ] **Step 3: 实现最小协议兼容**

将 `DirectoryJsonParser.kt` 中 DTO 列表的解码改为可空列表并立即规范化：

```kotlin
val entries = jsonCodec
    .decodeFromString<List<CaddyEntryDto>?>(json)
    .orEmpty()
    .map { dto ->
        val logical = logicalBase.resolve(dto.url)
            ?: throw IllegalArgumentException(
                "invalid logical relative URL",
            )
        val request = requestBase.resolve(dto.url)
            ?: throw IllegalArgumentException(
                "invalid request relative URL",
            )
        DirectoryEntry(
            name = dto.name,
            size = dto.size,
            modifiedAt = Instant.parse(dto.modifiedAt),
            mode = dto.mode,
            isDirectory = dto.isDirectory,
            isSymlink = dto.isSymlink,
            logicalUrl = logical.toString(),
            requestUrl = request.toString(),
            kind = MediaClassifier.classify(
                dto.name,
                dto.isDirectory,
            ),
        )
    }
```

保留现有排序和三个异常到 `InvalidDirectoryResponse` 的映射，不增加正文字符串特判。

- [ ] **Step 4: 只重跑刚才失败的测试并确认 GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*DirectoryJsonParserTest*Caddy null*' `
  --tests '*CaddyDirectoryClientTest*Caddy null*'
```

Expected: PASS；`null` 与 `[]` 都得到空列表，`""`、`{}` 和空正文仍失败。

- [ ] **Step 5: 提交协议修复**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/network/DirectoryJsonParser.kt `
  app/src/test/java/com/local/mediaviewer/network/DirectoryJsonParserTest.kt `
  app/src/test/java/com/local/mediaviewer/network/CaddyDirectoryClientTest.kt
git commit -m "fix: accept Caddy null empty directories"
```

---

### Task 2: 保留空子目录导航并显示“空文件夹”

**Files:**
- Modify: `app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt:224-262`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt:128-141`

**Interfaces:**
- Consumes: `BrowserViewModel.open(entry: DirectoryEntry)` 和 `BrowserUiState.Empty(page: BrowserPage)`。
- Produces: 空子目录成为页面栈中的当前 `BrowserPage`；`BrowserScreen` 在 `browser_empty_state` 中显示“空文件夹”。

- [ ] **Step 1: 添加空子目录导航边界测试**

在 `BrowserViewModelTest` 增加：

```kotlin
@Test
fun `进入空子目录后保留目标路径面包屑且返回父目录`() =
    runTest(dispatcher) {
        val rootUrl = "http://media.example:8080/middle/"
        val childUrl = "${rootUrl}empty/"
        val childEntry = entry(
            name = "empty",
            logicalUrl = childUrl,
            requestUrl = "http://192.0.2.1:8080/middle/empty/",
            kind = MediaKind.DIRECTORY,
        )
        val rootPage = page(rootUrl, listOf(childEntry))
        val childPage = page(
            logicalUrl = childUrl,
            entries = emptyList(),
            breadcrumbs = listOf(
                Breadcrumb("MiddleDir", rootUrl),
                Breadcrumb("empty", childUrl),
            ),
        )
        val viewModel = BrowserViewModel(
            MIDDLE_SHARE,
            QueueBrowserRepository(
                ArrayDeque(listOf(rootPage, childPage)),
            ),
        )

        advanceUntilIdle()
        viewModel.open(childEntry)
        advanceUntilIdle()

        val empty = viewModel.uiState.value as BrowserUiState.Empty
        assertEquals(childUrl, empty.page.logicalDirectoryUrl)
        assertEquals(
            listOf("MiddleDir", "empty"),
            empty.page.breadcrumbs.map { it.label },
        )
        assertTrue(viewModel.goBack())
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
    }
```

- [ ] **Step 2: 运行导航测试并记录现有边界为 GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*BrowserViewModelTest*进入空子目录*'
```

Expected: PASS，证明解析器只需返回成功空列表，`BrowserViewModel` 无需生产代码修改。

- [ ] **Step 3: 把现有 Compose 空状态测试改成新文案并确认 RED**

将 `BrowserScreenTest.emptyDirectoryHasExplicitState` 的文案断言改为：

```kotlin
rule.onNodeWithText("空文件夹").assertIsDisplayed()
rule.onNodeWithText("路径下无文件").assertDoesNotExist()
rule.onNodeWithText("加载子目录失败").assertDoesNotExist()
rule.onNodeWithText("目录响应格式无效").assertDoesNotExist()
```

并将 `folderOnlyDirectoryShowsFoldersWithoutEmptyMessage` 的最后一行改为：

```kotlin
rule.onNodeWithText("空文件夹").assertDoesNotExist()
```

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest#emptyDirectoryHasExplicitState'
```

Expected: FAIL，找不到“空文件夹”且仍能找到旧文案。没有连接 Android 设备时，记录为 `BLOCKED_NOT_RUN_DYNAMIC`，并继续通过 `:app:compileDebugAndroidTestKotlin` 验证测试源码可编译，不能写成动态测试通过。

- [ ] **Step 4: 修改唯一的空状态文案**

在 `BrowserScreen.kt` 的 `MediaStatePanel` 中修改：

```kotlin
MediaStatePanel(
    kind = MediaStateKind.EMPTY,
    title = "空文件夹",
    modifier = Modifier
        .align(Alignment.Center)
        .testTag("browser_empty_state"),
)
```

不修改 `BrowserErrorStatus`，因此真实加载失败仍显示错误条和重试按钮。

- [ ] **Step 5: 只重跑失败的空状态测试**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest#emptyDirectoryHasExplicitState'
```

Expected: PASS，空状态仍位于 `browser_list` 中央且只显示“空文件夹”。若无设备，仅运行：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

Expected: 编译成功；动态验收继续明确为未运行。

- [ ] **Step 6: 提交导航回归与文案修复**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt `
  app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt
git commit -m "fix: show empty folders as successful pages"
```
