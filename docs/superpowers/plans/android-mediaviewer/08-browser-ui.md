# 目录列表与媒体路由 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 BrowserViewModel 接入 Navigation Compose，提供紧凑目录列表、可点击面包屑、返回语义和类型安全媒体路由。

**Architecture:** `BrowserScreen` 是纯状态 Composable；`MediaViewerApp` 收集一次性媒体事件并转换为 `PlayerRoute` 或 `ImageRoute`。视频、音频和未知文件共用播放器路由，图片使用独立路由。本任务中的 `MediaRouteShell` 明确只显示文件名与返回按钮；任务 11 将 `PlayerRoute` 改为播放器页面，任务 12 将 `ImageRoute` 改为图片页面。

**Tech Stack:** Jetpack Compose、Material 3、Navigation Compose 类型安全路由、Kotlin Serialization。

## Global Constraints

- 列表不加载图片或视频缩略图。
- 每行显示类型图标、完整文件名、文件大小和修改时间。
- 文件夹优先排序由领域层保证，UI 不重新排序。
- 面包屑可点击；返回键在根目录返回首页。
- 视频、音频和未知文件进入 `PlayerRoute`；图片进入 `ImageRoute`。
- 路由编码后解码出的逻辑 URL 和请求 URL 必须逐字符相同。

---

### Task 8: BrowserScreen 与类型安全导航

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserFormatters.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/MediaRouteShell.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/browser/BrowserFormattersTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`

**Interfaces:**

- Consumes:

```kotlin
sealed interface BrowserUiState
data class MediaLaunchRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)
```

- Produces:

```kotlin
@Serializable
data class BrowserRoute(val rootId: String)

@Serializable
data class PlayerRoute(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)

@Serializable
data class ImageRoute(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
)
```

- [ ] **Step 1: 写显示格式失败测试**

`BrowserFormattersTest.kt`：

```kotlin
package com.local.mediaviewer.ui.browser

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserFormattersTest {
    @Test
    fun `目录无大小且文件按 IEC 单位格式化`() {
        assertEquals("—", formatEntrySize(0, isDirectory = true))
        assertEquals("512 B", formatEntrySize(512, isDirectory = false))
        assertEquals("1.5 KiB", formatEntrySize(1536, isDirectory = false))
        assertEquals("2.0 MiB", formatEntrySize(2L * 1024 * 1024, false))
    }

    @Test
    fun `修改时间按本地时区显示到分钟`() {
        assertEquals(
            "2026-07-28 09:02",
            formatModifiedAt(
                Instant.parse("2026-07-28T01:02:03Z"),
                ZoneId.of("Asia/Shanghai"),
            ),
        )
    }
}
```

- [ ] **Step 2: 运行格式测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.browser.BrowserFormattersTest'
```

Expected:

```text
Kotlin compilation fails because browser formatter functions are unresolved
```

- [ ] **Step 3: 实现确定性的格式函数**

`BrowserFormatters.kt`：

```kotlin
package com.local.mediaviewer.ui.browser

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatEntrySize(size: Long, isDirectory: Boolean): String {
    if (isDirectory) return "—"
    if (size < 1024) return "$size B"
    val kib = size / 1024.0
    if (kib < 1024) return String.format(Locale.ROOT, "%.1f KiB", kib)
    val mib = kib / 1024.0
    if (mib < 1024) return String.format(Locale.ROOT, "%.1f MiB", mib)
    return String.format(Locale.ROOT, "%.1f GiB", mib / 1024.0)
}

fun formatModifiedAt(
    instant: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    .withZone(zoneId)
    .format(instant)
```

- [ ] **Step 4: 扩充类型安全路由**

将 `Destinations.kt` 更新为：

```kotlin
package com.local.mediaviewer.navigation

import com.local.mediaviewer.model.MediaKind
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SettingsRoute

@Serializable
data class BrowserRoute(val rootId: String)

@Serializable
data class PlayerRoute(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)

@Serializable
data class ImageRoute(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
)
```

