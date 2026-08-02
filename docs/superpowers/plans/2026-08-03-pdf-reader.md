# PDF 阅读器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Android 应用增加独立 PDF 分类、筛选、临时下载和纵向连续阅读能力，且 PDF 永不进入音视频播放队列。

**Architecture:** 目录层新增 `MediaKind.PDF` 和 `PdfReaderRoute`；`PdfTemporaryFileRepository` 使用当前服务器会话把文件下载到应用缓存，`AndroidPdfDocumentFactory` 以系统 `PdfRenderer` 串行渲染页面；`PdfReaderViewModel` 管理文档和页面状态，Compose `LazyColumn` 只请求可见页及相邻页。退出路由时关闭渲染器并删除临时文件，配置变化复用同一 ViewModel 和缓存。

**Tech Stack:** Kotlin 2.3、Jetpack Compose、Navigation Compose、Android `PdfRenderer`、OkHttp 5、Coroutines、JUnit 4、Robolectric、Compose UI Test。

## Global Constraints

- `minSdk = 29`、`compileSdk = 36`、`targetSdk = 36`，不得提高最低版本。
- 使用系统 `android.graphics.pdf.PdfRenderer`，不得引入 AndroidX PDF Alpha、PDF.js 或其他 PDF 依赖。
- 阅读模式固定为纵向连续滚动，缩放范围固定为 `1×–5×`。
- PDF 只保存在 `cacheDir/pdf` 临时目录，退出阅读器后删除；配置变化不得重新下载。
- PDF 不得进入 VLC、Media3 或播放队列。
- 必须新增独立“PDF”筛选项和居中的独立 PDF 图标。
- 只做个人工具所需的定向单元测试、必要 Compose/模拟器冒烟和构建；不得加入模糊测试、独立渲染进程、广泛设备矩阵或无关审查。
- 不实现文本搜索、复制、链接、表单、批注、签名、密码输入、永久下载、离线收藏或分享。
- 保留当前工作树中的既有改动，每次提交只暂存本任务列出的文件。

---

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/local/mediaviewer/model/MediaKind.kt` | 新增 `PDF` 媒体类型 |
| `app/src/main/java/com/local/mediaviewer/network/MediaClassifier.kt` | 识别大小写不敏感的 `.pdf` |
| `app/src/main/java/com/local/mediaviewer/browser/BrowserViewModel.kt` | PDF 点击发出媒体阅读请求而非播放请求 |
| `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserFormatters.kt` | 新增 PDF 筛选契约 |
| `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt` | 展示 PDF 筛选 Chip |
| `app/src/main/java/com/local/mediaviewer/ui/browser/MediaFileRow.kt` | PDF 图标、语义和非播放菜单行为 |
| `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcons.kt` | 注册 PDF 图标 |
| `app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt` | 为防御性 MediaKind 映射补齐 PDF 分支 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt` | 为防御性队列文案补齐 PDF 分支 |
| `app/src/main/res/drawable/ic_wp_pdf.xml` | 居中的 PDF 文档矢量图标 |
| `app/src/main/java/com/local/mediaviewer/pdf/PdfTemporaryFileRepository.kt` | 会话刷新、临时下载、原子完成和缓存清理 |
| `app/src/main/java/com/local/mediaviewer/pdf/PdfFileClient.kt` | OkHttp 流式下载和空间检查 |
| `app/src/main/java/com/local/mediaviewer/pdf/PdfDocumentHandle.kt` | `PdfRenderer` 封装、页尺寸和位图渲染 |
| `app/src/main/java/com/local/mediaviewer/pdf/PdfPageBitmapCache.kt` | 有界页面位图 LRU 缓存 |
| `app/src/main/java/com/local/mediaviewer/pdf/PdfReaderModels.kt` | ViewModel/UI 状态模型 |
| `app/src/main/java/com/local/mediaviewer/pdf/PdfReaderViewModel.kt` | 文档加载、可见页请求、重试和释放 |
| `app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt` | 新增类型安全 `PdfReaderRoute` |
| `app/src/main/java/com/local/mediaviewer/ui/pdf/PdfReaderPolicy.kt` | 当前页、预取集合和渲染宽度纯函数 |
| `app/src/main/java/com/local/mediaviewer/ui/pdf/PdfTransform.kt` | PDF 连续阅读缩放与横向偏移计算 |
| `app/src/main/java/com/local/mediaviewer/ui/pdf/PdfReaderScreen.kt` | 加载、错误、连续页面和半透明工具栏 |
| `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt` | 提供 PDF 文件仓库和文档工厂 |
| `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt` | 从目录请求导航到 PDF 阅读器并创建 ViewModel |
| `app/src/main/java/com/local/mediaviewer/MediaViewerApplication.kt` | 启动时清理过期 PDF 缓存 |
| `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt` | 导航测试 PDF 假依赖 |
| `app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt` | 转发新增 AppContainer 属性 |

