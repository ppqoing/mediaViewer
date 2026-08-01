# 播放器、目录浏览与图片阅读交互改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不重写现有 MediaSession/LibVLC 架构的前提下，完成视频浮层与后台策略、目录兼容、图片手势和全应用异形屏适配，并修复暂停恢复画面卡帧。

**Architecture:** 保留服务拥有播放状态、Compose 通过 `PlaybackController` 发命令的边界；视频页面新增会话级后台开关和统一浮层行为，持久偏好仍走现有 DataStore。目录、单图、条漫和 Insets 分别在既有仓库、阅读器和共享 Scaffold 边界内做定向修改，每项行为遵循 RED → GREEN → REFACTOR。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、Media3 1.10.1、LibVLC 4.0.0-eap29、Coil 3.5.0、DataStore 1.2.1、OkHttp 5.3.0、JUnit 4、Robolectric、AndroidX Compose Test。

## Global Constraints

- 最低系统版本保持 Android 10 / API 29，目标 SDK 保持 36。
- 不新增第三方依赖，不改变 Room schema、稳定 `mediaKey`、逻辑 URL 或队列身份。
- 视频后台播放开关只属于当前视频会话，每次进入默认关闭；音频后台行为不变。
- 自动隐藏选项严格为 `3 秒 / 5 秒 / 10 秒 / 15 秒 / 不隐藏`，默认 3 秒。
- 双击视频只切换播放/暂停，不再触发左右快退、快进；快退、快进按钮和横向进度手势保留。
- 非全屏 Surface 尺寸不能因功能区显示、隐藏而改变；浮层必须半透明。
- 只有文件和文件夹都为空时才显示“路径下无文件”。
- 条漫缩放不得改变已显示图片的请求键或触发网络重载。
- 标准页面只消费一次 `safeDrawing`；沉浸背景可延伸到边缘，必要操作控件必须位于安全区。
- 只做基础功能性审查：需求覆盖、失败测试、明显回归和构建门禁；不增加多轮重量级审查。
- 不修改或提交现有未跟踪的 `.superpowers/brainstorm/` 与 `docs/verification/2026-07-30-arm64-compressed-release.md`。

---

## 文件结构与职责

### 新增文件

- `app/src/main/java/com/local/mediaviewer/settings/VideoControlsAutoHide.kt`：自动隐藏枚举、毫秒映射和存储回退。
- `app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt`：视频会话退出原因和停止/清队列决策。
- `app/src/main/java/com/local/mediaviewer/ui/image/SingleImagePager.kt`：单图分页、锚点同步和缩放手势仲裁。
- `app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt`：后台播放纯策略测试。

### 主要修改文件

- `app/src/main/java/com/local/mediaviewer/settings/PlayerPreferencesRepository.kt`：持久化自动隐藏偏好。
- `app/src/main/java/com/local/mediaviewer/settings/SettingsViewModel.kt`：加载、保存自动隐藏选项并回滚失败状态。
- `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt`：展示五个自动隐藏选项。
- `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`：完整竖屏画布、半透明上下浮层、菜单分工、后台开关和自动隐藏。
- `app/src/main/java/com/local/mediaviewer/ui/player/VideoGestureLayer.kt`：普通/全屏单击与双击仲裁，全屏专属拖动手势开关。
- `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`：全屏进度条下方速度、模式和比例操作区。
- `app/src/main/java/com/local/mediaviewer/player/VideoInteractionReducer.kt`：播放和暂停状态的可配置自动隐藏决策。
- `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`：视频会话开关、退出、真正进入后台和配置重建处理。
- `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt`：保存后退出、停止清队列和暂停恢复命令顺序。
- `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt`：一次性 Surface 刷新/重绑兜底。
- `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`：接入单图分页。
- `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`：向父级报告是否放大，不抢占 1× 单指翻页。
- `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`：使用稳定解码尺寸，不让实时缩放进入请求键。
- `app/src/main/java/com/local/mediaviewer/ui/components/MediaAppScaffold.kt`：根级 Insets 只负责全局底部组件。
- `app/src/main/java/com/local/mediaviewer/ui/components/MediaScreenScaffold.kt`：标准页面的唯一 `safeDrawing` owner。
- `README.md`：同步新手势、后台开关、自动隐藏和单图翻页说明。

---

### Task 1: 建立自动隐藏偏好与设置页入口

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/settings/VideoControlsAutoHide.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/settings/PlayerPreferencesRepository.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Test: `app/src/test/java/com/local/mediaviewer/settings/PlayerPreferencesRepositoryTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/settings/SettingsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`

**Interfaces:**
- Produces: `enum class VideoControlsAutoHide(val delayMs: Long?)`。
- Produces: `PlayerPreferencesRepository.videoControlsAutoHide: Flow<VideoControlsAutoHide>`。
- Produces: `suspend fun PlayerPreferencesRepository.setVideoControlsAutoHide(value: VideoControlsAutoHide)`。
- Produces: `SettingsViewModel.onVideoControlsAutoHideChanged(value: VideoControlsAutoHide)`。
- Consumes: 现有 `DataStore<Preferences>`、`SettingsChoiceRow` 和 `SettingsViewModel` 保存失败回滚模式。

- [ ] **Step 1: 为默认值、五个选项、持久化和非法值回退写失败测试**

