# 图片中点缩放与连续阅读进度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让单图和连续图片阅读均以双指中点缩放，并为连续阅读增加按文件夹全部图片数量计数的可拖动进度条和同步自动隐藏行为。

**Architecture:** 纯 Kotlin reducer 接收 centroid、pan、zoom 和视口几何，计算可测试的单图二维偏移和连续阅读横向偏移；连续阅读在缩放前后保存页内锚点并校正 `LazyListState`。进度条通过一次性 `ComicJumpCommand` 驱动 `scrollToItem`，避免把 ViewModel 锚点回写变成滚动循环；阅读器控制区使用独立状态 reducer，并复用已有 `VideoControlsAutoHide` 配置值。

**Tech Stack:** Kotlin 2.3、Jetpack Compose Foundation、Coil 3、Coroutines、JUnit 4、Compose UI Test。

## Global Constraints

- 单图和连续阅读都必须使用两指中点作为缩放锚点。
- 缩放范围保持 `1×–5×`；恢复 `1×` 时清除偏移。
- 单图左右滑动切图、单击功能区、双击复位行为必须保留。
- 连续阅读的普通单指纵向滚动必须保留，双指缩放不得重新请求已经显示的图片。
- 连续阅读进度最小值为 `1`，最大值为当前文件夹排序后 `state.images.size`；静态图和 GIF 均计入，其他媒体和子目录不计入。
- 进度条只在 `ImageReaderMode.COMIC` 显示；拖动松手后直接跳到目标图片，不逐项动画滚动。
- 进度条与上下功能区同步显示和隐藏，复用已有 `3 / 5 / 10 / 15 秒 / 不隐藏`配置。
- 正在滚动、缩放或拖动时不自动隐藏，交互结束后重新计时。
- 控件使用半透明背景并遵循 `WindowInsets.safeDrawing`。
- 只做个人工具所需的定向单元测试、必要 Compose/模拟器冒烟和 Release 构建；不得扩大为无关功能或安全审查。
- 保留当前工作树中的既有改动，每次提交只暂存本任务列出的文件。

---

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/local/mediaviewer/image/ZoomState.kt` | 单图 centroid 缩放、平移和边界钳制 |
| `app/src/main/java/com/local/mediaviewer/image/ComicTransform.kt` | 连续阅读 centroid 横向缩放和纵向锚点纯函数 |
| `app/src/main/java/com/local/mediaviewer/image/ReaderControlsState.kt` | 阅读控制区显示、交互和自动隐藏决策 |
| `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt` | 采集双指中点及图片固有尺寸，应用单图变换 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ComicGestureModifier.kt` | 把 centroid、pan 和 zoom 传给连续阅读器 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt` | 纵向锚点校正、直接跳转和当前图片更新 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ComicProgress.kt` | 进度数值映射和一次性跳转命令 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ReaderInteractionModifier.kt` | 不消费事件地跟踪按下/抬起，暂停自动隐藏 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt` | 连续阅读进度条和底部布局 |
| `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt` | 进度预览、跳转命令、功能区自动隐藏 |
| `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt` | 收集已有自动隐藏配置并传给图片阅读器 |

## Task 1：纯函数中点缩放与连续阅读锚点

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/image/ZoomState.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/image/ComicTransform.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/ZoomStateTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/ComicTransformTest.kt`

**Interfaces:**
- Consumes: 现有 `ZoomTransform`、`ComicTransform`。
- Produces: centroid-aware `ZoomReducer.gesture(...)`、`ZoomReducer.clamp(...)`、centroid-aware `ComicTransformReducer.gesture(...)`、`ComicViewportAnchor` 捕获和滚动校正函数。

- [ ] **Step 1: 写入失败的偏心缩放测试**

单图测试使用离开视口中心的 centroid：

```kotlin
val zoomed = ZoomReducer.gesture(
    current = ZoomTransform(),
    zoomChange = 2f,
    panChange = Offset.Zero,
    centroid = Offset(750f, 250f),
    viewportSize = Size(1_000f, 1_000f),
    fittedContentSize = Size(1_000f, 1_000f),
)
assertEquals(2f, zoomed.scale, 0.001f)
assertEquals(-250f, zoomed.offset.x, 0.001f)
assertEquals(250f, zoomed.offset.y, 0.001f)
```

连续阅读横向测试：

```kotlin
val zoomed = ComicTransformReducer.gesture(
    current = ComicTransform(),
    zoomChange = 2f,
    panXPx = 0f,
    centroidXPx = 750f,
    viewportWidthPx = 1_000f,
)
assertEquals(-250f, zoomed.horizontalOffsetPx, 0.001f)
```

纵向锚点测试：

```kotlin
val anchor = captureComicViewportAnchor(
    items = listOf(ComicVisibleItem(index = 4, offsetPx = 100, sizePx = 400)),
    centroidYPx = 250f,
)
assertEquals(0.375f, anchor!!.itemFraction, 0.001f)
assertEquals(
    100f,
    comicScrollCorrectionPx(
        anchor = anchor,
        updatedItem = ComicVisibleItem(index = 4, offsetPx = 50, sizePx = 800),
    ),
    0.001f,
)
```

同时保留 `1×` 归零和 `5×` 上限测试，并增加 fitted content 较窄时 X/Y 边界钳制测试。

- [ ] **Step 2: 运行测试并确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ZoomStateTest' `
  --tests 'com.local.mediaviewer.image.ComicTransformTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 新参数和锚点类型不存在，测试编译失败。

