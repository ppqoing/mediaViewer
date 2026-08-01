# 播放进度时钟修复与播放器控件分层布局实施计划

> **执行要求：** 实施本计划时必须使用 `superpowers:test-driven-development`，逐项执行 RED → GREEN → REFACTOR；完成前使用 `superpowers:verification-before-completion`。每个任务使用复选框跟踪。

**目标：** 修复暂停后恢复播放时 UI 时间和进度滑块重复推进的问题，并按 A 方案重排普通视频、全屏视频和音频页面的控制按钮，最后交付重新验证的 `arm64-v8a` Release APK。

**架构：** 保持 LibVLC 和 `PlaybackCoordinator` 为真实播放状态来源，让 `VlcSessionPlayer` 以常量位置快照向 Media3 公布进度，阻止服务层再次外推。普通视频和音频继续复用时间轴、传输控制与新的工具分组行；全屏保留专用覆盖层，但复用同一分组组件和命令回调。

**技术栈：** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、Media3 1.10.1、LibVLC 4.0.0-eap29、JUnit 4、Robolectric、AndroidX Compose Test、Gradle Android 插件。

**设计依据：** `docs/superpowers/specs/2026-08-02-player-progress-controls-layout-design.md`

## 全局约束

- LibVLC 上报的 `PlaybackState.positionMs` 是唯一可信进度；Compose 和 Media3 都不能额外建立递增时钟。
- 不改变 LibVLC 播放速度、媒体准备、播放队列、seek 语义或暂停恢复视频输出的现有命令顺序。
- 普通视频“更多”保留后台播放、速度、模式和比例；全屏“更多”只保留后台播放。
- 音频页面不得出现画面比例、全屏、锁定或视频后台播放控件。
- 保留单击显隐、双击播放/暂停、半透明背景、自动隐藏和 `safeDrawing` 适配。
- 自动隐藏选项仍为 `3 / 5 / 10 / 15 秒 / 不隐藏`，本次不修改偏好数据模型。
- 不新增第三方依赖，不修改 Room schema、版本号、应用 ID 或 ABI 规则。
- 只做基础功能性审查；每项测试先运行一次，实施过程中只重新运行未通过的测试。
- 不修改或提交现有未跟踪的 `.superpowers/brainstorm/`、`dist/` 和 `docs/verification/2026-07-30-arm64-compressed-release.md`，但最终 Release 步骤可按已确认交付范围更新 `dist` 中明确命名的 APK 与校验文件。
- 每次提交只暂存本任务列出的文件，不能使用 `git add .`。

---

## 文件结构与职责

### 新增文件

- `app/src/main/java/com/local/mediaviewer/ui/player/PlayerUtilityRow.kt`：普通视频、全屏视频和音频共用的左右分组工具行。
- `docs/verification/2026-08-02-player-progress-controls-layout.md`：记录定向测试、基础功能检查、构建、ABI、签名与 SHA-256 证据。

### 主要修改文件

- `app/src/main/java/com/local/mediaviewer/service/VlcSessionPlayer.kt`：向 Media3 发布不可外推的 LibVLC 位置快照。
- `app/src/test/java/com/local/mediaviewer/service/VlcSessionPlayerTest.kt`：覆盖暂停恢复、非默认倍速和新位置事件。
- `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt`：为时间轴层提供稳定语义标签。
- `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt`：标记并保持共享高频传输层。
- `app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt`：以“时间轴→传输→工具”结构组合共享控件。
- `app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt`：组合“速度/模式”和“队列/音量”两组工具。
- `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`：组合普通视频的“队列/音量”和右侧全屏入口。
- `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`：把全屏更多菜单移至顶部，将队列移至底部工具组。
- `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`：验证共享控件的垂直层级和主按钮优先级。
- `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`：验证普通视频与音频分组及视频专属边界。
- `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`：验证全屏顶部、中央、配置层和工具层。
- `README.md`：同步进度来源和三类播放页面的操作入口说明。

---

### Task 1：用失败测试锁定 Media3 重复推进问题

**Files:**
- Modify: `app/src/test/java/com/local/mediaviewer/service/VlcSessionPlayerTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/service/VlcSessionPlayer.kt`

**接口与边界：**
- Consumes: `PlaybackCoordinator.sessionState.value.playback.positionMs`。
- Produces: `SimpleBasePlayer.State` 中不可自行外推的 `contentPositionMs`。
- Preserves: `PlaybackParameters`、`playWhenReady`、seek、缓冲位置和队列映射。

- [ ] **Step 1：编写“暂停恢复后位置不自行推进”的失败测试**