- [ ] **Step 5: 写 BrowserScreen 失败测试**

`BrowserScreenTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserUiState
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.ui.browser.BrowserScreen
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrowserScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun contentShowsFullNameMetadataAndForwardsClick() {
        val entry = DirectoryEntry(
            name = "很长的 動画 (1) 😀.mkv",
            size = 1536,
            modifiedAt = Instant.parse("2026-07-28T01:02:03Z"),
            mode = 420,
            isDirectory = false,
            isSymlink = false,
            logicalUrl = "http://media.example/middle/video.mkv",
            requestUrl = "http://192.0.2.1/middle/video.mkv",
            kind = MediaKind.VIDEO,
        )
        var clicked: DirectoryEntry? = null
        rule.setContent {
            BrowserScreen(
                state = BrowserUiState.Content(
                    BrowserPage(
                        RootShare.MIDDLE,
                        "http://media.example/middle/",
                        "http://192.0.2.1/middle/",
                        listOf(Breadcrumb("MiddleDir", "http://media.example/middle/")),
                        listOf(entry),
                    ),
                ),
                onEntryClick = { clicked = it },
                onBreadcrumbClick = {},
                onRetry = {},
                onBack = {},
            )
        }

        rule.onNodeWithText(entry.name).assertIsDisplayed().performClick()
        rule.onNodeWithText("1.5 KiB").assertIsDisplayed()
        rule.runOnIdle { assertEquals(entry, clicked) }
    }

    @Test
    fun emptyAndErrorHaveExplicitStates() {
        rule.setContent {
            BrowserScreen(
                state = BrowserUiState.Empty(
                    BrowserPage(
                        RootShare.PIK,
                        "http://media.example/pik/",
                        "http://192.0.2.1/pik/",
                        listOf(Breadcrumb("pik", "http://media.example/pik/")),
                        emptyList(),
                    ),
                ),
                onEntryClick = {},
                onBreadcrumbClick = {},
                onRetry = {},
                onBack = {},
            )
        }
        rule.onNodeWithText("此目录为空").assertIsDisplayed()
    }
}
```

- [ ] **Step 6: 运行 UI 测试并观察预期失败**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest
```

Expected:

```text
Android test compilation fails because BrowserScreen is unresolved
```

- [ ] **Step 7: 实现紧凑目录页面**

`BrowserScreen.kt` 的完整行为骨架：

```kotlin
package com.local.mediaviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserUiState
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.ui.components.AppErrorPanel

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    onEntryClick: (DirectoryEntry) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTitle(state)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                BrowserUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is BrowserUiState.Error ->
                    AppErrorPanel(
                        state.error.userMessage,
                        onRetry,
                    )
                is BrowserUiState.Empty -> {
                    BrowserPageContent(
                        state.page,
                        onEntryClick,
                        onBreadcrumbClick,
                    )
                    Text("此目录为空", Modifier.align(Alignment.Center))
                }
                is BrowserUiState.Content ->
                    BrowserPageContent(
                        state.page,
                        onEntryClick,
                        onBreadcrumbClick,
                    )
            }
        }
    }
}