- [ ] **Step 3: 实现精确变换公式**

单图 reducer 固定签名：

```kotlin
fun gesture(
    current: ZoomTransform,
    zoomChange: Float,
    panChange: Offset,
    centroid: Offset,
    viewportSize: Size,
    fittedContentSize: Size,
): ZoomTransform
```

设 `ratio = newScale / oldScale`、`relative = centroid - viewportCenter`，新偏移公式为：

```kotlin
val anchoredOffset =
    current.offset * ratio +
        relative * (1f - ratio) +
        panChange
```

最大偏移分别为：

```kotlin
maxX = maxOf(0f, (fittedContentSize.width * newScale - viewportSize.width) / 2f)
maxY = maxOf(0f, (fittedContentSize.height * newScale - viewportSize.height) / 2f)
```

`ZoomReducer.clamp(current, viewportSize, fittedContentSize)` 复用相同边界；图片固有尺寸首次到达或视口变化时调用它，避免尺寸变化后保留越界偏移。

连续阅读横向使用同一锚点公式；纵向定义：

```kotlin
data class ComicVisibleItem(val index: Int, val offsetPx: Int, val sizePx: Int)
data class ComicViewportAnchor(
    val itemIndex: Int,
    val itemFraction: Float,
    val centroidYPx: Float,
)
```

`captureComicViewportAnchor` 优先选择 centroid 所在项，落在间隙时选择中心距离最近的可见项；`comicScrollCorrectionPx` 返回“更新后锚点屏幕 Y - 原 centroid Y”，供 `listState.scrollBy()` 消除跳位。

- [ ] **Step 4: 运行纯函数测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ZoomStateTest' `
  --tests 'com.local.mediaviewer.image.ComicTransformTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 中点、边界、上限、归零和纵向校正测试全部通过。

- [ ] **Step 5: 提交几何计算**

```powershell
git add app/src/main/java/com/local/mediaviewer/image/ZoomState.kt `
  app/src/main/java/com/local/mediaviewer/image/ComicTransform.kt `
  app/src/test/java/com/local/mediaviewer/image/ZoomStateTest.kt `
  app/src/test/java/com/local/mediaviewer/image/ComicTransformTest.kt
git commit -m "fix: anchor image zoom at finger centroid"
```

## Task 2：把中点手势接入单图和连续阅读

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ComicGestureModifier.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImagePager.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt`

**Interfaces:**
- Consumes: Task 1 的 reducer 和纵向锚点函数。
- Produces: `comicTransformGestures(onGesture: suspend (centroid, zoom, pan) -> Unit)`；单图/连续阅读可观测的缩放语义值。

- [ ] **Step 1: 写入失败的偏心手势和不重载测试**

在 `ImageReaderScreenTest` 增加单图与连续阅读偏心缩放：两根手指围绕视口右上区域张开，读取测试语义并断言 X 偏移为负、Y 偏移为正，证明锚点不是屏幕中心。

新增语义键：

```kotlin
val SingleImageScaleSemanticsKey = SemanticsPropertyKey<Float>("SingleImageScale")
val SingleImageOffsetXSemanticsKey = SemanticsPropertyKey<Float>("SingleImageOffsetX")
val SingleImageOffsetYSemanticsKey = SemanticsPropertyKey<Float>("SingleImageOffsetY")
```