```kotlin
@Test
fun `自动隐藏默认三秒并可保存不隐藏`() = runTest {
    val repository = DataStorePlayerPreferencesRepository(
        InMemoryPreferencesDataStore(),
    )

    assertEquals(
        VideoControlsAutoHide.THREE_SECONDS,
        repository.videoControlsAutoHide.first(),
    )

    repository.setVideoControlsAutoHide(VideoControlsAutoHide.NEVER)

    assertEquals(
        VideoControlsAutoHide.NEVER,
        repository.videoControlsAutoHide.first(),
    )
}
```

在同一测试类中直接向测试 DataStore 写入未知字符串，断言读取结果为 `THREE_SECONDS`。在 `SettingsViewModelTest` 断言保存失败恢复前值并设置 `videoControlsAutoHideError`。

- [ ] **Step 2: 运行设置 JVM 测试并确认 RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*PlayerPreferencesRepositoryTest' --tests '*SettingsViewModelTest'
```

Expected: FAIL，原因是 `VideoControlsAutoHide`、Repository 属性和 ViewModel 回调尚不存在。

- [ ] **Step 3: 实现枚举和 DataStore 映射**

```kotlin
enum class VideoControlsAutoHide(val delayMs: Long?) {
    THREE_SECONDS(3_000L),
    FIVE_SECONDS(5_000L),
    TEN_SECONDS(10_000L),
    FIFTEEN_SECONDS(15_000L),
    NEVER(null),
    ;

    companion object {
        fun fromStored(value: String?): VideoControlsAutoHide =
            entries.firstOrNull { it.name == value } ?: THREE_SECONDS
    }
}
```

在 `PlayerPreferencesRepository` 中使用 `stringPreferencesKey("video_controls_auto_hide")`，只存枚举名。保留现有 `hasShownVideoGestures` 行为。

- [ ] **Step 4: 扩展 Settings 状态和保存逻辑**

在 `SettingsUiState` 增加：

```kotlin
val videoControlsAutoHide: VideoControlsAutoHide =
    VideoControlsAutoHide.THREE_SECONDS,
val isSavingVideoControlsAutoHide: Boolean = false,
val videoControlsAutoHideError: String? = null,
```

给 `SettingsViewModel` 注入 `PlayerPreferencesRepository`。`onVideoControlsAutoHideChanged` 采用现有默认图片模式的乐观更新、失败回滚模式，失败文案固定为“自动隐藏时长保存失败”。在 `MediaViewerApp` 的 Settings factory 和 `SettingsScreen` 调用点传入新依赖与回调。

- [ ] **Step 5: 为设置页五个选项写 Compose 失败测试**

```kotlin
@Test
fun settingsShowsFiveVideoControlAutoHideChoices() {
    showSettings(
        SettingsUiState(
            videoControlsAutoHide = VideoControlsAutoHide.TEN_SECONDS,
        ),
    )

    listOf("3 秒", "5 秒", "10 秒", "15 秒", "不隐藏")
        .forEach { rule.onNodeWithText(it).assertExists() }
    rule.onNodeWithTag("video_controls_auto_hide_10")
        .assertIsSelected()
}
```

- [ ] **Step 6: 运行目标 AndroidTest 并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest'
```

Expected: FAIL，设置页尚未显示播放器选项。

- [ ] **Step 7: 实现设置页“视频播放”区并使测试 GREEN**

使用五个 `SettingsChoiceRow`，tag 固定为：

```text
video_controls_auto_hide_3
video_controls_auto_hide_5
video_controls_auto_hide_10
video_controls_auto_hide_15
video_controls_auto_hide_never
```

保存期间禁用这一组选项；错误文本使用礼貌 live region。

- [ ] **Step 8: 运行 Task 1 测试和编译门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*PlayerPreferencesRepositoryTest' --tests '*SettingsViewModelTest'
.\gradlew.bat compileDebugAndroidTestKotlin
```

Expected: PASS。

- [ ] **Step 9: 提交 Task 1**

```powershell
git add app/src/main/java/com/local/mediaviewer/settings app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt app/src/test/java/com/local/mediaviewer/settings app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt
git commit -m "feat(android): configure video control auto hide"
```

---

### Task 2: 重构视频画布、浮层、菜单和单双击手势

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoGestureLayer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/VideoInteractionReducer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/VideoInteractionModels.kt`
- Test: `app/src/test/java/com/local/mediaviewer/player/VideoInteractionReducerTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`

**Interfaces:**
- Consumes: `VideoControlsAutoHide.delayMs` from Task 1。
- Produces: `VideoPlayerScreen(..., backgroundPlaybackEnabled: Boolean, onBackgroundPlaybackChanged: (Boolean) -> Unit, ...)`。
- Produces: `VideoGestureLayer(..., extendedGesturesEnabled: Boolean, onDoubleTap: () -> Unit, ...)`。
- Produces: 普通浮层 tag `video_top_controls_ordinary`、`video_bottom_controls_ordinary`；全屏行 tag `fullscreen_inline_playback_options`。

- [ ] **Step 1: 为自动隐藏状态写 RED 测试**