## Task 1：PDF 分类、筛选、图标与浏览器请求

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/model/MediaKind.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/network/MediaClassifier.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/browser/BrowserViewModel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserFormatters.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/MediaFileRow.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcons.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt`
- Create: `app/src/main/res/drawable/ic_wp_pdf.xml`
- Test: `app/src/test/java/com/local/mediaviewer/network/MediaClassifierTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/browser/BrowserFormattersTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/icons/MediaIconsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`

**Interfaces:**
- Consumes: 现有 `MediaLaunchRequest`、`DirectoryEntry` 和 `BrowserFilter`。
- Produces: `MediaKind.PDF`、`BrowserFilter.PDF`、`MediaIcons.Pdf`；PDF 点击继续通过 `SharedFlow<MediaLaunchRequest>` 发出，`kind == MediaKind.PDF`。

- [ ] **Step 1: 写入失败的分类、筛选和请求测试**

在现有测试中加入下列断言：

```kotlin
assertEquals(
    MediaKind.PDF,
    MediaClassifier.classify("说明书.PdF", false),
)

val pdf = browserEntry("manual.pdf", MediaKind.PDF)
assertTrue(BrowserFilter.PDF.accepts(pdf))
assertFalse(BrowserFilter.PDF.accepts(image))
assertTrue(BrowserFilter.ALL.accepts(pdf))
```

在 `BrowserViewModelTest` 的媒体打开场景中监听 `mediaLaunches.first()`，调用 `viewModel.open(pdf)` 后断言：

```kotlin
var playbackRequestCount = 0
val playbackJob = backgroundScope.launch {
    viewModel.playbackRequests.collect {
        playbackRequestCount += 1
    }
}
val mediaLaunchDeferred = async { viewModel.mediaLaunches.first() }
runCurrent()
viewModel.open(pdf)
val launch = mediaLaunchDeferred.await()
assertEquals(MediaKind.PDF, launch.kind)
assertEquals(pdf.logicalUrl, launch.logicalUrl)
assertEquals(subUrl, launch.directoryLogicalUrl)
assertEquals(0, playbackRequestCount)
playbackJob.cancel()
```

在 `BrowserScreenTest` 加入 `pdfFilterShowsOnlyPdfAndHasNoPlaybackMenu`，点击 `browser_filter_pdf` 后只显示 `manual.pdf`，并断言不存在 `更多播放操作：manual.pdf`。

- [ ] **Step 2: 运行测试并确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.MediaClassifierTest' `
  --tests 'com.local.mediaviewer.browser.BrowserViewModelTest' `
  --tests 'com.local.mediaviewer.ui.browser.BrowserFormattersTest' `
  --tests 'com.local.mediaviewer.ui.icons.MediaIconsTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 编译因 `MediaKind.PDF`、`BrowserFilter.PDF`、`MediaIcons.Pdf` 尚不存在而失败。

- [ ] **Step 3: 实现最小分类和浏览器行为**

把 `PDF` 加入 `MediaKind`；分类器加入单值集合 `pdf = setOf("pdf")`。`BrowserViewModel.open()` 对 `IMAGE` 或 `PDF` 都发出 `MediaLaunchRequest`，其他可播放类型仍走播放请求。

`BrowserFilter.PDF.accepts()` 只接受 `entry.kind == MediaKind.PDF`。在筛选标签、图标和 testTag 映射中加入：

```kotlin
BrowserFilter.PDF -> "PDF"
BrowserFilter.PDF -> MediaIcons.Pdf
BrowserFilter.PDF -> "browser_filter_pdf"
```