在 `VlcSessionPlayerTest` 增加测试：

```kotlin
@Test
fun `Media3 does not extrapolate LibVLC position after pause resume`() = runTest {
    val fixture = fixture(this)
    fixture.coordinator.replaceQueue(listOf(item("a")), "a")
    fixture.engine.emit(
        PlaybackState(
            status = PlaybackStatus.PAUSED,
            positionMs = 12_000L,
            durationMs = 60_000L,
            isSeekable = true,
        ),
    )
    settle()

    fixture.player.play()
    settle()
    fixture.engine.emit(
        PlaybackState(
            status = PlaybackStatus.PLAYING,
            positionMs = 12_000L,
            durationMs = 60_000L,
            isSeekable = true,
        ),
    )
    settle()

    shadowOf(Looper.getMainLooper()).idleFor(5L, TimeUnit.SECONDS)
    assertEquals(12_000L, fixture.player.currentPosition)

    fixture.engine.emit(
        PlaybackState(
            status = PlaybackStatus.PLAYING,
            positionMs = 12_750L,
            durationMs = 60_000L,
            isSeekable = true,
        ),
    )
    settle()
    assertEquals(12_750L, fixture.player.currentPosition)
    fixture.close()
}
```

测试文件同时导入 `java.util.concurrent.TimeUnit`。

再增加一个 `2.0x` 用例：设置倍速后推进测试时钟，但不发送新位置事件，断言位置仍等于 LibVLC 快照。

- [ ] **Step 2：运行定向 JVM 测试并确认 RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VlcSessionPlayerTest'
```

Expected: 新增暂停恢复测试 FAIL；现有实现会在播放就绪状态按系统时钟继续外推位置。记录实际值，不修改断言去适配错误行为。

- [ ] **Step 3：把内容位置改为常量快照**

在 `VlcSessionPlayer.getState()` 中替换位置发布方式：

```kotlin
.setContentPositionMs(
    PositionSupplier.getConstant(playback.positionMs.coerceAtLeast(0L)),
)
```

不要修改 `setPlaybackParameters`；倍速仍交给 LibVLC 执行，并由后续绝对位置事件反映。

- [ ] **Step 4：只重新运行失败的 `VlcSessionPlayerTest` 并确认 GREEN**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VlcSessionPlayerTest'
```

Expected: PASS；暂停恢复、`2.0x` 和新绝对位置事件均满足测试。

- [ ] **Step 5：检查差异并提交进度修复**

Run:

```powershell
git diff --check -- app/src/main/java/com/local/mediaviewer/service/VlcSessionPlayer.kt app/src/test/java/com/local/mediaviewer/service/VlcSessionPlayerTest.kt
git status --short
```

Commit:

```powershell
git add -- app/src/main/java/com/local/mediaviewer/service/VlcSessionPlayer.kt app/src/test/java/com/local/mediaviewer/service/VlcSessionPlayerTest.kt
git commit -m "fix(android): use LibVLC position snapshots"
```

---

### Task 2：建立共享的三层控件结构

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerUtilityRow.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`

**接口与语义：**
- Produces: `PlayerUtilityRow(startContent, endContent)`，左右两组保持分组与足够触控尺寸。
- Produces tags: `player_timeline_layer`、`player_transport_layer`、`player_utility_layer`、`player_utility_start_group`、`player_utility_end_group`。
- Preserves: 时间轴拖动、主播放动作解析、上一项/下一项和前后 10 秒命令。

- [ ] **Step 1：编写共享层级和主按钮尺寸失败测试**

在 `PlaybackControlsTest` 增加：

```kotlin
@Test
fun sharedControlsLayerTimelineTransportAndUtilitiesByFrequency() {
    showSharedControlsWithBothUtilityGroups()

    val timeline = boundsOfTag("player_timeline_layer")
    val transport = boundsOfTag("player_transport_layer")
    val utilities = boundsOfTag("player_utility_layer")

    assertTrue(timeline.bottom <= transport.top)
    assertTrue(transport.bottom <= utilities.top)
    assertTrue(boundsOfTag("player_utility_start_group").right <=
        boundsOfTag("player_utility_end_group").left)
    assertTrue(
        boundsOfDescription("播放").width >
            boundsOfDescription("快退 10 秒").width,
    )
}
```

测试内容使用稳定 test tag 和语义边界，不依赖颜色像素或截图。

- [ ] **Step 2：运行单个新增测试并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackControlsTest#sharedControlsLayerTimelineTransportAndUtilitiesByFrequency'
```