```kotlin
@Test
fun `播放和暂停按偏好隐藏而不隐藏返回空延迟`() {
    val visible = VideoInteractionState(controlsVisible = true)

    assertEquals(
        5_000L,
        VideoInteractionReducer.autoHideDelayMs(
            PlaybackStatus.PLAYING,
            visible,
            VideoControlsAutoHide.FIVE_SECONDS,
        ),
    )
    assertEquals(
        5_000L,
        VideoInteractionReducer.autoHideDelayMs(
            PlaybackStatus.PAUSED,
            visible,
            VideoControlsAutoHide.FIVE_SECONDS,
        ),
    )
    assertNull(
        VideoInteractionReducer.autoHideDelayMs(
            PlaybackStatus.PAUSED,
            visible,
            VideoControlsAutoHide.NEVER,
        ),
    )
}
```

- [ ] **Step 2: 运行 reducer 测试确认 RED，然后实现最小状态函数**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VideoInteractionReducerTest'
```

Expected: FAIL，`autoHideDelayMs` 尚不存在。

实现函数时，菜单展开、时间轴拖动、手势反馈、锁定状态和控件已隐藏都返回 `null`；只有 `PLAYING`、`PAUSED` 返回偏好毫秒值。

- [ ] **Step 3: 改写单双击 Compose 测试为新契约并确认 RED**

```kotlin
@Test
fun doubleTapInvokesPlaybackToggleWithoutSingleTapOrSeek() {
    var singleTaps = 0
    var doubleTaps = 0
    var seekBacks = 0
    var seekForwards = 0
    setGestureLayer(
        onSingleTap = { singleTaps++ },
        onDoubleTap = { doubleTaps++ },
        onSeekBack = { seekBacks++ },
        onSeekForward = { seekForwards++ },
    )

    doubleTapCenter()

    rule.runOnIdle {
        assertEquals(1, doubleTaps)
        assertEquals(0, singleTaps)
        assertEquals(0, seekBacks)
        assertEquals(0, seekForwards)
    }
}
```

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.VideoGestureLayerTest'
```

Expected: FAIL，现有双击仍按左右区域 seek。

- [ ] **Step 4: 最小修改 VideoGestureLayer**

把第二次 tap 的分支替换为 `currentOnDoubleTap()`。新增 `extendedGesturesEnabled`：普通模式为 `false` 时只处理单击/双击，不启动横向 seek、亮度或音量手势；全屏为 `true` 时保留原拖动行为。删除 `DOUBLE_TAP_SEEK_MS` 和双击 seek 反馈生成代码。

- [ ] **Step 5: 为菜单和画布写 RED Compose 测试**

测试必须断言：

```kotlin
rule.onNodeWithTag("vlc_surface").assertIsDisplayed()
rule.onNodeWithTag("video_top_controls_ordinary").assertIsDisplayed()
rule.onNodeWithTag("video_bottom_controls_ordinary").assertIsDisplayed()
rule.onNodeWithContentDescription("更多播放设置").performClick()
rule.onNodeWithText("后台播放").assertIsDisplayed()
rule.onNodeWithText("播放速度").assertIsDisplayed()
rule.onNodeWithText("播放模式").assertIsDisplayed()
rule.onNodeWithText("画面比例").assertIsDisplayed()
```

进入全屏后断言“其他”菜单只有“后台播放”，并断言 `fullscreen_inline_playback_options` 中存在速度、模式、比例入口。记录 Surface 在功能区显示与隐藏时的 bounds，断言完全相同。

- [ ] **Step 6: 运行播放器 Compose 测试确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.VideoControlsOverlayTest'
```

Expected: FAIL，普通控件仍占布局空间，全屏菜单仍包含三个低频选项，普通模式没有视频点击层。

- [ ] **Step 7: 实现完整画布和半透明浮层**

`VideoPlayerScreen` 根部改为单个 `Box(Modifier.fillMaxSize())`：Surface、状态层、手势层、顶部浮层、底部浮层按 z 顺序叠放。普通模式不再使用 `Column` 的 `weight` 分配 Surface。浮层背景使用透明度固定的深色渐变或 `Color.Black.copy(alpha = 0.58f)`，不能使用不透明 Surface。

普通模式给 `VideoGestureLayer` 传 `extendedGesturesEnabled = false`，全屏传 `true`。双击回调按当前状态执行：`PLAYING/BUFFERING → onPause`，`ENDED → onReplay`，其他状态 → `onPlay`。

- [ ] **Step 8: 拆分普通与全屏菜单**

普通菜单 root 页顺序固定为：后台播放、播放速度、播放模式、画面比例。全屏菜单 root 只渲染带 `Checkbox` 语义的后台播放项；速度、模式、比例使用进度条下方的紧凑按钮/菜单，复用现有选择列表，不复制业务回调。

- [ ] **Step 9: 接入动态隐藏偏好并验证 GREEN**

从 `preferences.videoControlsAutoHide` 收集设置。`LaunchedEffect` 使用 reducer 返回的毫秒值：为 `null` 时不 delay；其他值 delay 后隐藏。单击、双击、按钮、菜单、时间轴、亮度和音量手势调用现有 `revealControls()`，通过 `autoHideEpoch` 重置计时。

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VideoInteractionReducerTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.VideoGestureLayerTest,com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.VideoControlsOverlayTest'
```

Expected: PASS。