`MediaFileRow` 为 PDF 返回 `MediaIcons.Pdf`、内容描述“PDF 文档”和 `MaterialTheme.colorScheme.error` 色调；`isPlayable` 明确不包含 PDF。由于新增 enum 会使防御性映射变为非穷尽，`NowPlayingBar` 补齐 PDF 图标、标签和色调分支，`PlaybackQueueSheet` 补齐“PDF”标签；这些分支只用于恢复数据异常时安全展示，不能让 PDF 进入队列。

创建居中矢量图标：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M6,2.5H14L18,6.5V21.5H6Z"
        android:strokeColor="#FF000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="1.8" />
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M14,2.5V6.5H18M8.5,14H15.5M8.5,17H13.5"
        android:strokeColor="#FF000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="1.8" />
</vector>
```

注册 `val Pdf = MediaIcon(R.drawable.ic_wp_pdf)`，并把图标清单数量断言从 `36` 更新为 `37`。

- [ ] **Step 4: 运行定向 JVM 和浏览器 Compose 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.MediaClassifierTest' `
  --tests 'com.local.mediaviewer.browser.BrowserViewModelTest' `
  --tests 'com.local.mediaviewer.ui.browser.BrowserFormattersTest' `
  --tests 'com.local.mediaviewer.ui.icons.MediaIconsTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 所列测试通过；PDF 行没有播放菜单。

- [ ] **Step 5: 提交分类与入口**

```powershell
git add app/src/main/java/com/local/mediaviewer/model/MediaKind.kt `
  app/src/main/java/com/local/mediaviewer/network/MediaClassifier.kt `
  app/src/main/java/com/local/mediaviewer/browser/BrowserViewModel.kt `
  app/src/main/java/com/local/mediaviewer/ui/browser/BrowserFormatters.kt `
  app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/browser/MediaFileRow.kt `
  app/src/main/java/com/local/mediaviewer/ui/icons/MediaIcons.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt `
  app/src/main/res/drawable/ic_wp_pdf.xml `
  app/src/test/java/com/local/mediaviewer/network/MediaClassifierTest.kt `
  app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt `
  app/src/test/java/com/local/mediaviewer/ui/browser/BrowserFormattersTest.kt `
  app/src/test/java/com/local/mediaviewer/ui/icons/MediaIconsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt
git commit -m "feat: classify and browse PDF files"
```

## Task 2：临时 PDF 下载与缓存清理

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/core/AppError.kt`
- Create: `app/src/main/java/com/local/mediaviewer/pdf/PdfFileClient.kt`
- Create: `app/src/main/java/com/local/mediaviewer/pdf/PdfTemporaryFileRepository.kt`
- Test: `app/src/test/java/com/local/mediaviewer/pdf/PdfFileClientTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/pdf/PdfTemporaryFileRepositoryTest.kt`

**Interfaces:**
- Consumes: `ServerSessionManager.state`、`refreshAfterRequestFailure()`、`SessionEndpoint.requestUrlFor()`、`AppResult<T>`。
- Produces: `PdfFileClient.download(requestUrl: String, destination: File): AppResult<Long>`；`PdfTemporaryFileRepository.acquire(logicalUrl: String): AppResult<PdfTemporaryFile>`；同步 `release(file)` 与挂起 `cleanupExpired(nowMs)`。

- [ ] **Step 1: 写入失败的下载和仓库测试**

创建以下测试场景：

```kotlin
@get:Rule
val temporaryFolder = TemporaryFolder()

private val initialEndpoint = endpoint("192.0.2.1")
private val refreshedEndpoint = endpoint("192.0.2.2")

@Test
fun `网络失败刷新一次并使用新端点重新下载`() = runTest {
    val session = PdfSession(initialEndpoint, refreshedEndpoint)
    val calls = mutableListOf<String>()
    val repository = DefaultPdfTemporaryFileRepository(
        cacheRoot = temporaryFolder.root,
        client = PdfFileClient { url, destination ->
            calls += url
            if (calls.size == 1) {
                AppResult.Failure(AppError.NetworkFailure("timeout"))
            } else {
                destination.writeBytes("%PDF-1.4".encodeToByteArray())
                AppResult.Success(destination.length())
            }
        },
        session = session,
    )

    val result = repository.acquire(LOGICAL_URL)

    assertTrue(result is AppResult.Success)
    assertEquals(listOf(FIRST_URL, SECOND_URL), calls)
    assertEquals(1, session.refreshCalls)
    assertFalse(temporaryFolder.root.walk().any { it.extension == "part" })
}
```

同一测试文件定义 `endpoint(ipv4)`，逻辑基址固定为 `http://media.example:8080`；定义 `PdfSession : ServerSessionManager`，以 `MutableStateFlow<ServerSessionState>` 暴露当前端点，`refreshAfterRequestFailure()` 把状态切为 refreshed endpoint 并递增 `refreshCalls`。`LOGICAL_URL`、`FIRST_URL` 和 `SECOND_URL` 分别固定为逻辑地址及两个 IPv4 对应请求地址。