Expected: FAIL，原因是共享层级标签和左右工具分组尚不存在。

- [ ] **Step 3：实现 `PlayerUtilityRow` 和稳定层级标签**

实现结构：

```kotlin
@Composable
fun PlayerUtilityRow(
    startContent: @Composable RowScope.() -> Unit,
    endContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_utility_layer"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.testTag("player_utility_start_group"),
            content = startContent,
        )
        Row(
            modifier = Modifier.testTag("player_utility_end_group"),
            content = endContent,
        )
    }
}
```

为左右内部 `Row` 设置一致的小间距。`PlaybackTimeline` 的外层 `Column` 添加 `player_timeline_layer`，`PlaybackTransportControls` 的外层 `Row` 添加 `player_transport_layer`。

- [ ] **Step 4：把 `PlayerControls` 改为时间轴、传输、工具三层**

移除无分组的底部 `FlowRow`，改用 `PlayerUtilityRow`。保留：

- 起始组：可选的速度和播放模式，再组合 `leadingUtilityControls`；
- 结束组：可选队列入口，再组合 `trailingUtilityControls`；
- `showLowFrequencyControls = false` 时不显示速度和模式；
- 所有既有回调和 content description 不变。

槽位接口使用 `@Composable RowScope.() -> Unit`，让调用方明确决定普通视频和音频的两组内容。

- [ ] **Step 5：只重新运行失败测试并确认 GREEN**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackControlsTest#sharedControlsLayerTimelineTransportAndUtilitiesByFrequency'
```

Expected: PASS。

- [ ] **Step 6：运行 `PlaybackControlsTest` 类，处理接口改动回归**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackControlsTest'
```

Expected: PASS；只修复该类中因槽位签名变化或新层级引起的失败。

- [ ] **Step 7：提交共享控件层级**

```powershell
git add -- app/src/main/java/com/local/mediaviewer/ui/player/PlayerUtilityRow.kt app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt
git commit -m "refactor(android): layer shared player controls"
```

---

### Task 3：接入普通视频和音频工具分组

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`

**普通视频分组：**
- 起始组：播放列表、音量。
- 结束组：全屏。
- 速度、模式和比例仍只在右上角“更多”。

**音频分组：**
- 起始组：播放速度、播放模式。
- 结束组：播放列表、音量。
- 不显示视频专属控件。

- [ ] **Step 1：为普通视频和音频分组编写失败测试**

在 `PlayerScreenTest` 增加或扩展测试：

```kotlin
@Test
fun ordinaryVideoGroupsQueueAndVolumeOppositeFullscreen() {
    showOrdinaryVideoWithQueue()

    assertSameGroup("打开队列", "音量，当前 50%，未静音",
        groupTag = "player_utility_start_group")
    assertDescendant("全屏", groupTag = "player_utility_end_group")
    assertTrue(centerX("打开队列") < centerX("全屏"))
}

@Test
fun audioGroupsPlaybackOptionsOppositeQueueAndVolume() {
    showAudioWithQueueAndMode()

    assertDescendant("播放速度，当前 1.0 倍", "player_utility_start_group")
    assertDescendant("播放模式", "player_utility_start_group")
    assertDescendant("打开队列", "player_utility_end_group")
    assertDescendant("音量，当前 50%，未静音", "player_utility_end_group")
    assertVideoOnlyControlsAbsent()
}
```

实际断言使用 Compose `hasAnyAncestor(hasTestTag(...))` 或对应语义树匹配器。

- [ ] **Step 2：运行两个新增测试并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest#ordinaryVideoGroupsQueueAndVolumeOppositeFullscreen,com.local.mediaviewer.PlayerScreenTest#audioGroupsPlaybackOptionsOppositeQueueAndVolume'
```

Expected: 至少一个测试 FAIL；当前普通视频顺序为音量、队列、全屏且没有左右组契约，音频也没有明确工具组。

- [ ] **Step 3：调整普通视频 `PlayerControls` 调用**

- `showLowFrequencyControls = false`；
- 起始槽依次放入“打开队列”和 `PlaybackVolumeControl`；
- 结束槽只放“全屏”；
- 每个入口先调用既有 `revealControls()` 或音量展开处理，保证自动隐藏重新计时；
- 不修改 `OrdinaryPlaybackSettingsMenu` 的四项内容。

- [ ] **Step 4：调整音频 `PlayerControls` 调用**

- 让 `PlayerControls` 在起始组渲染速度和播放模式；
- 队列入口放在结束组首位；
- 音量控件放在结束组队列之后；
- 不增加任何视频参数或后台播放参数。