- [ ] **Step 10: 提交 Task 2**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/player app/src/main/java/com/local/mediaviewer/player/VideoInteractionReducer.kt app/src/main/java/com/local/mediaviewer/player/VideoInteractionModels.kt app/src/test/java/com/local/mediaviewer/player/VideoInteractionReducerTest.kt app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt
git commit -m "feat(android): redesign video controls and gestures"
```

---

### Task 3: 实现视频会话级后台播放开关与退出策略

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicy.kt`
- Create: `app/src/test/java/com/local/mediaviewer/player/VideoBackgroundPlaybackPolicyTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt`
- Test: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BackgroundPlaybackTest.kt`

**Interfaces:**
- Consumes: `VideoPlayerScreen.backgroundPlaybackEnabled` 和 `onBackgroundPlaybackChanged` from Task 2。
- Produces: `enum class VideoSessionExitReason { NAVIGATE_AWAY, APP_BACKGROUND, CONFIGURATION_CHANGE }`。
- Produces: `VideoBackgroundPlaybackPolicy.shouldStopAndClear(enabled: Boolean, reason: VideoSessionExitReason): Boolean`。
- Produces: `PlayerViewModel.stopAndClear(onFinished: () -> Unit)`。

- [ ] **Step 1: 为纯退出策略写 RED 测试**

```kotlin
@Test
fun `默认关闭时导航和后台清空但配置重建保留`() {
    assertTrue(
        VideoBackgroundPlaybackPolicy.shouldStopAndClear(
            enabled = false,
            reason = VideoSessionExitReason.NAVIGATE_AWAY,
        ),
    )
    assertTrue(
        VideoBackgroundPlaybackPolicy.shouldStopAndClear(
            enabled = false,
            reason = VideoSessionExitReason.APP_BACKGROUND,
        ),
    )
    assertFalse(
        VideoBackgroundPlaybackPolicy.shouldStopAndClear(
            enabled = false,
            reason = VideoSessionExitReason.CONFIGURATION_CHANGE,
        ),
    )
}
```

另测 `enabled = true` 对三种原因均不清空。

- [ ] **Step 2: 运行策略测试确认 RED，然后实现最小纯函数**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VideoBackgroundPlaybackPolicyTest'
```

Expected: FAIL，类型尚不存在。

实现：

```kotlin
object VideoBackgroundPlaybackPolicy {
    fun shouldStopAndClear(
        enabled: Boolean,
        reason: VideoSessionExitReason,
    ): Boolean = !enabled && reason != VideoSessionExitReason.CONFIGURATION_CHANGE
}
```

- [ ] **Step 3: 为 stopAndClear 顺序写 RED ViewModel 测试**

```kotlin
@Test
fun `停止清队列先保存快照再清空并回调`() = runTest(dispatcher) {
    val controller = FakePlaybackController()
    val store = FakeStore()
    val viewModel = playerViewModel(controller = controller, store = store)
    controller.emit(playback(PlaybackStatus.PLAYING, 12_000L))
    runCurrent()
    var finished = false

    viewModel.stopAndClear { finished = true }
    advanceUntilIdle()

    assertEquals(listOf(12_000L), store.savedPositions)
    assertEquals(1, controller.clearAllCalls)
    assertTrue(finished)
}
```

- [ ] **Step 4: 实现 stopAndClear 并使 JVM 测试 GREEN**

复用 `leave` 的幂等保护和 `saveSnapshot(ended = false)`，保存的 `finally` 中调用 `controller.clearAll()`，随后执行 `onFinished()`。不要直接关闭共享控制器。

- [ ] **Step 5: 为页面退出、Home 和旋转写 RED Android 测试**

覆盖：

- 进入视频后复选框默认未选中；
- 普通/全屏切换保持当前勾选；
- 未选中返回后队列为空；
- 已选中返回后队列和播放意图保留；
- `ActivityScenario.recreate()` 不清空；
- 未选中视频在 `moveToState(CREATED)` 模拟真正后台后清空；
- 音频进入后台仍保留队列。

- [ ] **Step 6: 接入 MediaViewerApp 会话状态**

在根 Composable 保存：

```kotlin
var videoBackgroundPlaybackEnabled by rememberSaveable {
    mutableStateOf(false)
}
```

进入新的 `PlayerRoute` 视频会话时重置为 `false`；配置重建由 `rememberSaveable` 恢复。视频 `onBack` 根据策略调用 `player.stopAndClear` 或现有 `player.leave`。音频始终走现有 `leave`。

`Lifecycle.Event.ON_STOP` 中先检查当前是否为视频播放器以及 `activity.isChangingConfigurations`：配置重建只调用现有 `onAppStopped`；真正后台且开关关闭时先 `playbackController.clearAll()`，再执行 `onAppStopped()`；开关打开或当前为音频时保持现有行为。

- [ ] **Step 7: 运行 Task 3 目标门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VideoBackgroundPlaybackPolicyTest' --tests '*PlayerViewModelTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.BackgroundPlaybackTest'
```

Expected: PASS。

- [ ] **Step 8: 提交 Task 3**

```powershell
git add app/src/main/java/com/local/mediaviewer/player app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt app/src/test/java/com/local/mediaviewer/player app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt app/src/androidTest/java/com/local/mediaviewer/BackgroundPlaybackTest.kt
git commit -m "feat(android): gate video background playback per session"
```

---

### Task 4: 修复暂停恢复只有声音、画面停帧

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/playback/VideoOutputRefreshSchedulerTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/LibVlcVideoOutputTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaPlaybackInstrumentedTest.kt`

**Interfaces:**
- Consumes: 现有 `PlaybackController.play()`、`refreshVideoOutput()`、`VideoOutputRefreshScheduler`。
- Produces: 暂停视频恢复命令顺序 `play → refresh`，以及一次不会被陈旧 `Vout` 提前取消的受控重绑。