@Composable
private fun BrowserPageContent(
    page: BrowserPage,
    onEntryClick: (DirectoryEntry) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(page.breadcrumbs) { index, crumb ->
                TextButton(onClick = { onBreadcrumbClick(index) }) {
                    Text(crumb.label)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(
                items = page.entries,
                key = { it.logicalUrl },
            ) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = kindIcon(entry.kind),
                        contentDescription = contentDescription(entry.kind),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(entry.name)
                        Text(
                            "${formatEntrySize(entry.size, entry.isDirectory)} · " +
                                formatModifiedAt(entry.modifiedAt),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun currentTitle(state: BrowserUiState): String = when (state) {
    BrowserUiState.Loading -> "目录"
    is BrowserUiState.Error -> "目录"
    is BrowserUiState.Content -> state.page.breadcrumbs.last().label
    is BrowserUiState.Empty -> state.page.breadcrumbs.last().label
}

private fun kindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.DIRECTORY -> Icons.Default.Folder
    MediaKind.VIDEO -> Icons.Default.Movie
    MediaKind.AUDIO -> Icons.Default.AudioFile
    MediaKind.IMAGE -> Icons.Default.Image
    MediaKind.UNKNOWN -> Icons.Default.InsertDriveFile
}

private fun contentDescription(kind: MediaKind) = when (kind) {
    MediaKind.DIRECTORY -> "文件夹"
    MediaKind.VIDEO -> "视频"
    MediaKind.AUDIO -> "音频"
    MediaKind.IMAGE -> "图片"
    MediaKind.UNKNOWN -> "文件"
}
```

不要给文件名设置 `maxLines = 1` 或 TextOverflow，确保完整文件名可换行。

- [ ] **Step 8: 创建可返回的媒体路由外壳**

`MediaRouteShell.kt`：

```kotlin
package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MediaRouteShell(title: String, typeLabel: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(typeLabel)
        }
    }
}
```

- [ ] **Step 9: 接入 BrowserRoute 和媒体事件**

在 `MediaViewerApp.kt`：

1. 把首页的根回调改为：

```kotlin
onOpenRoot = { root ->
    navController.navigate(BrowserRoute(root.id))
},
```

2. 在 `NavHost` 中加入：

```kotlin
composable<BrowserRoute> { entry ->
    val route = entry.toRoute<BrowserRoute>()
    val root = RootShare.fromId(route.rootId)
    val browser: BrowserViewModel = viewModel(
        key = "browser:${root.id}",
        factory = viewModelFactory {
            initializer {
                BrowserViewModel(root, container.browserRepository)
            }
        },
    )
    val state by browser.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(browser) {
        browser.mediaLaunches.collect { media ->
            if (media.kind == MediaKind.IMAGE) {
                navController.navigate(
                    ImageRoute(
                        media.name,
                        media.logicalUrl,
                        media.requestUrl,
                    ),
                )
            } else {
                navController.navigate(
                    PlayerRoute(
                        media.name,
                        media.logicalUrl,
                        media.requestUrl,
                        media.mediaKey,
                        media.kind,
                    ),
                )
            }
        }
    }
    BackHandler {
        if (!browser.goBack()) navController.popBackStack()
    }
    BrowserScreen(
        state = state,
        onEntryClick = browser::open,
        onBreadcrumbClick = browser::openBreadcrumb,
        onRetry = browser::retry,
        onBack = {
            if (!browser.goBack()) navController.popBackStack()
        },
    )
}

composable<PlayerRoute> { entry ->
    val route = entry.toRoute<PlayerRoute>()
    MediaRouteShell(route.name, "媒体播放器", navController::popBackStack)
}

composable<ImageRoute> { entry ->
    val route = entry.toRoute<ImageRoute>()
    MediaRouteShell(route.name, "图片查看器", navController::popBackStack)
}
```

3. 添加精确 import：

```kotlin
import androidx.activity.compose.BackHandler
import androidx.navigation.toRoute
import com.local.mediaviewer.browser.BrowserViewModel
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.navigation.BrowserRoute
import com.local.mediaviewer.navigation.ImageRoute
import com.local.mediaviewer.navigation.PlayerRoute
import com.local.mediaviewer.ui.browser.BrowserScreen
import com.local.mediaviewer.ui.components.MediaRouteShell
```

- [ ] **Step 10: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.browser.*'
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest
```

Expected:

```text
Formatter tests pass
BrowserScreen tests pass
Lint reports 0 errors
Typed route code compiles in Debug APK
```

- [ ] **Step 11: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt `
  app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt `
  app/src/main/java/com/local/mediaviewer/ui `
  app/src/test/java/com/local/mediaviewer/ui `
  app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt
git commit -m "feat: add directory browser UI and media routes"
```