- [ ] **Step 5：只重新运行失败的两个测试并确认 GREEN**

Run 与 Step 2 相同。Expected: PASS。

- [ ] **Step 6：运行 `PlayerScreenTest`，处理普通/音频基础回归**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest'
```

Expected: PASS；重点确认普通菜单内容、稳定画布、单击/双击、音频无视频控件和窄屏错误操作仍可用。

- [ ] **Step 7：提交普通视频和音频分组**

```powershell
git add -- app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt
git commit -m "feat(android): group ordinary player utilities"
```

---

### Task 4：重排全屏顶部和底部工具组

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`

**布局契约：**
- 顶部：返回、标题、右侧更多菜单。
- 中央：后退 10 秒、大播放/暂停、前进 10 秒。
- 底部：时间轴；速度/模式/比例；左侧上一项/下一项/队列，右侧音量/锁定/退出全屏。

- [ ] **Step 1：编写全屏结构失败测试**

扩展 `fullscreenKeepsBackgroundInMenuAndOptionsBelowTimeline`，并新增：

```kotlin
@Test
fun fullscreenPlacesMoreAtTopAndQueueInBottomUtilityGroup() {
    showFullscreen(hasShownGestureHint = true)

    assertDescendant("更多播放设置", "fullscreen_top_controls")
    assertFalse(isDescendant("打开播放队列", "fullscreen_top_controls"))
    assertDescendant("打开播放队列", "player_utility_start_group")
    assertDescendant("音量，当前 50%，未静音", "player_utility_end_group")
    assertDescendant("锁定控制", "player_utility_end_group")
    assertDescendant("退出全屏", "player_utility_end_group")

    assertTrue(bottomOf("player_timeline_layer") <=
        topOf("fullscreen_inline_playback_options"))
    assertTrue(bottomOf("fullscreen_inline_playback_options") <=
        topOf("player_utility_layer"))
}
```

保留现有 `fullscreenQueueEntryInvokesTheRootCallback`，证明移动后队列入口仍调用根回调。

- [ ] **Step 2：运行新增全屏测试并确认 RED**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.VideoControlsOverlayTest#fullscreenPlacesMoreAtTopAndQueueInBottomUtilityGroup'
```

Expected: FAIL；当前顶部是队列，更多菜单位于底部。

- [ ] **Step 3：移动更多菜单并建立底部双组工具行**

- 从顶部移除 `queue_entry_fullscreen`；
- 在标题之后渲染 `FullscreenPlaybackSettingsMenu`，使其成为右上角入口；
- 保持菜单中只有“后台播放”；
- 时间轴下继续渲染 `fullscreen_inline_playback_options`；
- 使用 `PlayerUtilityRow`：起始组放上一项、下一项、队列；结束组放音量、锁定、退出全屏；
- 保持 `onMenuExpandedChanged`、返回键关闭菜单、锁定和音量弹层优先级。

- [ ] **Step 4：只重新运行失败的新增测试并确认 GREEN**

Run 与 Step 2 相同。Expected: PASS。

- [ ] **Step 5：运行 `VideoControlsOverlayTest` 类**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.VideoControlsOverlayTest'
```

Expected: PASS；重点确认队列回调、更多菜单、中央按钮位置、注入 Insets、锁定、音量和自动隐藏。

- [ ] **Step 6：提交全屏布局**

```powershell
git add -- app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt
git commit -m "feat(android): reorganize fullscreen player controls"
```

---

### Task 5：同步说明并汇总定向基础验收

**Files:**
- Modify: `README.md`
- Create: `docs/verification/2026-08-02-player-progress-controls-layout.md`

- [ ] **Step 1：更新 README**

在播放器能力和画面模式章节补充：

- UI 时间轴只跟随播放器实际上报位置，暂停后保持不动；
- 普通视频底部为时间轴、传输、队列/音量与全屏分组；
- 全屏顶部更多只含后台，速度/模式/比例位于时间轴下方，队列移至底部；
- 音频按速度/模式和队列/音量分组，不包含视频专属控件。

- [ ] **Step 2：汇总相关 JVM 测试结果**

Task 1 已执行并通过 `VlcSessionPlayerTest`，直接把该次新鲜结果写入验证记录，不重复运行。仅当 Task 1 未实际运行或仍有失败时，才运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VlcSessionPlayerTest'
```

Expected: PASS。若失败，只重验失败测试。

- [ ] **Step 3：汇总三类 Compose 测试并编译 AndroidTest**

Task 2、3、4 已分别运行并通过三个 Compose 测试类，直接记录这些新鲜结果，不重复运行。仅当其中某类未实际运行或仍有失败时，才运行对应类，而不是重跑已经通过的类：

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.VideoControlsOverlayTest'
```