- [ ] **Step 1: 写命令顺序 RED 测试**

把现有测试改为真实期望：

```kotlin
@Test
fun `paused video starts playback before refreshing output`() =
    runTest(dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = playerViewModel(controller = controller)
        controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
        runCurrent()

        viewModel.play()

        assertEquals(listOf("play", "refresh"), controller.playCommands)
    }
```

- [ ] **Step 2: 写陈旧 Vout 不取消兜底的 RED 测试**

```kotlin
@Test
fun `vout during resume keeps exactly one pending rebind`() {
    val fixture = SchedulerFixture()
    var rebinds = 0

    fixture.scheduler.refresh { rebinds++ }
    fixture.scheduler.onVout()
    fixture.runOwnerPosts()
    fixture.runDelayedPosts()

    assertEquals(1, rebinds)
}
```

同时断言重复 `refresh` 仍只留下最后一个重绑任务，detach 后仍会取消任务。

- [ ] **Step 3: 运行目标 JVM 测试确认 RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*PlayerViewModelTest' --tests '*VideoOutputRefreshSchedulerTest'
```

Expected: FAIL，当前顺序是 `refresh → play`，`onVout` 会取消待执行重绑。

- [ ] **Step 4: 实现最小事件顺序修复**

`PlayerViewModel.playNow()` 对暂停视频先 `controller.play()`，随后 `controller.refreshVideoOutput()`；音频仍只调用 play。`VideoOutputRefreshScheduler.onVout()` 可以更新 surfaces，但不能仅因收到 Vout 就取消当前恢复周期的唯一兜底重绑；新的 refresh 仍替换旧任务，detach/close 仍取消任务。

- [ ] **Step 5: 增加真实 LibVLC 仪器回归**

使用现有本地媒体 fixture：播放到位置大于 1 秒，暂停并记录位置，再播放；等待状态为 `PLAYING` 且位置继续前进。断言宿主仍只有一个 `VLCVideoLayout`、媒体对象身份未变化、队列未变化。测试名称固定为 `pausedVideoResumeAdvancesWithoutReplacingMediaOrSurfaceHost`。

- [ ] **Step 6: 运行 JVM 与设备测试确认 GREEN**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*PlayerViewModelTest' --tests '*VideoOutputRefreshSchedulerTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.LibVlcVideoOutputTest,com.local.mediaviewer.MediaPlaybackInstrumentedTest'
```

Expected: PASS。若设备测试只能证明状态和位置而不能证明真实帧变化，在最终报告把画面验收保留为人工项，不扩大代码改动。

- [ ] **Step 7: 提交 Task 4**

```powershell
git add app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt app/src/test/java/com/local/mediaviewer/playback/VideoOutputRefreshSchedulerTest.kt app/src/androidTest/java/com/local/mediaviewer/LibVlcVideoOutputTest.kt app/src/androidTest/java/com/local/mediaviewer/MediaPlaybackInstrumentedTest.kt
git commit -m "fix(android): resume paused video output reliably"
```

---

### Task 5: 复现并修复目录响应与空目录状态

**Files:**
- Modify: `app/src/test/java/com/local/mediaviewer/network/DirectoryJsonParserTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/network/CaddyDirectoryClientTest.kt`
- Modify when a failing test identifies the boundary: `app/src/main/java/com/local/mediaviewer/network/CaddyEntryDto.kt`
- Modify when a failing test identifies the boundary: `app/src/main/java/com/local/mediaviewer/network/DirectoryJsonParser.kt`
- Modify when a failing test identifies the boundary: `app/src/main/java/com/local/mediaviewer/model/SessionEndpoint.kt`
- Modify when a failing test identifies the boundary: `app/src/main/java/com/local/mediaviewer/browser/DirectoryContentRepository.kt`
- Test: `app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`

**Interfaces:**
- Consumes: `DefaultDirectoryJsonParser.parse(json, logicalDirectoryUrl, requestDirectoryUrl)`。
- Produces: 目标响应中的目录项作为 `AppResult.Success<List<DirectoryEntry>>`。
- Produces: `BrowserUiState.Content` for folder-only page；`BrowserUiState.Empty` only for empty `entries`。

- [ ] **Step 1: 固化目标响应的最小真实样本**

在 `DirectoryJsonParserTest` 添加：

```kotlin
private val ayameFoldersOnlyJson = """
[
  {"name":"129+.7z/","size":0,"url":"./129+.7z/","mod_time":"2026-06-14T06:51:34.7013105Z","mode":2147484159,"is_dir":true,"is_symlink":false},
  {"name":"纱雾/","size":0,"url":"./%E7%BA%B1%E9%9B%BE/","mod_time":"2026-06-15T05:49:45.4945284Z","mode":2147484159,"is_dir":true,"is_symlink":false}
]
""".trimIndent()
```

断言得到两个 `MediaKind.DIRECTORY`，逻辑 URL 和请求 URL 分别正确解析，`mode == 2147484159L`。

