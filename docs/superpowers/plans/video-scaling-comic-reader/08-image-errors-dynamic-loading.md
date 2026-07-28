# TODO 08 Image Error Recovery and Dynamic Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为条漫中的单张图片提供隔离错误、人工重试和去重端点刷新，并证明大目录不会首屏请求全部图片。

**Architecture:** Coil 错误先归类为网络或解码失败；ViewModel 只接收领域失败类型。首次网络失败启动唯一端点刷新任务，成功后统一重写请求 URL。每张图片拥有轻量失败状态和独立请求代数。

**Tech Stack:** Coil ErrorResult、Coroutines、StateFlow、MockWebServer 5.3.0、Compose Instrumentation。

## Global Constraints

- 单张失败不得替换整页内容。
- 解码失败不得刷新 DNS。
- 同一阅读页面自动端点刷新最多一次。
- 并发失败不得产生多个刷新任务。
- 人工重试只递增目标图片请求代数。
- 50 张图片首屏不得请求全部图片。
- 不记录真实媒体名称或响应正文。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/image/ImageLoadFailure.kt`
- Create: `app/src/test/java/com/local/mediaviewer/image/ImageLoadFailureTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/image/ImageReaderViewModel.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/image/ImageReaderViewModelTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/MediaFixtureServer.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/image/MediaImageLoaderFactoryTest.kt`

## Interfaces

- Produces:

```kotlin
enum class ImageLoadFailureKind {
    NETWORK,
    DECODE,
}

data class ImageItemFailure(
    val message: String,
    val kind: ImageLoadFailureKind,
)

fun classifyImageLoadFailure(
    throwable: Throwable,
): ImageLoadFailureKind
```

- Extended content:

```kotlin
data class Content(
    // Existing fields remain.
    val itemFailures:
        Map<String, ImageItemFailure> = emptyMap(),
    val itemRequestGenerations:
        Map<String, Int> = emptyMap(),
)
```

- ViewModel actions:

```kotlin
fun onImageLoadError(
    logicalUrl: String,
    kind: ImageLoadFailureKind,
)

fun onImageLoadSuccess(logicalUrl: String)

fun retryImage(logicalUrl: String)
```

## Steps

- [ ] **Step 1: Write failing failure-classifier tests**

```kotlin
@Test
fun `IO 异常归类网络而解码异常归类解码`() {
    assertEquals(
        ImageLoadFailureKind.NETWORK,
        classifyImageLoadFailure(IOException("timeout")),
    )
    assertEquals(
        ImageLoadFailureKind.NETWORK,
        classifyImageLoadFailure(
            IllegalStateException(
                "wrapper",
                SocketTimeoutException(),
            ),
        ),
    )
    assertEquals(
        ImageLoadFailureKind.DECODE,
        classifyImageLoadFailure(
            IllegalArgumentException("bad bitmap"),
        ),
    )
}
```

Traverse the cause chain with an identity set or bounded loop so malformed
cyclic causes cannot loop forever.

- [ ] **Step 2: Run classifier tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ImageLoadFailureTest'
```

Expected: compilation fails for missing failure types.

- [ ] **Step 3: Implement failure domain**

Classify `IOException` anywhere in the cause chain as `NETWORK`; all other
throwables are `DECODE`. Fixed Chinese messages:

```kotlin
fun ImageLoadFailureKind.userMessage(): String =
    when (this) {
        ImageLoadFailureKind.NETWORK -> "图片网络加载失败"
        ImageLoadFailureKind.DECODE -> "图片解码失败"
    }
```

- [ ] **Step 4: Write failing ViewModel refresh tests**

Add a controllable session fake and:

```kotlin
@Test
fun `并发网络失败只刷新一次并重写全部请求 URL`() =
    runTest(dispatcher) {
        val session = ControlledImageSession(
            refreshedEndpoint,
        )
        val viewModel = populatedViewModel(session = session)
        advanceUntilIdle()

        viewModel.onImageLoadError(
            logicalA,
            ImageLoadFailureKind.NETWORK,
        )
        viewModel.onImageLoadError(
            logicalB,
            ImageLoadFailureKind.NETWORK,
        )
        advanceUntilIdle()

        val content =
            viewModel.uiState.value as ImageReaderUiState.Content
        assertEquals(1, session.refreshCalls)
        assertTrue(
            content.images.all {
                it.requestUrl.startsWith(
                    refreshedEndpoint.requestBaseUrl,
                )
            },
        )
        assertEquals(1, content.requestGeneration)
        assertTrue(content.itemFailures.isEmpty())
    }
```

Add tests:

```text
解码失败只记录单项且不刷新
第二次网络失败不自动刷新
人工重试只递增目标项代数
加载成功清除目标项失败
刷新失败保留单项错误并结束 refreshing
```