同时覆盖：HTTP 404 不刷新、第二次网络失败不再刷新、取消/失败删除 `.part`、`release()` 删除完成文件、超过 24 小时文件被清理、有效新文件不清理、Content-Length 大于可用空间返回 `PdfCacheSpaceInsufficient`。

- [ ] **Step 2: 运行测试并确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.pdf.PdfFileClientTest' `
  --tests 'com.local.mediaviewer.pdf.PdfTemporaryFileRepositoryTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 新类型和接口不存在，测试编译失败。

- [ ] **Step 3: 实现错误类型、流式下载和临时仓库**

在 `AppError` 增加：

```kotlin
data object PdfCacheSpaceInsufficient : AppError {
    override val userMessage = "缓存空间不足，无法打开 PDF"
}

data class PdfCacheFailure(val detail: String) : AppError {
    override val userMessage = "PDF 临时文件写入失败：$detail"
}

data object InvalidPdfDocument : AppError {
    override val userMessage = "PDF 文件无效或已损坏"
}

data object EncryptedPdfDocument : AppError {
    override val userMessage = "当前版本暂不支持加密 PDF"
}

data class PdfPageRenderFailure(val pageNumber: Int) : AppError {
    override val userMessage = "第 $pageNumber 页渲染失败"
}
```

接口固定为：

```kotlin
fun interface PdfFileClient {
    suspend fun download(
        requestUrl: String,
        destination: File,
    ): AppResult<Long>
}

data class PdfTemporaryFile(
    val logicalUrl: String,
    val file: File,
    val byteCount: Long,
)

interface PdfTemporaryFileRepository {
    suspend fun acquire(logicalUrl: String): AppResult<PdfTemporaryFile>
    fun release(file: PdfTemporaryFile)
    suspend fun cleanupExpired(nowMs: Long = System.currentTimeMillis())
}
```

`DefaultPdfFileClient` 使用 OkHttp 同步调用包在 `withContext(dispatchers.io)` 中；校验 HTTP 状态和 Content-Length，保留 16 MiB 空间余量，按 32 KiB 缓冲流式写入。仓库使用逻辑 URL 的 SHA-256 作为安全文件名，在同一目录内从 `.part` 移动为 `.pdf`；只对 `AppError.NetworkFailure` 刷新一次端点。过期常量固定为 `24L * 60L * 60L * 1_000L`，清理范围只包含 PDF 缓存目录内的 `.part` 和 `.pdf` 文件。

- [ ] **Step 4: 运行定向测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.pdf.PdfFileClientTest' `
  --tests 'com.local.mediaviewer.pdf.PdfTemporaryFileRepositoryTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 全部通过，测试临时目录内没有遗留 `.part`。

- [ ] **Step 5: 提交临时下载层**

```powershell
git add app/src/main/java/com/local/mediaviewer/core/AppError.kt `
  app/src/main/java/com/local/mediaviewer/pdf/PdfFileClient.kt `
  app/src/main/java/com/local/mediaviewer/pdf/PdfTemporaryFileRepository.kt `
  app/src/test/java/com/local/mediaviewer/pdf/PdfFileClientTest.kt `
  app/src/test/java/com/local/mediaviewer/pdf/PdfTemporaryFileRepositoryTest.kt
git commit -m "feat: cache PDF files temporarily"
```