- [ ] **Step 2: 运行 parser 测试并记录边界结果**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*DirectoryJsonParserTest'
```

Expected branches:

- FAIL：保留失败堆栈，按失败字段进入 Step 3。
- PASS：当前 parser 已接受真实结构，不改 parser；继续 Step 4 查完整请求链，避免制造无依据修复。

- [ ] **Step 3: 只修复已由失败测试确认的字段兼容问题**

允许的最小修改只有：保持 `mode: Long`、接受 ISO-8601 合法小数时间、使用 `HttpUrl.resolve` 解析 `./` 和已编码中文。缺字段、非法 URL、非法时间及非 JSON 仍返回 `InvalidDirectoryResponse`。修复后重新运行 Step 2，必须 PASS。

- [ ] **Step 4: 用 MockWebServer 写完整客户端 RED/回归测试**

让 MockWebServer 返回 `ayameFoldersOnlyJson` 和 `Content-Type: application/json`，调用 `DefaultCaddyDirectoryClient.listDirectory`，断言成功、两个目录项和两个不同主机的逻辑/请求 URL。若 parser 测试 PASS 但这里 FAIL，修复客户端边界；若仍 PASS，继续仓库层，不修改生产代码。

- [ ] **Step 5: 为 folder-only 与 truly-empty 写 Browser 状态测试**

```kotlin
@Test
fun `只有子目录是内容而完全空目录才为空`() = runTest(dispatcher) {
    val foldersPage = page(rootUrl, listOf(directoryEntry("child")))
    val emptyPage = page(emptyUrl, emptyList())

    val folders = BrowserViewModel(root, repositoryOf(foldersPage))
    advanceUntilIdle()
    assertTrue(folders.uiState.value is BrowserUiState.Content)

    val empty = BrowserViewModel(root, repositoryOf(emptyPage))
    advanceUntilIdle()
    assertTrue(empty.uiState.value is BrowserUiState.Empty)
}
```

- [ ] **Step 6: 写 UI 空提示测试并确认 RED 或回归 GREEN**

目录项存在时断言“路径下无文件”不存在；完全空时断言提示在 `browser_content` 中居中显示。若现有实现已满足，保留测试作为回归，不对 UI 做无意义改动。

- [ ] **Step 7: 运行 Task 5 全部目标测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*DirectoryJsonParserTest' --tests '*CaddyDirectoryClientTest' --tests '*BrowserViewModelTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest'
```

Expected: PASS。若当前源码从一开始完整通过，最终报告明确记录“源码已兼容，新增真实回归覆盖；需用新 APK 验证旧安装问题”，不能声称修改了不存在的 parser 缺陷。

- [ ] **Step 8: 提交 Task 5**

只暂存实际修改的目录相关文件：

```powershell
git add app/src/main/java/com/local/mediaviewer/network app/src/main/java/com/local/mediaviewer/model/SessionEndpoint.kt app/src/main/java/com/local/mediaviewer/browser/DirectoryContentRepository.kt app/src/test/java/com/local/mediaviewer/network app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt
git commit -m "fix(android): accept folder-only directory responses"
```

若生产代码无需修改，提交消息改为：

```powershell
git commit -m "test(android): cover folder-only directory responses"
```

---

### Task 6: 实现单图原始比例左右翻页

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImagePager.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/ImageSequenceTest.kt`

**Interfaces:**
- Produces: `SingleImagePager(images, anchorLogicalUrl, ..., onAnchorChanged)`。
- Produces: `SingleImageViewer(..., onZoomedChanged: (Boolean) -> Unit)`。
- Consumes: `ImageReaderUiState.Content.images`、`anchorLogicalUrl`、`ImageReaderViewModel.updateAnchor`。

- [ ] **Step 1: 写单图翻页 RED Compose 测试**

```kotlin
@Test
fun singleImageSwipesLeftToNextAndRightToPrevious() {
    val anchors = mutableListOf<String>()
    showReader(
        state = contentState(mode = ImageReaderMode.SINGLE),
        onAnchorChanged = anchors::add,
    )

    rule.onNodeWithTag("single_image_pager").performTouchInput {
        swipeLeft()
    }
    rule.waitForIdle()
    rule.onNodeWithContentDescription("c.png").assertIsDisplayed()

    rule.onNodeWithTag("single_image_pager").performTouchInput {
        swipeRight()
    }
    rule.waitForIdle()
    rule.onNodeWithContentDescription("b.png").assertIsDisplayed()
    assertEquals("http://media.example/pik/b.png", anchors.last())
}
```

另测第一页右划和最后一页左划停在边界。

- [ ] **Step 2: 写放大后水平平移不翻页的 RED 测试**

对当前图片执行双指放大，再单指左划，断言当前 contentDescription 和 anchor 不变；双击复位后左划才切到下一张。

- [ ] **Step 3: 运行目标测试确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest'
```

Expected: FAIL，当前单图只渲染一个 `SingleImageViewer`，没有 pager。

- [ ] **Step 4: 实现 SingleImagePager**

使用 Compose Foundation `HorizontalPager` 和 `rememberPagerState`，初始页由 `anchorLogicalUrl` 解析。核心结构：

```kotlin
HorizontalPager(
    state = pagerState,
    userScrollEnabled = !currentPageZoomed,
    key = { page -> images[page].logicalUrl },
    modifier = Modifier.testTag("single_image_pager"),
) { page ->
    SingleImageViewer(
        item = images[page],
        onZoomedChanged = { zoomed ->
            zoomedByUrl[images[page].logicalUrl] = zoomed
        },
        // 其余现有加载、失败和重试参数原样传递
    )
}
```