无论是否有设备，再执行一次 AndroidTest 编译门禁：

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin
```

Expected: 编译 PASS。若设备不可用，设备断言记录 `NOT RUN`；编译通过不能替代设备断言。

- [ ] **Step 4：执行基础静态检查**

Run:

```powershell
.\gradlew.bat lintDebug assembleDebug
git diff --check
git status --short
```

Expected: Gradle exit 0，`git diff --check` 无输出；状态中只有本任务 README、验证文档和已知未跟踪产物。

- [ ] **Step 5：编写阶段验证记录**

在新验证文档记录：

- 当前 Git 修订；
- `VlcSessionPlayerTest` 结果；
- 三个 Compose 测试类结果或 `NOT RUN` 原因；
- lint/Debug 构建结果；
- 真机暂停、等待、恢复、seek 与三类布局结果；
- 未运行场景不得写成 PASS。

- [ ] **Step 6：提交 README 和阶段验证记录**

```powershell
git add -- README.md docs/verification/2026-08-02-player-progress-controls-layout.md
git commit -m "docs(android): document player progress verification"
```

---

### Task 6：构建并校验更新后的 ARM64 Release APK

**Files:**
- Update expected artifact: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- Update expected artifact: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256`
- Modify: `docs/verification/2026-08-02-player-progress-controls-layout.md`

- [ ] **Step 1：确认目标和工作树边界**

Run:

```powershell
$repositoryRoot = (Resolve-Path -LiteralPath '.').Path
$distRoot = (Resolve-Path -LiteralPath '.\dist').Path
$expectedApk = [IO.Path]::GetFullPath((Join-Path $distRoot 'mediaviewer-v1.1.0-arm64-v8a-release.apk'))
if (-not $expectedApk.StartsWith($distRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'APK 目标越出 dist' }
git status --short
```

确认只会覆盖明确命名的 APK 和 `.sha256`。不要删除或移动 `dist` 中其他文件。

- [ ] **Step 2：运行个人 Release 构建脚本**

Run:

```powershell
.\scripts\Build-PersonalRelease.ps1 -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

该脚本会执行 `testDebugUnitTest`、`lintRelease`、`assembleRelease`、对齐、签名、ABI/压缩/大小检查并发布 APK 和 SHA-256。它是一次新的最终门禁；若失败，只重验失败项或重新执行失败阶段，不重复单独通过的 Compose 测试。

Expected: exit 0，最终 APK 只包含 `arm64-v8a`，签名和 ZIP 对齐通过，体积不超过 70 MiB。

- [ ] **Step 3：独立读取产物证据**

Run:

```powershell
$apk = Resolve-Path -LiteralPath '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk'
Get-Item -LiteralPath $apk | Select-Object FullName, Length, LastWriteTime
Get-FileHash -LiteralPath $apk -Algorithm SHA256
Get-Content -LiteralPath "$apk.sha256"
```

再使用 Android SDK 中的 `apkanalyzer`/`aapt2` 和 `apksigner verify --verbose --print-certs` 独立确认 ABI、包名、版本和签名；不得在日志中输出密钥密码。

- [ ] **Step 4：更新验证文档并提交**

补充：

- Release 构建所使用的源提交 ID；
- APK 绝对路径、字节数和 MiB；
- SHA-256；
- 唯一 ABI；
- APK Signature Scheme 结果和证书用途限制；
- arm64 真机若未安装则明确为 `NOT RUN`。

Commit:

```powershell
git add -- docs/verification/2026-08-02-player-progress-controls-layout.md
git commit -m "docs(android): record player release verification"
```

- [ ] **Step 5：完成前基础审查**

仅检查：

```powershell
git diff e1f0fab..HEAD --check
git status --short
git log -6 --oneline
```

并逐项确认：

- 进度只有 LibVLC 一个可信时钟；
- 三类页面符合 A 方案；
- 普通/全屏菜单内容没有互换；
- 音频没有视频专属控件；
- 未跟踪的预览和历史产物没有被提交；
- 所有未运行设备项均明确标记。

---

## 最终交付内容

- 修复后的进度快照实现和回归测试。
- 普通视频、全屏视频、音频三类分层控件布局。
- 相关 README 和中文验证记录。
- `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`。
- 对应 `.sha256`、唯一 ABI、签名和大小证据。
- 自动化、设备与未运行项目分别报告，不互相替代。