## Task 3：PdfRenderer 封装与页面位图缓存

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/pdf/PdfDocumentHandle.kt`
- Create: `app/src/main/java/com/local/mediaviewer/pdf/PdfPageBitmapCache.kt`
- Test: `app/src/test/java/com/local/mediaviewer/pdf/PdfPageBitmapCacheTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PdfRendererInstrumentedTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `AppError.InvalidPdfDocument`、`EncryptedPdfDocument` 和 `PdfPageRenderFailure`。
- Produces: `PdfPageSize`、`PdfDocumentHandle`、`PdfDocumentFactory`、`AndroidPdfDocumentFactory`、`PdfPageBitmapCache`；缓存逐出回调保证 UI 状态先移除位图再回收。

- [ ] **Step 1: 写入失败的缓存和真实 PdfRenderer 测试**

缓存测试固定键和逐出规则：

```kotlin
val first = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
val second = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
val third = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
val evicted = mutableListOf<Pair<Int, Bitmap>>()
val cache = PdfPageBitmapCache(
    maxBytes = first.byteCount + second.byteCount,
    onEvicted = { pageIndex, bitmap -> evicted += pageIndex to bitmap },
)
cache.put(0, first)
cache.put(1, second)
cache.put(2, third)
assertNull(cache.get(0))
assertSame(third, cache.get(2))
assertEquals(listOf(0 to first), evicted)
cache.clear()
assertEquals(0, cache.sizeBytes)
```

仪器测试使用 `android.graphics.pdf.PdfDocument` 在 `context.cacheDir` 生成两页 PDF，再通过 `AndroidPdfDocumentFactory.open()` 断言 `pageCount == 2`、页面尺寸为正、渲染位图为 ARGB 且宽度等于请求宽度；测试结束关闭 handle 并删除文件。

- [ ] **Step 2: 确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.pdf.PdfPageBitmapCacheTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 新工厂、handle 和缓存类型不存在，编译失败。

- [ ] **Step 3: 实现串行渲染接口**

接口固定为：

```kotlin
data class PdfPageSize(
    val pageIndex: Int,
    val widthPoints: Int,
    val heightPoints: Int,
)

interface PdfDocumentHandle : AutoCloseable {
    val pageCount: Int
    val pageSizes: List<PdfPageSize>
    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
    ): AppResult<Bitmap>
}

fun interface PdfDocumentFactory {
    suspend fun open(file: File): AppResult<PdfDocumentHandle>
}
```

`AndroidPdfDocumentFactory` 在 IO dispatcher 打开可随机访问文件；`SecurityException` 映射为 `EncryptedPdfDocument`，`IOException` 和非法页数映射为 `InvalidPdfDocument`。handle 内使用 `Mutex`，每次 `openPage(index).use { page -> ... }`；按页面宽高比创建 `ARGB_8888` 位图并用 `RENDER_MODE_FOR_DISPLAY` 渲染。`close()` 只执行一次并同时关闭 renderer 所拥有的文件描述符。

`PdfPageBitmapCache` 使用访问顺序 `LinkedHashMap<Int, Bitmap>`，页索引是唯一键，以 `bitmap.allocationByteCount` 计费。逐出或替换时先调用 `onEvicted(pageIndex, bitmap)`；ViewModel 在该回调中从 `PdfPageUiState` 移除仍指向同一对象的位图，然后再执行 `bitmap.recycle()`，避免 Compose 持有已回收位图。替换同一对象时不得重复回收。

类构造固定为：

```kotlin
class PdfPageBitmapCache(
    private val maxBytes: Int,
    private val onEvicted: (pageIndex: Int, bitmap: Bitmap) -> Unit,
)
```

`PdfPageBitmapCacheTest` 使用 `@RunWith(RobolectricTestRunner::class)` 和 `@Config(sdk = [29])` 创建真实 Bitmap。

- [ ] **Step 4: 运行缓存测试和模拟器渲染冒烟**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.pdf.PdfPageBitmapCacheTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PdfRendererInstrumentedTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 缓存逐出测试和两页 PDF 实际渲染均通过。

- [ ] **Step 5: 提交渲染层**

```powershell
git add app/src/main/java/com/local/mediaviewer/pdf/PdfDocumentHandle.kt `
  app/src/main/java/com/local/mediaviewer/pdf/PdfPageBitmapCache.kt `
  app/src/test/java/com/local/mediaviewer/pdf/PdfPageBitmapCacheTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PdfRendererInstrumentedTest.kt
git commit -m "feat: render PDF pages with PdfRenderer"
```

