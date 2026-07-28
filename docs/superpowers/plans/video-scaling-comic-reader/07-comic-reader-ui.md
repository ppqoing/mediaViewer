# TODO 07 Single Image and Comic Reader UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用可切换的单图与条漫组件替换旧单图页面，实现初始锚点、六种排序菜单、惰性列表和整流统一缩放。

**Architecture:** `ImageReaderScreen` 只分派状态和工具栏；`SingleImageViewer` 保留原有局部缩放；`ComicReader` 使用共享 `ComicTransform` 改变每个条目的真实测量宽高，并用 `LazyColumn` 保持正确滚动范围。

**Tech Stack:** Compose LazyColumn、pointer input、horizontal draggable、Coil Compose 3.5.0、Navigation Compose、Compose UI Test。

## Global Constraints

- 条漫默认 `1×` 等比铺满视口宽。
- 不能只对 LazyColumn 外层应用 `graphicsLayer` 缩放。
- 两指手势统一缩放；单指纵向滚动；放大后单指横向平移。
- 列表 key 为逻辑 URL。
- 只为组合中的条目创建 Coil 请求。
- 单图保留原有双指缩放、拖动和双击复位。
- 临时模式和排序不写入 DataStore。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/ComicGestureModifier.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/image/MediaImageLoaderFactory.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt`
- Delete: `app/src/main/java/com/local/mediaviewer/ui/image/ImageViewerScreen.kt`
- Delete: `app/src/main/java/com/local/mediaviewer/image/ImageViewerViewModel.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Delete: `app/src/androidTest/java/com/local/mediaviewer/ImageViewerScreenTest.kt`
- Delete: `app/src/test/java/com/local/mediaviewer/image/ImageViewerViewModelTest.kt`

## Interfaces

- Screen:

```kotlin
@Composable
fun ImageReaderScreen(
    state: ImageReaderUiState,
    imageLoader: ImageLoader,
    onModeChanged: (ImageReaderMode) -> Unit,
    onSortChanged: (ImageSortOrder) -> Unit,
    onAnchorChanged: (String) -> Unit,
    onRetryDirectory: () -> Unit,
    onBack: () -> Unit,
)
```

- Comic:

```kotlin
@Composable
fun ComicReader(
    images: List<ImageReaderItem>,
    anchorLogicalUrl: String,
    sortOrder: ImageSortOrder,
    imageLoader: ImageLoader,
    requestGeneration: Int,
    onAnchorChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

- Single:

```kotlin
@Composable
fun SingleImageViewer(
    item: ImageReaderItem,
    imageLoader: ImageLoader,
    requestGeneration: Int,
    modifier: Modifier = Modifier,
)
```

- Request factory:

```kotlin
fun MediaImageLoaderFactory.createRequest(
    context: Context,
    url: String,
    decodeSize: ImageDecodeSize,
    requestGeneration: Int,
): ImageRequest
```

The existing two-argument overload may delegate to the new overload for
backward-compatible tests until old screen deletion is complete.

## Steps

- [ ] **Step 1: Write failing toolbar Compose tests**

Create `ImageReaderScreenTest` with a `Content` state containing three items.
Assert:

```kotlin
rule.onNodeWithTag("reader_mode_toggle").performClick()
rule.runOnIdle {
    assertEquals(ImageReaderMode.SINGLE, selectedMode)
}

rule.onNodeWithTag("image_sort_menu").performClick()
rule.onNodeWithText("文件名升序").assertIsDisplayed()
rule.onNodeWithText("文件大小降序").performClick()
rule.runOnIdle {
    assertEquals(ImageSortOrder.SIZE_DESC, selectedSort)
}
```

Add loading, empty and directory error assertions with Chinese text and retry.

- [ ] **Step 2: Run the new UI test and verify failure**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest'
```

Expected: instrumentation compilation fails because the new screen does not
exist.

- [ ] **Step 3: Implement toolbar labels and menus**