连续阅读测试记录缩放前 centroid 所在图片的屏幕 Y，缩放后断言误差不超过 `3 px`。

在 `ComicReaderDynamicLoadingTest.zoomingLoadedImageKeepsTheExistingRequest` 保留现有请求次数断言，并把手势 centroid 改为非中心位置，确保修复不触发新网络请求。

- [ ] **Step 2: 编译并确认 RED**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 新语义键和新手势签名不存在，编译失败。

- [ ] **Step 3: 接入单图 centroid 与固有尺寸**

`SingleImageViewer` 移除 `rememberTransformableState/transformable`，改用独立 `pointerInput` 的 `detectTransformGestures`，直接使用回调提供的 `centroid`、`pan` 和 `zoom`。

成功加载后从 `state.result.image.width/height` 保存固有尺寸，按 `ContentScale.Fit` 计算 `fittedContentSize`；加载完成前用视口尺寸作为临时 fitted size。固有尺寸或视口变化后调用 `ZoomReducer.clamp`。按钮缩放把 `viewportCenter` 作为 centroid，保持按钮仍以画面中心放大。

在 `media_image` modifier 写入 scale 和 X/Y offset 测试语义。保留另一条 `pointerInput` 的单击/双击处理、Coil request key、GIF 解码限制和 `HorizontalPager.userScrollEnabled` 逻辑。

- [ ] **Step 4: 接入连续阅读 centroid 和纵向校正**

`comicTransformGestures` 把签名改为：

```kotlin
onGesture: suspend (
    centroid: Offset,
    zoomChange: Float,
    panChange: Offset,
) -> Unit
```

每次双指事件使用 `calculateCentroid()`、`calculateZoom()` 和 `calculatePan()`。`ComicReader` 在更新 scale 前从 `listState.layoutInfo.visibleItemsInfo` 捕获纵向锚点；更新 transform 后调用 `withFrameNanos {}` 等待布局，再取得同一项的新几何并执行：

```kotlin
listState.scrollBy(comicScrollCorrectionPx(anchor, updatedItem))
```

横向单指拖动仍由现有 `draggable` 处理，并把 `viewportWidthPx / 2f` 作为 ratio 为 1 时的 centroid 参数；纵向单指事件仍由 `LazyColumn` 处理。不得把 scale 放入 Coil request key；因此缩放只改变布局宽度，不重新请求已显示图片。

- [ ] **Step 5: 运行图片阅读 Compose 和动态加载测试**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 偏心缩放方向、纵向锚点、左右切图、双击复位和不重复网络请求全部通过。

- [ ] **Step 6: 提交手势接入**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ComicGestureModifier.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/SingleImagePager.kt `
  app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt
git commit -m "fix: preserve image content under pinch centroid"
```

## Task 3：连续阅读进度条与直接跳转

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/ComicProgress.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/image/ComicProgressTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/image/ComicReaderPolicyTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt`

**Interfaces:**
- Consumes: `state.images`、现有 most-visible 图片锚点。
- Produces: `ComicJumpCommand(id: Long, targetIndex: Int)`、`comicProgressIndex(value, totalCount)`、底部 `comic_progress_slider`。

- [ ] **Step 1: 写入失败的进度映射和 UI 测试**

纯函数测试：

```kotlin
assertEquals(0, comicProgressIndex(1f, totalCount = 50))
assertEquals(24, comicProgressIndex(25f, totalCount = 50))
assertEquals(49, comicProgressIndex(50f, totalCount = 50))
assertEquals(49, comicProgressIndex(99f, totalCount = 50))
```

Compose 测试在三张图片的连续阅读状态下断言：

```kotlin
rule.onNodeWithTag("comic_progress_slider").assertIsDisplayed()
rule.onNodeWithTag("comic_progress_label").assertTextEquals("2 / 3")
```

拖动到末端后断言 `onAnchorChanged` 最终收到第三张 logical URL；切换到单图后断言进度条不存在；单图片状态断言 slider disabled 且显示 `1 / 1`。

在 `ImageReaderScreenTest` 中实际拖动 slider；在直接渲染 `ComicReader` 的 50 图动态测试中，把 `jumpCommand` 从 null 更新为 `ComicJumpCommand(1L, 49)`，断言请求路径包含 `/pik/page-050.png`，同时 `requestedMediaPaths().size < 50`。

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.image.ComicProgressTest' `
  --tests 'com.local.mediaviewer.ui.image.ComicReaderPolicyTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 进度类型和 UI 标签不存在。