## Task 4：PDF 阅读 ViewModel 与页面加载策略

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/pdf/PdfReaderModels.kt`
- Create: `app/src/main/java/com/local/mediaviewer/pdf/PdfReaderViewModel.kt`
- Test: `app/src/test/java/com/local/mediaviewer/pdf/PdfReaderViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 的临时仓库和 Task 3 的文档工厂/handle。
- Produces: `StateFlow<PdfReaderUiState>`；`updateViewport(currentPageIndex, visiblePageIndices, viewportWidthPx, renderScale)`；`retryDocument()`；`retryPage(pageIndex)`。

- [ ] **Step 1: 写入失败的 ViewModel 状态测试**

使用测试文件内的 `FakePdfTemporaryFileRepository`、`FakePdfDocumentFactory` 和 `FakePdfDocumentHandle` 覆盖：下载到打开的顺序、页数/尺寸发布、可视页加前后一页预取、重复 viewport 不重复渲染、页面失败只标记单页、重试只重渲染单页、高分辨率失败后清缓存并以视口宽度降级一次、文档失败显示中文错误、`closeForTest()` 关闭 handle 并删除临时文件。假仓库记录 `acquiredLogicalUrls` 与 `releasedFiles`；假 handle 固定五页尺寸并记录 `(pageIndex, targetWidthPx)`，可按测试指定页或宽度返回失败。

核心断言：

```kotlin
viewModel.updateViewport(
    currentPageIndex = 4,
    visiblePageIndices = setOf(4, 5),
    viewportWidthPx = 1080,
    renderScale = 1f,
)
advanceUntilIdle()
assertEquals(setOf(3, 4, 5, 6), handle.renderedPages.toSet())
assertEquals(4, (viewModel.uiState.value as PdfReaderUiState.Content).currentPageIndex)
```

页码在模型中保持零基索引，只有 UI 文案转换为 `index + 1`。

- [ ] **Step 2: 运行测试并确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.pdf.PdfReaderViewModelTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: `PdfReaderUiState` 和 `PdfReaderViewModel` 不存在。

- [ ] **Step 3: 实现状态模型和最小 ViewModel**

状态模型固定为：

```kotlin
enum class PdfLoadPhase { DOWNLOADING, OPENING }

sealed interface PdfReaderUiState {
    data class Loading(
        val fileName: String,
        val phase: PdfLoadPhase,
    ) : PdfReaderUiState

    data class Content(
        val fileName: String,
        val pageSizes: List<PdfPageSize>,
        val pages: Map<Int, PdfPageUiState>,
        val currentPageIndex: Int,
    ) : PdfReaderUiState

    data class Error(
        val fileName: String,
        val message: String,
    ) : PdfReaderUiState
}

data class PdfPageUiState(
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val renderedWidthPx: Int = 0,
)
```

构造函数：

```kotlin
class PdfReaderViewModel(
    private val fileName: String,
    private val logicalUrl: String,
    private val files: PdfTemporaryFileRepository,
    private val documents: PdfDocumentFactory,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val bitmapCacheBytes: Int = defaultPdfBitmapCacheBytes(),
) : ViewModel()
```

`PdfReaderViewModelTest` 使用 `@RunWith(RobolectricTestRunner::class)`、`@Config(sdk = [29])` 和 `StandardTestDispatcher`，确保 Bitmap 与 ViewModel coroutine 都可控。`defaultPdfBitmapCacheBytes()` 固定为 `minOf(Runtime.getRuntime().maxMemory() / 8L, 48L * 1024L * 1024L).toInt()`；测试传入小预算验证逐出。

`updateViewport` 把可见集合扩为前后一页，按当前视口宽度和 `renderScale.coerceIn(1f, 2f)` 计算目标宽度；已有相同或更高宽度位图时跳过。高于视口宽度的渲染失败时清理远离视口的位图，并只以视口宽度降级重试一次；第二次失败才写入单页错误。文档级重试先释放旧资源再重新下载；页面级重试不下载文件。`onCleared()` 与测试专用 `closeForTest()` 走同一幂等释放函数。

- [ ] **Step 4: 运行 ViewModel 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.pdf.PdfReaderViewModelTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 所有状态、预取、重试和释放断言通过。

- [ ] **Step 5: 提交 ViewModel**