Use fixed labels:

```kotlin
fun imageSortLabel(order: ImageSortOrder): String =
    when (order) {
        ImageSortOrder.NAME_ASC -> "文件名升序"
        ImageSortOrder.NAME_DESC -> "文件名降序"
        ImageSortOrder.MODIFIED_ASC -> "修改时间升序"
        ImageSortOrder.MODIFIED_DESC -> "修改时间降序"
        ImageSortOrder.SIZE_ASC -> "文件大小升序"
        ImageSortOrder.SIZE_DESC -> "文件大小降序"
    }
```

`ImageReaderToolbar` contains:

- back button;
- current anchor file name as title;
- `reader_mode_toggle` icon with content descriptions “切换到单图” or
  “切换到条漫”;
- `image_sort_menu` and six `DropdownMenuItem` entries.

- [ ] **Step 4: Implement screen state dispatch**

`ImageReaderScreen` uses black background. Dispatch:

```kotlin
when (state) {
    ImageReaderUiState.Loading -> CircularProgressIndicator()
    ImageReaderUiState.Empty -> Text("此目录没有图片")
    is ImageReaderUiState.Error -> AppErrorPanel(
        message = state.message,
        onRetry = onRetryDirectory,
    )
    is ImageReaderUiState.Content -> {
        val current = state.images.first {
            it.logicalUrl == state.anchorLogicalUrl
        }
        if (state.mode == ImageReaderMode.COMIC) {
            ComicReader(...)
        } else {
            SingleImageViewer(item = current, ...)
        }
    }
}
```

Use `firstOrNull()` with the first list item fallback instead of a throwing
`first()` in production.

- [ ] **Step 5: Write failing request-size tests**

Extend `MediaImageLoaderFactoryTest`:

```kotlin
val request = MediaImageLoaderFactory.createRequest(
    context = context,
    url = "http://192.0.2.1/pik/a.png",
    decodeSize = ImageDecodeSize(1080, 4096),
    requestGeneration = 3,
)
assertEquals(
    Size(1080, 4096),
    request.sizeResolver.size(),
)
assertEquals(Scale.FIT, request.scale)
assertEquals(Precision.INEXACT, request.precision)
assertTrue(
    requireNotNull(request.memoryCacheKey)
        .key.contains("generation=3"),
)
```

If `Size` equality requires `Dimension.Pixels`, construct the exact Coil
`Size` expected by the 3.5.0 API.

- [ ] **Step 6: Implement bounded Coil requests**

Build:

```kotlin
ImageRequest.Builder(context.applicationContext)
    .data(url)
    .size(decodeSize.widthPx, decodeSize.heightPx)
    .scale(Scale.FIT)
    .precision(Precision.INEXACT)
    .memoryCacheKey(
        "$url|${decodeSize.widthPx}x" +
            "${decodeSize.heightPx}|" +
            "generation=$requestGeneration",
    )
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.DISABLED)
    .networkCachePolicy(CachePolicy.ENABLED)
    .build()
```

Do not change the ImageLoader's 20% cache and disabled disk cache.

- [ ] **Step 7: Implement single-image component**

Move the successful image body from the old screen into
`SingleImageViewer`. Keep:

- `ContentScale.Fit`;
- `ZoomTransform`;
- `ZoomReducer.gesture`;
- double-tap reset;
- `media_image` test tag;
- clipping and black background.

Its request uses the bounded decode policy based on current viewport and
visual zoom no greater than 2× for decode.

- [ ] **Step 8: Implement two-finger-only transform modifier**

`ComicGestureModifier.kt` uses:

```kotlin
fun Modifier.comicTransformGestures(
    onGesture: (
        zoomChange: Float,
        panXPx: Float,
    ) -> Unit,
): Modifier = pointerInput(onGesture) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressed =
                event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                onGesture(
                    event.calculateZoom(),
                    event.calculatePan().x,
                )
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
```