`snapshotFlow { pagerState.settledPage }` 去重后调用 `onAnchorChanged(images[index].logicalUrl)`。图片序列或排序变化时根据 anchor 定位，不用旧数字索引硬跳。

- [ ] **Step 5: 调整 SingleImageViewer 手势消费**

`onZoomedChanged(zoom.scale > 1f + 0.001f)`。1× 时单指水平拖动不得被 transform gesture 消费，让 pager 处理；双指或已经放大时由图片平移/缩放处理。双击仍重置到 1× 并报告未放大。

- [ ] **Step 6: 运行 Task 6 测试确认 GREEN**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ImageSequenceTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest'
```

Expected: PASS。

- [ ] **Step 7: 提交 Task 6**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/image/SingleImagePager.kt app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt app/src/test/java/com/local/mediaviewer/image/ImageSequenceTest.kt
git commit -m "feat(android): swipe between single images"
```

---

### Task 7: 保持条漫缩放期间图片请求稳定

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/image/ImageDecodePolicy.kt` only if a named stable comic target helper is needed
- Test: `app/src/test/java/com/local/mediaviewer/image/ImageDecodePolicyTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`

**Interfaces:**
- Consumes: `MediaImageLoaderFactory.createRequest(context, url, decodeSize, requestGeneration)`。
- Produces: 同一 viewport、URL、重试代数下，`ComicTransform.scale` 从 1× 到 5× 不改变 `ImageRequest.memoryCacheKey`。

- [ ] **Step 1: 为稳定条漫解码目标写 RED 单元测试**

如果抽取 helper，接口固定为：

```kotlin
fun ImageDecodePolicy.comicTarget(
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    maxBitmapWidthPx: Int,
    maxBitmapHeightPx: Int,
): ImageDecodeSize
```

测试同一 viewport 得到同一尺寸，并满足 `MAX_PIXELS` 与设备最大位图边界。函数不接收实时缩放比例。

- [ ] **Step 2: 写连续缩放不增加请求次数的 RED Android 测试**

在 `ComicReaderDynamicLoadingTest` 使用 `MockWebServer`：等待首张图片成功后记录 `requestCount`，连续执行两次 pinch zoom，等待 UI idle，断言 requestCount 不变且 `comic_image:<name>` 没有重新出现 loading placeholder。

- [ ] **Step 3: 运行目标测试确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ComicReaderDynamicLoadingTest'
```

Expected: FAIL，当前 `visualScale` 参与 `decodeSize` 和请求 model。

- [ ] **Step 4: 从 ComicImage 请求键移除实时缩放**

删除 `ComicImage.visualScale` 参数和 `remember(..., visualScale)` 键。使用稳定 viewport/设备限制计算一次解码目标：

```kotlin
val decodeSize = remember(
    viewportWidthPx,
    viewportHeightPx,
    deviceBitmapLimits,
) {
    ImageDecodePolicy.comicTarget(
        viewportWidthPx,
        viewportHeightPx,
        deviceBitmapLimits.maxWidthPx,
        deviceBitmapLimits.maxHeightPx,
    )
}
```

条漫缩放仍通过 `requiredWidth(itemWidth)` 与 `horizontalOffsetPx` 完成，不更换 painter。

- [ ] **Step 5: 验证锚点和失败重试没有回归**

保留 `itemRequestGenerations[item.logicalUrl]`，明确重试目标图片仍生成新请求；成功图片不提升代数。运行现有 transform、动态加载、失败重试和模式切换测试。

- [ ] **Step 6: 运行 Task 7 全部目标测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ImageDecodePolicyTest' --tests '*ImageReaderViewModelTest'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ComicReaderDynamicLoadingTest,com.local.mediaviewer.ImageReaderScreenTest'
```

Expected: PASS。

- [ ] **Step 7: 提交 Task 7**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt app/src/main/java/com/local/mediaviewer/image/ImageDecodePolicy.kt app/src/test/java/com/local/mediaviewer/image/ImageDecodePolicyTest.kt app/src/androidTest/java/com/local/mediaviewer/ComicReaderDynamicLoadingTest.kt app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt
git commit -m "fix(android): keep comic image requests stable while zooming"
```

---

### Task 8: 统一全应用刘海、挖孔和系统导航安全区

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaAppScaffold.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/components/MediaScreenScaffold.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/MediaScaffoldTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`

**Interfaces:**
- Produces: 标准页面由 `MediaScreenScaffold(contentWindowInsets = WindowInsets.safeDrawing)` 消费一次安全区。
- Produces: 根 `MediaAppScaffold` 默认 `contentWindowInsets = WindowInsets(0, 0, 0, 0)`，只通过 bottom bar 的 `navigationBarsPadding()` 占据全局底部区域。
- Consumes: 视频、图片、队列已有可注入 WindowInsets 参数。

- [ ] **Step 1: 写根/页面 Insets 不重复的 RED 测试**

在 `MediaScaffoldTest` 注入顶部 24dp、左 16dp、右 20dp、底部 32dp。标准页面顶栏只能偏移一次 24dp，内容左右只能偏移一次 16/20dp；不能得到双倍边距。

- [ ] **Step 2: 为沉浸页和浮层写边界 RED 测试**

断言：

- 视频 Surface/图片 canvas 仍覆盖根 bounds；
- 普通和全屏顶部按钮位于 top/left/right safe bounds 内；
- 底部进度、播放选项、音量和队列列表位于 bottom/left/right safe bounds 内；
- 600dp 宽屏与 2.0 fontScale 下操作仍可达。

- [ ] **Step 3: 运行目标 Compose 测试确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaScaffoldTest,com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlaybackQueueUiTest'
```