```powershell
git add app/src/main/java/com/local/mediaviewer/pdf/PdfReaderModels.kt `
  app/src/main/java/com/local/mediaviewer/pdf/PdfReaderViewModel.kt `
  app/src/test/java/com/local/mediaviewer/pdf/PdfReaderViewModelTest.kt
git commit -m "feat: manage PDF reader state"
```

## Task 5：纵向连续 PDF 阅读界面

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/pdf/PdfReaderPolicy.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/pdf/PdfTransform.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/pdf/PdfReaderScreen.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/pdf/PdfReaderPolicyTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/pdf/PdfTransformTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PdfReaderScreenTest.kt`

**Interfaces:**
- Consumes: Task 4 的 `PdfReaderUiState` 和回调。
- Produces: `PdfReaderScreen(...)`、`mostVisiblePdfPage(...)`、`PdfTransformReducer`。

- [ ] **Step 1: 写入失败的纯策略和 Compose 测试**

纯策略覆盖可见面积最大页和缩放锚点：

```kotlin
assertEquals(
    1,
    mostVisiblePdfPage(
        items = listOf(
            VisiblePdfPage(0, -500, 700),
            VisiblePdfPage(1, 200, 700),
        ),
        viewportStartPx = 0,
        viewportEndPx = 1_000,
    ),
)

