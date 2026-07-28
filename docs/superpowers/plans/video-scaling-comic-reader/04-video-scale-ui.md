# TODO 04 Video Scale State and UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在视频页面提供四种中文画面模式，并让当前选择跨全屏、旋转和输出重建保持，但不跨视频持久化。

**Architecture:** `PlayerViewModel` 是当前播放会话模式的唯一状态源；`VideoScaleMenu` 是无状态 Compose 组件；`AndroidVlcPlaybackEngine` 只执行模式。普通控制栏和全屏浮层复用同一个菜单。

**Tech Stack:** Kotlin StateFlow、Compose Material 3 DropdownMenu、Compose UI Test、JUnit。

## Global Constraints

- 新 `PlayerViewModel` 初始模式始终是 `BEST_FIT`。
- 切换模式不得调用 `prepare()`、`play()` 或位置存储。
- 音频页面不显示画面模式。
- 全屏状态必须能够切换模式。
- 四个中文名称固定为：等比适应、裁剪铺满、强制拉伸、原始尺寸。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/ui/player/VideoScaleMenu.kt`
- Create: `app/src/test/java/com/local/mediaviewer/ui/player/VideoScaleLabelsTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerModels.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`

## Interfaces

- Produces:

```kotlin
fun videoScaleLabel(mode: VideoScaleMode): String

@Composable
fun VideoScaleMenu(
    current: VideoScaleMode,
    onSelected: (VideoScaleMode) -> Unit,
    modifier: Modifier = Modifier,
)
```

- Updated state and action:

```kotlin
data class PlayerUiState(
    // Existing fields remain.
    val videoScaleMode:
        VideoScaleMode = VideoScaleMode.BEST_FIT,
)

fun PlayerViewModel.setVideoScaleMode(
    mode: VideoScaleMode,
)
```

- Updated screen parameter:

```kotlin
onVideoScaleModeChanged: (VideoScaleMode) -> Unit
```

## Steps

- [ ] **Step 1: Write failing label and ViewModel tests**

`VideoScaleLabelsTest.kt`:

```kotlin
@Test
fun `四种模式使用固定中文名称`() {
    assertEquals(
        listOf("等比适应", "裁剪铺满", "强制拉伸", "原始尺寸"),
        VideoScaleMode.entries.map(::videoScaleLabel),
    )
}
```

Add to `PlayerViewModelTest`:

```kotlin
@Test
fun `画面模式只更新当前播放器且不重启媒体`() =
    runTest(dispatcher) {
        val engine = FakeEngine()
        val viewModel = PlayerViewModel(
            request(),
            engine,
            FakeStore(),
            FakePlayerSession(),
        )
        runCurrent()
        val preparesBefore = engine.preparedUrls.size
        val playsBefore = engine.playCalls

        viewModel.setVideoScaleMode(
            VideoScaleMode.FILL_CROP,
        )

        assertEquals(
            VideoScaleMode.FILL_CROP,
            viewModel.uiState.value.videoScaleMode,
        )
        assertEquals(
            listOf(VideoScaleMode.FILL_CROP),
            engine.scaleModes,
        )
        assertEquals(preparesBefore, engine.preparedUrls.size)
        assertEquals(playsBefore, engine.playCalls)
    }
```

Also instantiate a second ViewModel and assert its mode is `BEST_FIT`.

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.VideoScaleLabelsTest' `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest'
```

Expected: compilation fails for missing label, state and action.

- [ ] **Step 3: Implement state and action**

Add `videoScaleMode` to `PlayerUiState`; do not change `withEngine()`, so engine
playback events preserve the selected UI mode.

Implement:

```kotlin
fun setVideoScaleMode(mode: VideoScaleMode) {
    if (
        mutableUiState.value.videoScaleMode == mode
    ) {
        return
    }
    engine.setVideoScaleMode(mode)
    mutableUiState.value = mutableUiState.value.copy(
        videoScaleMode = mode,
    )
}
```

Call `engine.setVideoScaleMode(BEST_FIT)` once before initial `play()` only if
the engine does not already guarantee the same default. The resulting fake
test expectation must be explicit and not count it as a user selection.

- [ ] **Step 4: Implement label and menu**

`VideoScaleMenu.kt`:

```kotlin
fun videoScaleLabel(mode: VideoScaleMode): String =
    when (mode) {
        VideoScaleMode.BEST_FIT -> "等比适应"
        VideoScaleMode.FILL_CROP -> "裁剪铺满"
        VideoScaleMode.STRETCH -> "强制拉伸"
        VideoScaleMode.ORIGINAL -> "原始尺寸"
    }
```

The composable owns only `expanded`:

```kotlin
Box(modifier) {
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.testTag("video_scale_menu"),
    ) {
        Icon(
            imageVector = Icons.Default.AspectRatio,
            contentDescription =
                "画面模式：${videoScaleLabel(current)}",
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        VideoScaleMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(videoScaleLabel(mode)) },
                onClick = {
                    expanded = false
                    onSelected(mode)
                },
                leadingIcon = if (mode == current) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
```

- [ ] **Step 5: Integrate normal and fullscreen layouts**

In normal `PlayerControls.trailingControl`, place a Row containing
`VideoScaleMenu` and the existing fullscreen button.

Wrap the fullscreen video output in a `Box`. When fullscreen, render a compact
`VideoScaleMenu` aligned `Alignment.TopEnd` with a semitransparent black
background and safe-system-bar padding. Do not show the normal seek controls.

Update `VideoPlayerScreen` and `MediaViewerApp` signatures to pass
`player::setVideoScaleMode`.

- [ ] **Step 6: Write Compose mode tests**

Add a video test to `PlayerScreenTest` with a fake engine and controller:

```kotlin
rule.onNodeWithTag("video_scale_menu").performClick()
rule.onNodeWithText("等比适应").assertIsDisplayed()
rule.onNodeWithText("裁剪铺满").performClick()
rule.runOnIdle {
    assertEquals(
        VideoScaleMode.FILL_CROP,
        selectedMode,
    )
}
```

Enter fullscreen through the existing button and assert
`video_scale_menu` still exists while normal seek controls do not.

Retain the existing audio assertion that no `vlc_surface` exists, and add:

```kotlin
rule.onNodeWithTag("video_scale_menu")
    .assertDoesNotExist()
```

- [ ] **Step 7: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest' `
  --tests 'com.local.mediaviewer.ui.player.VideoScaleLabelsTest'
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest'
```

Expected: all pass.

- [ ] **Step 8: Verify native playback still seeks**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaPlaybackInstrumentedTest'
```

Expected: video and audio tests pass; mode controls did not change Range or
seek behavior.

- [ ] **Step 9: Review and commit**

Run:

```powershell
.\gradlew.bat lintDebug assembleDebug
git diff --check
git status --short
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add video scale controls"
```

Confirm no mode value was added to DataStore or Room.