- [ ] **Step 5: Implement refresh deduplication**

Add:

```kotlin
private var refreshJob: Job? = null
private var automaticEndpointRefreshUsed = false
```

`onImageLoadError()` first records the item failure. For `NETWORK`, start
refresh only when both guards allow it:

```kotlin
if (
    automaticEndpointRefreshUsed ||
    refreshJob?.isActive == true
) {
    return
}
automaticEndpointRefreshUsed = true
refreshJob = viewModelScope.launch {
    setRefreshing(true)
    when (
        val result =
            session.refreshAfterRequestFailure()
    ) {
        is AppResult.Success -> remapRequests(result.value)
        is AppResult.Failure ->
            retainRefreshFailure(result.error.userMessage)
    }
}
```

`remapRequests()` copies every `ImageReaderItem.requestUrl` from
`endpoint.requestUrlFor(item.logicalUrl)`, increments global
`requestGeneration`, clears network failures and sets refreshing false.

`retryImage()`:

```kotlin
itemRequestGenerations =
    content.itemRequestGenerations +
        (
            logicalUrl to
                (
                    content.itemRequestGenerations[
                        logicalUrl
                    ] ?: 0
                ) + 1
        )
```

Clear only that item's failure.

- [ ] **Step 6: Run ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ImageReaderViewModelTest' `
  --tests 'com.local.mediaviewer.image.ImageLoadFailureTest'
```

Expected: all pass.

- [ ] **Step 7: Add per-item UI errors**

For each request calculate:

```kotlin
val itemGeneration =
    content.itemRequestGenerations[
        item.logicalUrl
    ] ?: 0
val effectiveGeneration =
    content.requestGeneration * 1_000_000 +
        itemGeneration
```

On Coil error:

```kotlin
LaunchedEffect(result) {
    onImageLoadError(
        item.logicalUrl,
        classifyImageLoadFailure(result.throwable),
    )
}
```

When `itemFailures` contains the URL, render a black/dark-gray block with:

- file name;
- failure message;
- `重试此图` button tagged
  `retry_image:<logicalUrl hash>` or a stable index-independent encoded tag.

The block uses a minimum height such as `160.dp` but does not claim the missing
image's original aspect ratio. Other items remain visible.

On successful composition call `onImageLoadSuccess` once with
`LaunchedEffect(item.logicalUrl, effectiveGeneration)`.

- [ ] **Step 8: Extend fixture server for synthetic image collections**

Add an optional constructor parameter:

```kotlin
class MediaFixtureServer(
    private val fixtures: MediaFixtures,
    imageCount: Int = 1,
)
```

Build deterministic names:

```kotlin
private val imageFiles = (1..imageCount).associate {
    index ->
    "page-${index.toString().padStart(3, '0')}.png" to
        fixtures.png
}
```

Include these names in Caddy JSON and serve the same generated PNG bytes for
each unique path. Add request counters:

```kotlin
fun mediaRequestCount(): Int
fun requestedMediaPaths(): Set<String>
```

Only generated fixture names are ever returned.

- [ ] **Step 9: Write dynamic-loading instrumentation test**

Create 50 `ImageReaderItem`s backed by the fixture server and compose
`ComicReader`.

After first idle:

```kotlin
assertTrue(server.mediaRequestCount() in 1 until 50)
assertTrue(server.requestedMediaPaths().size < 50)
```

Scroll:

```kotlin
rule.onNodeWithTag("comic_reader")
    .performScrollToIndex(49)
rule.waitUntil(10_000) {
    server.requestedMediaPaths()
        .contains("/pik/page-050.png")
}
assertTrue(server.requestedMediaPaths().size < 50)
```

This proves requests follow composition/scroll rather than eager iteration.
Do not assert an exact initial count because Compose prefetch may vary.

- [ ] **Step 10: Verify memory and request policies**

Extend factory tests to assert:

- disk cache remains `null`;
- disk policy remains disabled;
- request size never exceeds `ImageDecodePolicy`;
- memory key changes with request generation;
- request data remains HTTP URL;
- no new OkHttp disk cache is configured.

Do not use process RSS as a deterministic test. The enforceable bounds are
lazy request count, per-request size, absence of ViewModel bitmaps and cache
configuration.

- [ ] **Step 11: Run focused Android tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest'
```

Expected: inline error tests and 50-image dynamic loading pass.

- [ ] **Step 12: Run all image regressions and commit**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.*'
.\gradlew.bat lintDebug assembleDebug
git diff --check
git status --short
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: recover image items and bound lazy loading"
```

Confirm no bitmap-like type appears in the ViewModel:

```powershell
rg -n "Bitmap|Drawable|Painter" `
  app/src/main/java/com/local/mediaviewer/image/ImageReaderViewModel.kt
```

Expected: no output.