- [ ] **Step 3: 实现进度模型和一次性命令**

创建：

```kotlin
data class ComicJumpCommand(
    val id: Long,
    val targetIndex: Int,
)

internal fun comicProgressIndex(
    value: Float,
    totalCount: Int,
): Int = (value.roundToInt() - 1)
    .coerceIn(0, (totalCount - 1).coerceAtLeast(0))
```

`ImageReaderContent` 保存 `progressPreviewIndex`、递增 command ID 和当前 `ComicJumpCommand?`。拖动期间只更新 preview；`onValueChangeFinished` 创建一次性 command，不逐项修改 ViewModel anchor。

`ComicReader` 新增默认值为 null 的 `jumpCommand` 和 `onJumpHandled` 参数：

```kotlin
LaunchedEffect(jumpCommand?.id) {
    val command = jumpCommand ?: return@LaunchedEffect
    val target = command.targetIndex.coerceIn(images.indices)
    listState.scrollToItem(target)
    onAnchorChanged(images[target].logicalUrl)
    onJumpHandled(command.id)
}
```

外部滚动锚点更新仍只来自当前 most-visible 项，不把普通 anchor 更新反向转换为跳转命令。

- [ ] **Step 4: 实现底部半透明进度区**

在 `ImageReaderOverlayControls` 的 `mode == COMIC` 分支、模式分段控件上方加入：

```kotlin
Text(
    text = "${displayIndex + 1} / $totalCount",
    modifier = Modifier.testTag("comic_progress_label"),
)
Slider(
    value = displayIndex + 1f,
    onValueChange = onComicProgressChanged,
    onValueChangeFinished = onComicProgressFinished,
    valueRange = 1f..totalCount.coerceAtLeast(1).toFloat(),
    steps = (totalCount - 2).coerceAtLeast(0),
    enabled = totalCount > 1,
    modifier = Modifier.testTag("comic_progress_slider"),
)
```

进度区使用现有 `playerColors.topScrimStart` 和圆角 `Surface`，保持半透明；总数始终传 `state.images.size`，不得按静态图/GIF 分段按钮缩减。

- [ ] **Step 5: 运行进度相关测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.image.ComicProgressTest' `
  --tests 'com.local.mediaviewer.ui.image.ComicReaderPolicyTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: `N / total`、单图隐藏、单项禁用、拖动直达和不加载中间全部图片均通过。

- [ ] **Step 6: 提交连续阅读进度**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/image/ComicProgress.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt `
  app/src/test/java/com/local/mediaviewer/ui/image/ComicProgressTest.kt `
  app/src/test/java/com/local/mediaviewer/ui/image/ComicReaderPolicyTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt
git commit -m "feat: add comic reading progress slider"
```

## Task 4：图片功能区自动隐藏与交互暂停

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/image/ReaderControlsState.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/ReaderInteractionModifier.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/ReaderControlsStateTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`

**Interfaces:**
- Consumes: 现有 `PlayerPreferencesRepository.videoControlsAutoHide` 和 `VideoControlsAutoHide.delayMs`。
- Produces: `ReaderControlsState`、`ReaderControlsReducer`、`Modifier.trackReaderInteraction(...)`；`ImageReaderScreen` 新增 `controlsAutoHide` 参数。

- [ ] **Step 1: 写入失败的自动隐藏状态和时钟测试**

纯状态测试：

```kotlin
val interacting = ReaderControlsReducer.beginInteraction(ReaderControlsState())
assertNull(
    ReaderControlsReducer.autoHideDelayMs(
        interacting,
        VideoControlsAutoHide.THREE_SECONDS,
    ),
)
val released = ReaderControlsReducer.endInteraction(interacting)
assertEquals(
    3_000L,
    ReaderControlsReducer.autoHideDelayMs(
        released,
        VideoControlsAutoHide.THREE_SECONDS,
    ),
)
```

Compose UI 测试关闭自动时钟：默认功能区显示，前进 3 秒后消失；单击重新显示；`NEVER` 前进 20 秒仍显示；按住进度滑块超过 3 秒仍显示，抬起后再过 3 秒隐藏。

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ReaderControlsStateTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: reader controls 类型和新 Screen 参数不存在。

- [ ] **Step 3: 实现状态 reducer 和非消费式交互跟踪**

状态固定为：