Expected: 至少根 Scaffold 与页面 Scaffold 的重复 inset 或某个沉浸浮层边界断言失败。

- [ ] **Step 4: 统一 Insets owner**

`MediaAppScaffold` 默认零 content insets，继续让 NavHost 使用 `appPadding`，其中只包含真实 bottom bar 高度。`MediaScreenScaffold` 保持 `safeDrawing`。迷你播放器继续在自身容器使用一次 `navigationBarsPadding()`。

视频和图片背景不加 safe padding；其顶部/底部控件分别使用 `safeDrawing.only(Top + Horizontal)` 与 `safeDrawing.only(Bottom + Horizontal)`。队列 Sheet 的标题与列表不能同时重复应用 navigation bar inset。

- [ ] **Step 5: 验证全应用页面**

运行首页、目录、设置、音频、普通视频、全屏视频、图片、迷你播放器和队列相关现有 Compose 测试。若某页面依赖根 inset，迁移到其 `MediaScreenScaffold` 或专属沉浸控件，不增加固定 dp 系统栏高度。

- [ ] **Step 6: 运行 Task 8 目标门禁确认 GREEN**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaScaffoldTest,com.local.mediaviewer.HomeSettingsScreenTest,com.local.mediaviewer.BrowserScreenTest,com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.ImageReaderScreenTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlaybackQueueUiTest'
```

Expected: PASS。

- [ ] **Step 7: 提交 Task 8**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/components app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt app/src/main/java/com/local/mediaviewer/ui/player app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderToolbar.kt app/src/androidTest/java/com/local/mediaviewer/MediaScaffoldTest.kt app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt
git commit -m "fix(android): apply display cutout safe areas once"
```

---

### Task 9: 文档、基础功能审查与完整验证

**Files:**
- Modify: `README.md`
- Create: `docs/verification/2026-08-02-player-browser-image-interactions.md`
- Test: `scripts/Invoke-AndroidVerification.ps1`

**Interfaces:**
- Consumes: Tasks 1–8 的最终行为和测试证据。
- Produces: 中文使用说明和 `PASS / FAIL / NOT RUN` 验收记录。

- [ ] **Step 1: 更新 README 行为说明**

准确记录：

- 单击显示/隐藏、双击播放/暂停；
- 后台播放复选框默认关闭及音频例外；
- 自动隐藏五个选项；
- 普通/全屏菜单位置；
- 单图左右翻页和放大后的手势；
- 条漫缩放不触发已显示图片重载；
- 异形屏适配范围。

删除 README 中“视频退到后台后总是继续声音”的旧表述，改成受当前视频会话开关控制。

- [ ] **Step 2: 运行格式与静态检查**

Run:

```powershell
git diff --check
.\gradlew.bat lintDebug compileDebugAndroidTestKotlin
```

Expected: exit 0，无新增 lint error。

- [ ] **Step 3: 运行完整 JVM 和构建门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

Expected: exit 0，全部 JVM 测试通过，Debug/Release APK 生成。

- [ ] **Step 4: 运行现有 Android 验证脚本**

Run:

```powershell
.\scripts\Invoke-AndroidVerification.ps1 -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected: 本地门禁 PASS；脚本未启用的设备和真实服务器步骤明确显示 `NOT RUN`。

- [ ] **Step 5: 在当前可用模拟器运行基础设备集**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: exit 0，设备测试无失败。若模拟器 ABI、服务或环境阻塞，记录准确错误并标为 BLOCKED/NOT RUN，不改成 PASS。

- [ ] **Step 6: 执行问题 URL 与人工视频验收**

只读请求：

```powershell
$response = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/MiddleDir/11111111/Ayame/' -Headers @{ Accept = 'application/json' } -TimeoutSec 10
$response.StatusCode
$response.Headers.'Content-Type'
$response.Content
```

安装 Debug 后确认目标目录显示两个子文件夹且不显示空提示。使用真实视频连续执行至少三轮“播放 → 暂停 → 播放”，观察声音和画面同步。Home、返回、旋转、全屏和后台复选框分别执行一次。刘海/挖孔若当前设备不可模拟，标记 `NOT RUN`，以注入 Insets 自动化结果作为静态证据但不冒充真机通过。

- [ ] **Step 7: 写中文验证报告并做基础功能审查**

报告固定包含：

```markdown
## 自动化结果
| 门禁 | 命令 | 结果 |

## 设备与真实服务器
| 场景 | PASS / FAIL / NOT RUN | 证据或原因 |

## 需求覆盖
| 需求 | 实现文件 | 测试或验收 |

## 剩余限制
```

只检查规格 16 节完成定义、失败测试证据、明显回归和构建结果，不启动额外审查轮次。

- [ ] **Step 8: 提交 Task 9**

```powershell
git add README.md docs/verification/2026-08-02-player-browser-image-interactions.md
git commit -m "docs(android): record media interaction verification"
```

- [ ] **Step 9: 最终核对提交和工作树边界**

Run:

```powershell
git status --short
git log --oneline --decorate -12
```

Expected: 只剩开始前已存在且未纳入范围的未跟踪文件；Tasks 1–9 各自提交可见。不得声称未运行的真实设备或服务器验收已通过。