Single-pointer vertical events remain unconsumed for `LazyColumn`.

- [ ] **Step 9: Implement ComicReader measured scaling**

Use `BoxWithConstraints` to obtain finite viewport pixel dimensions and a
`LazyListState`.

Remember transform with a custom saver:

```kotlin
var transform by rememberSaveable(
    stateSaver = listSaver(
        save = {
            listOf(it.scale, it.horizontalOffsetPx)
        },
        restore = {
            ComicTransform(it[0], it[1])
        },
    ),
) {
    mutableStateOf(ComicTransform())
}
```

Apply:

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    items(
        items = images,
        key = ImageReaderItem::logicalUrl,
        contentType = { "image" },
    ) { item ->
        ComicImage(
            item = item,
            modifier = Modifier
                .requiredWidth(
                    with(density) {
                        (
                            viewportWidthPx *
                                transform.scale
                        ).toDp()
                    },
                )
                .offset {
                    IntOffset(
                        transform.horizontalOffsetPx
                            .roundToInt(),
                        0,
                    )
                },
        )
    }
}
```

Each item uses `fillMaxWidth()`, `wrapContentHeight()` and
`ContentScale.FillWidth`, so height follows intrinsic aspect ratio. The actual
measured item dimensions grow with scale; do not scale the whole LazyColumn
using `graphicsLayer`.

Add a horizontal `draggable` enabled only above `1×`; it calls
`ComicTransformReducer.gesture(zoomChange = 1f, panXPx = delta, ...)`.

- [ ] **Step 10: Implement initial/sort/mode anchoring**

Compute index by logical URL. Use:

```kotlin
LaunchedEffect(images, sortOrder) {
    listState.scrollToItem(anchorIndex)
}
```

`images` changes when sorting; `sortOrder` makes intent explicit. Visible
anchor updates do not change either key, so this effect cannot fight normal
scrolling.

Observe current visible item:

```kotlin
LaunchedEffect(listState, images) {
    snapshotFlow {
        mostVisibleLogicalUrl(
            listState.layoutInfo,
        )
    }
        .filterNotNull()
        .distinctUntilChanged()
        .collect(onAnchorChanged)
}
```

Implement `mostVisibleLogicalUrl()` as a focused internal pure helper and test
items clipped at both viewport edges.

- [ ] **Step 11: Wire the new route and remove old implementation**

In `MediaViewerApp`, navigate images to `ImageReaderRoute` using launch
context. Add a `composable<ImageReaderRoute>` that creates
`ImageReaderViewModel` with:

```kotlin
directoryLogicalUrl = route.directoryLogicalUrl
selectedLogicalUrl = route.selectedLogicalUrl
contentRepository =
    container.directoryContentRepository
preferences =
    container.readerPreferencesRepository
```

Pass ViewModel callbacks to `ImageReaderScreen`.

Delete `ImageRoute`, old `ImageViewerViewModel`, old `ImageViewerScreen` and
their superseded tests in the same commit so there is one image architecture.

- [ ] **Step 12: Update navigation fakes and tests**

`FakeAppContainer.directoryContentRepository` returns a deterministic nested
directory containing at least three images. Update
`MediaViewerNavigationTest.homeOpensNestedImage` to assert:

```kotlin
rule.onNodeWithTag("comic_reader").assertExists()
rule.onNodeWithText("样例.png").assertIsDisplayed()
```

Add a variant with preference `SINGLE` asserting `media_image`.

- [ ] **Step 13: Run focused UI and regression tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.*'
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.MediaViewerNavigationTest'
```

Expected: all pass; no old `ImageViewer` class references remain.

- [ ] **Step 14: Run Lint and commit**

Run:

```powershell
rg -n "ImageViewerScreen|ImageViewerViewModel|ImageRoute" app/src
.\gradlew.bat lintDebug assembleDebug
git diff --check
git status --short
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add zoomable comic reader"
```

Expected `rg`: no matches except historical documentation outside `app/src`.