val zoomed = PdfTransformReducer.gesture(
    current = PdfTransform(),
    zoomChange = 2f,
    panXPx = 0f,
    centroidXPx = 750f,
    viewportWidthPx = 1_000f,
)
assertEquals(-250f, zoomed.horizontalOffsetPx, 0.001f)
```

生产类型固定为：

```kotlin
data class VisiblePdfPage(
    val pageIndex: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

data class PdfTransform(
    val scale: Float = 1f,
    val horizontalOffsetPx: Float = 0f,
)
```

Compose 测试覆盖：加载中文文案、文档错误重试、三页纵向列表、顶部 `2 / 3`、单页错误只影响对应页、单击隐藏/显示工具栏、注入安全区后按钮不进入 cutout。

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.pdf.PdfReaderPolicyTest' `
  --tests 'com.local.mediaviewer.ui.pdf.PdfTransformTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: PDF UI 和策略类型不存在。

- [ ] **Step 3: 实现连续页面、工具栏与双指缩放**

`PdfReaderScreen` 签名：

```kotlin
@Composable
fun PdfReaderScreen(
    state: PdfReaderUiState,
    onViewportChanged: (Int, Set<Int>, Int, Float) -> Unit,
    onRetryDocument: () -> Unit,
    onRetryPage: (Int) -> Unit,
    onBack: () -> Unit,
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
)
```

Content 使用带稳定页索引 key 的 `LazyColumn`。页面宽高比来自 `PdfPageSize`；位图为空时显示固定比例占位，失败时显示“第 N 页渲染失败”和“重试”。`snapshotFlow` 根据可见面积最大页调用 `onViewportChanged`。

手势使用 `detectTransformGestures` 的 centroid、pan 和 zoom；水平方向使用 Task 5 reducer，纵向在缩放前记录 centroid 所在页及页内比例，布局更新后用 `listState.scrollBy()` 校正同一内容回到 centroid。`1×` 时偏移归零，范围限制为放大文档宽度的一半。单击页面只切换半透明顶部工具栏，不增加双击行为。

- [ ] **Step 4: 运行策略和 Compose UI 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.pdf.*' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PdfReaderScreenTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 加载、错误、连续三页、页码、缩放和安全区测试通过。

- [ ] **Step 5: 提交 PDF 界面**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/pdf/PdfReaderPolicy.kt `
  app/src/main/java/com/local/mediaviewer/ui/pdf/PdfTransform.kt `
  app/src/main/java/com/local/mediaviewer/ui/pdf/PdfReaderScreen.kt `
  app/src/test/java/com/local/mediaviewer/ui/pdf/PdfReaderPolicyTest.kt `
  app/src/test/java/com/local/mediaviewer/ui/pdf/PdfTransformTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PdfReaderScreenTest.kt
git commit -m "feat: add continuous PDF reader UI"
```

## Task 6：依赖注入、导航和启动清理

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/MediaViewerApplication.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/AppActivityRecreationTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5 的媒体请求、临时仓库、文档工厂、ViewModel 和 Screen。
- Produces: 类型安全 `PdfReaderRoute(rootId, logicalUrl, fileName)`，完整目录到 PDF 阅读器导航。

- [ ] **Step 1: 写入失败的完整导航测试**

在假目录加入 `manual.pdf`。点击后断言：

```kotlin
rule.onNodeWithText("manual.pdf").performClick()
rule.onNodeWithTag("pdf_reader_root").assertIsDisplayed()
rule.onNodeWithText("manual.pdf").assertIsDisplayed()
assertTrue(container.fakePlaybackController.sessionState.value.queue.items.isEmpty())
```

在 recreation 测试中打开 PDF、记录假下载次数、重建 Activity，断言仍在同一 PDF 页且下载次数保持 `1`。

- [ ] **Step 2: 编译仪器测试并确认 RED**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: `PdfReaderRoute` 和 AppContainer PDF 属性尚不存在。

- [ ] **Step 3: 接入 AppContainer 和导航**

在 `AppContainer` 增加：

```kotlin
val pdfTemporaryFileRepository: PdfTemporaryFileRepository
val pdfDocumentFactory: PdfDocumentFactory
```

`DefaultAppContainer` 使用 `File(appContext.cacheDir, "pdf")`、`DefaultPdfFileClient`、`DefaultPdfTemporaryFileRepository` 和 `AndroidPdfDocumentFactory`。两个 androidTest container 分别提供假实现或转发 delegate。

新增路由：

```kotlin
@Serializable
data class PdfReaderRoute(
    val rootId: String,
    val logicalUrl: String,
    val fileName: String,
)
```

浏览器 `mediaLaunches.collect` 按 `media.kind` 分流：IMAGE 维持 `ImageReaderRoute`，PDF 导航到 `PdfReaderRoute`，其他类型不处理。PDF destination 用 `logicalUrl` 作为 ViewModel key，创建 Task 4 ViewModel并传入 Task 5 Screen。

`MediaViewerApplication.onCreate()` 在现有 IO scope 中调用 `container.pdfTemporaryFileRepository.cleanupExpired()`；失败只记录并继续启动，不影响首页。

- [ ] **Step 4: 运行导航和配置变化测试**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.AppActivityRecreationTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: PDF 点击进入阅读器、不创建播放项；Activity 重建不重复下载。

- [ ] **Step 5: 提交导航集成**

```powershell
git add app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt `
  app/src/main/java/com/local/mediaviewer/app/AppContainer.kt `
  app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt `
  app/src/main/java/com/local/mediaviewer/MediaViewerApplication.kt `
  app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt `
  app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt `
  app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/AppActivityRecreationTest.kt
git commit -m "feat: navigate to PDF reader"
```

## Task 7：PDF 基础功能门禁

**Files:**
- Create: `docs/verification/2026-08-03-pdf-reader.md`

**Interfaces:**
- Consumes: Tasks 1–6 完整 PDF 功能。
- Produces: 定向 JVM、模拟器和 Debug/Release 编译结果的简短中文记录。

- [ ] **Step 1: 运行 PDF 相关 JVM 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.pdf.*' `
  --tests 'com.local.mediaviewer.ui.pdf.*' `
  --tests 'com.local.mediaviewer.network.MediaClassifierTest' `
  --tests 'com.local.mediaviewer.browser.BrowserViewModelTest' `
  --tests 'com.local.mediaviewer.ui.browser.BrowserFormattersTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 全部通过。

- [ ] **Step 2: 运行必要模拟器类**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PdfRendererInstrumentedTest,com.local.mediaviewer.PdfReaderScreenTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.AppActivityRecreationTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 所列类通过。若没有在线模拟器，记录为 `NOT RUN（无设备）`，不得写成 PASS。

- [ ] **Step 3: 运行基础构建门禁**

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: `BUILD SUCCESSFUL`；Release 继续只包含 `arm64-v8a`，本任务不做 ARM64 真机声明。

- [ ] **Step 4: 写入简短验证记录**

记录每条实际命令、退出码、通过/失败/未运行状态，以及 APK 路径 `app/build/outputs/apk/release/app-release.apk`。不要增加模糊测试、安全审计、全设备矩阵或无关功能结果。

- [ ] **Step 5: 提交验证记录**

```powershell
git add docs/verification/2026-08-03-pdf-reader.md
git commit -m "docs: record PDF reader verification"
```