```kotlin
data class ReaderControlsState(
    val visible: Boolean = true,
    val interactionActive: Boolean = false,
    val autoHideEpoch: Long = 0L,
)
```

`ReaderControlsReducer` 提供 `toggle`、`reveal`、`beginInteraction`、`endInteraction` 和 `autoHideDelayMs`。只有 `visible && !interactionActive` 时返回 preference delay；`endInteraction` 递增 epoch，使计时从抬手重新开始。

`trackReaderInteraction` 在根 Box 的 pointer input 中使用 `awaitEachGesture` 和 `awaitFirstDown(requireUnconsumed = false)`，通知按下；等待所有 pointer 抬起后通知结束。它不得调用 `consume()`，子级图片、列表、slider 和按钮继续收到原事件。

- [ ] **Step 4: 接入现有配置和 LaunchedEffect**

`ImageReaderScreen` 新增：

```kotlin
controlsAutoHide: VideoControlsAutoHide =
    VideoControlsAutoHide.THREE_SECONDS,
```

`ImageReaderContent` 用 `ReaderControlsState` 替代布尔 `toolbarVisible`。以 `visible`、`interactionActive`、`autoHideEpoch` 和 preference 作为 `LaunchedEffect` key；delay 非空时等待后隐藏。单击仍调用 toggle，任何按钮/滑动/缩放的按下和抬起通过根 modifier 暂停并重启计时。`ImageReaderScreenTest.setScreen()` 默认显式传 `VideoControlsAutoHide.NEVER`，保持既有测试稳定；只有自动隐藏用例传具体秒数。

在 `MediaViewerApp` 的 ImageReader destination 收集：

```kotlin
val controlsAutoHide by container.playerPreferencesRepository
    .videoControlsAutoHide
    .collectAsStateWithLifecycle(
        initialValue = VideoControlsAutoHide.THREE_SECONDS,
    )
```

然后传给 `ImageReaderScreen`。不修改 DataStore key、设置页面选项或视频播放器行为。

- [ ] **Step 5: 运行自动隐藏测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ReaderControlsStateTest' `
  '-Pkotlin.incremental=false' --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 3 秒、NEVER、按住不隐藏、抬手重新计时和单击恢复全部通过；底部进度和顶部工具栏同时消失/恢复。

- [ ] **Step 6: 提交自动隐藏**

```powershell
git add app/src/main/java/com/local/mediaviewer/image/ReaderControlsState.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ReaderInteractionModifier.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt `
  app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt `
  app/src/test/java/com/local/mediaviewer/image/ReaderControlsStateTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt
git commit -m "feat: auto hide image reader controls"
```

## Task 5：图片阅读基础功能门禁

**Files:**
- Create: `docs/verification/2026-08-03-image-centroid-progress.md`

**Interfaces:**
- Consumes: Tasks 1–4 完整图片阅读增强。
- Produces: 定向测试、模拟器和 ARM64 Release 构建的简短中文记录。

- [ ] **Step 1: 运行图片相关 JVM 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.ZoomStateTest' `
  --tests 'com.local.mediaviewer.image.ComicTransformTest' `
  --tests 'com.local.mediaviewer.image.ReaderControlsStateTest' `
  --tests 'com.local.mediaviewer.ui.image.ComicProgressTest' `
  --tests 'com.local.mediaviewer.ui.image.ComicReaderPolicyTest' `
  --tests 'com.local.mediaviewer.image.ImageReaderViewModelTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 全部通过。

- [ ] **Step 2: 运行必要模拟器测试**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.ComicReaderDynamicLoadingTest,com.local.mediaviewer.MediaViewerNavigationTest' `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: 所列类通过；若无设备，记录 `NOT RUN（无设备）`，不得写成 PASS。

- [ ] **Step 3: 联合 PDF 计划执行一次基础构建门禁**

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease `
  '-Pkotlin.incremental=false' --no-daemon
```

Expected: `BUILD SUCCESSFUL`，Release 仍只包含 `arm64-v8a`。不声称完成 ARM64 真机运行验收。

- [ ] **Step 4: 写入简短验证记录**

记录中只列实际执行的命令、退出码、定向测试结果、模拟器状态和 `app/build/outputs/apk/release/app-release.apk` 路径；不扩展到音视频、服务器、安装器、模糊测试或设备矩阵。

- [ ] **Step 5: 提交验证记录**

```powershell
git add docs/verification/2026-08-03-image-centroid-progress.md
git commit -m "docs: record image reader verification"
```
