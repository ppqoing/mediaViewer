# 播放恢复、进度同步与播放器界面改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复暂停恢复及暂停后拖动导致的视频停帧和进度漂移，并完成霓虹双色图标、单一时间轴、竖向音量反馈和分层紧凑播放队列，最终交付只含 `arm64-v8a` 的签名 Release APK。

**Architecture:** 保持 `PlaybackService`/MediaSessionService 对唯一 LibVLC 实例的所有权，在 `PlayerViewModel` 前增加一个轻量、纯函数式 seek 协调模型，用播放器实际位置确认拖动目标；视频恢复通过现有本地 Binder 链路请求 Surface 更新和受控重绑。Compose 层只展示实际位置或当前拖动/待确认目标，并通过共享矢量图标、级别指示器和紧凑队列组件统一视频、音频与迷你播放器界面。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、Material 3、Media3 1.10.1、LibVLC 4.0.0-eap29、Room 2.8.4、JUnit 4、Robolectric、Compose UI Test、PowerShell 7、Android SDK 36

## Global Constraints

- 项目为个人使用，不引入复杂的审查、遥测、审批或重量级状态机框架。
- LibVLC/Media3 报告的实际播放位置是唯一可信播放位置。
- 暂停拖动松手后保持暂停并显示目标帧；点击播放后声音和画面从目标位置同步恢复。
- 视频进入后台后继续播放声音，返回视频页面后画面追上当前实际位置。
- 图标范围只包括视频播放器、音频播放器、迷你播放条、播放队列和音量/亮度反馈。
- 首页、文件浏览器、应用启动图标、字幕、多音轨和投屏不在本次范围。
- 删除时间轴下方独立的 `LinearProgressIndicator`，只保留一个播放进度滑杆。
- 普通模式使用锚定竖向音量浮层；全屏右侧手势显示临时竖向音量轨；左侧亮度只影响当前 Activity。
- 播放队列继续支持手动添加、拖动排序、删除和重启恢复，不迁移 Room 数据模型。
- Release 继续保持 `versionName = "1.1.0"`、`versionCode = 3`、`minSdk = 29`、`targetSdk = 36`，且只包含 `arm64-v8a`。
- APK 使用现有个人调试证书签名；不得把密码、密钥文件或真实密码值写入 Git。
- 自动化构建结果和真机验收结果必须分开陈述。

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/local/mediaviewer/player/SeekSyncState.kt` | 纯函数式拖动预览、待确认 seek 和实际位置协调 |
| `app/src/main/java/com/local/mediaviewer/player/PlayerModels.kt` | 将 seek 协调状态并入 `PlayerUiState`，计算唯一展示位置 |
| `app/src/main/java/com/local/mediaviewer/player/PlayerInteractionReducer.kt` | 开始、更新和完成拖动的用户交互入口 |
| `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt` | 提交一次 seek、延迟播放到确认后、超时回退、媒体切换清理 |
| `app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt` | 250ms 实际位置采样和视频输出刷新命令 |
| `app/src/main/java/com/local/mediaviewer/player/PlaybackController.kt` | 暴露 `refreshVideoOutput()` |
| `app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt` | 暴露引擎级 `refreshVideoOutput()` |
| `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt` | 更新 LibVLC Surface 并对已绑定布局执行受控重绑 |
| `app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt` | 将视频刷新委托给播放引擎 |
| `app/src/main/java/com/local/mediaviewer/service/LocalVideoOutputBinder.kt` | 将 UI 进程内刷新请求安全地传给协调器 |
| `app/src/main/java/com/local/mediaviewer/playback/EngineEventReducer.kt` | 用真实时间前进事件结束陈旧缓冲状态 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlayerIcons.kt` | 播放器专用霓虹双色矢量图标目录 |
| `app/src/main/java/com/local/mediaviewer/ui/player/NeonPlayerIcon.kt` | 双层着色、激活/禁用状态和统一按钮外观 |
| `app/src/main/java/com/local/mediaviewer/ui/player/VerticalLevelIndicator.kt` | 竖向音量/亮度轨和百分比的共享视觉组件 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt` | 单一时间轴，不再显示第二条缓冲条 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackVolumeControl.kt` | 锚定竖向音量浮层、静音和百分比 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt` | 全屏手势的左右竖向临时轨和 seek 中央提示 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt` | 分层紧凑队列、整行拖动、下一项标签和删除 |
| `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt`、`NowPlayingBar.kt`、`PlaybackModeButton.kt`、`PlaybackSpeedMenu.kt`、`VideoControlsOverlay.kt`、`VideoScaleMenu.kt` | 将共享播放器图标应用到音频、视频、迷你条和菜单 |
| `app/src/test/java/com/local/mediaviewer`、`app/src/androidTest/java/com/local/mediaviewer` 中各任务列明的测试文件 | seek、视频刷新、缓冲、音量、图标和队列回归测试 |
| `docs/verification/2026-07-31-player-resume-progress-ui-redesign.md` | 自动化、真机边界和 Release 产物证据 |

---

### Task 1: Pure Seek Synchronization Model

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/player/SeekSyncState.kt`
- Create: `app/src/test/java/com/local/mediaviewer/player/SeekSyncStateTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerModels.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerInteractionReducer.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerInteractionReducerTest.kt`

**Interfaces:**
- Produces: `data class PendingSeek(val mediaKey: String?, val targetMs: Long)`
- Produces: `data class SeekSyncState(val previewMs: Long? = null, val pending: PendingSeek? = null)`
- Produces: `SeekSyncState.begin(actualMs: Long): SeekSyncState`
- Produces: `SeekSyncState.preview(targetMs: Long, durationMs: Long): SeekSyncState`
- Produces: `SeekSyncState.commit(mediaKey: String?): Pair<SeekSyncState, Long?>`
- Produces: `SeekSyncState.reconcile(mediaKey: String?, actualMs: Long, status: PlaybackStatus): SeekSyncState`
- Produces: `SeekSyncState.clear(): SeekSyncState`
- Uses confirmation tolerance `SEEK_CONFIRMATION_TOLERANCE_MS = 1_000L`

- [ ] **Step 1: Write failing pure reducer tests**

Create `SeekSyncStateTest.kt` with these cases:

```kotlin
class SeekSyncStateTest {
    @Test
    fun `commit keeps target visible until matching engine position arrives`() {
        val preview = SeekSyncState()
            .begin(actualMs = 10_000L)
            .preview(targetMs = 34_000L, durationMs = 60_000L)
        val (pending, command) = preview.commit(mediaKey = "movie")

        assertEquals(34_000L, command)
        assertEquals(34_000L, pending.displayedPosition(actualMs = 10_500L))
        assertNotNull(pending.pending)

        val stale = pending.reconcile(
            mediaKey = "movie",
            actualMs = 10_700L,
            status = PlaybackStatus.PAUSED,
        )
        assertEquals(34_000L, stale.displayedPosition(actualMs = 10_700L))

        val confirmed = stale.reconcile(
            mediaKey = "movie",
            actualMs = 33_400L,
            status = PlaybackStatus.PAUSED,
        )
        assertNull(confirmed.pending)
        assertEquals(33_400L, confirmed.displayedPosition(actualMs = 33_400L))
    }

    @Test
    fun `media switch error and end clear pending target`() {
        val pending = SeekSyncState(
            pending = PendingSeek("movie-a", 40_000L),
        )
        assertNull(
            pending.reconcile("movie-b", 0L, PlaybackStatus.OPENING).pending,
        )
        assertNull(
            pending.reconcile("movie-a", 10_000L, PlaybackStatus.ERROR).pending,
        )
        assertNull(
            pending.reconcile("movie-a", 60_000L, PlaybackStatus.ENDED).pending,
        )
    }
}
```

Update `PlayerInteractionReducerTest.kt` so `finishScrub` asserts that the preview becomes a pending target instead of disappearing directly.

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*SeekSyncStateTest' `
  --tests '*PlayerInteractionReducerTest' `
  --no-daemon
```

Expected: FAIL because `SeekSyncState` and `PendingSeek` do not exist.

- [ ] **Step 3: Implement the minimal pure model**

Create `SeekSyncState.kt`:

```kotlin
package com.local.mediaviewer.player

import com.local.mediaviewer.playback.PlaybackStatus
import kotlin.math.abs

data class PendingSeek(
    val mediaKey: String?,
    val targetMs: Long,
)

data class SeekSyncState(
    val previewMs: Long? = null,
    val pending: PendingSeek? = null,
) {
    fun begin(actualMs: Long) = copy(previewMs = actualMs)

    fun preview(targetMs: Long, durationMs: Long) = copy(
        previewMs = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L)),
    )

    fun commit(mediaKey: String?): Pair<SeekSyncState, Long?> {
        val target = previewMs ?: return this to null
        return copy(
            previewMs = null,
            pending = PendingSeek(mediaKey, target),
        ) to target
    }

    fun reconcile(
        mediaKey: String?,
        actualMs: Long,
        status: PlaybackStatus,
    ): SeekSyncState {
        val target = pending ?: return this
        val terminal = status == PlaybackStatus.ERROR ||
            status == PlaybackStatus.ENDED
        val mediaChanged = target.mediaKey != null &&
            mediaKey != null &&
            target.mediaKey != mediaKey
        val confirmed = abs(actualMs - target.targetMs) <=
            SEEK_CONFIRMATION_TOLERANCE_MS
        return if (terminal || mediaChanged || confirmed) {
            copy(pending = null)
        } else {
            this
        }
    }

    fun clear() = SeekSyncState()

    fun displayedPosition(actualMs: Long): Long =
        previewMs ?: pending?.targetMs ?: actualMs
}

internal const val SEEK_CONFIRMATION_TOLERANCE_MS = 1_000L
```

Modify `PlayerUiState`:

```kotlin
val seekSync: SeekSyncState = SeekSyncState(),

val PlayerUiState.displayedPositionMs: Long
    get() = seekSync.displayedPosition(positionMs)
```

Remove `previewPositionMs` from `PlayerUiState`. Change `PlayerInteractionReducer.beginScrub`, `updateScrub`, and `finishScrub` to call `seekSync.begin`, `seekSync.preview`, and `seekSync.commit(state.currentMediaKey)`.

- [ ] **Step 4: Run the focused tests**

Run the command from Step 2.

Expected: both test classes PASS.

- [ ] **Step 5: Commit the pure seek model**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/player/SeekSyncState.kt `
  app/src/main/java/com/local/mediaviewer/player/PlayerModels.kt `
  app/src/main/java/com/local/mediaviewer/player/PlayerInteractionReducer.kt `
  app/src/test/java/com/local/mediaviewer/player/SeekSyncStateTest.kt `
  app/src/test/java/com/local/mediaviewer/player/PlayerInteractionReducerTest.kt
git commit -m "fix: coordinate scrub targets with engine position"
```

### Task 2: ViewModel Confirmation, Deferred Play, and Position Sampling

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/Media3StateMapperTest.kt`

**Interfaces:**
- Consumes: `PlayerUiState.seekSync` and `SeekSyncState.reconcile(...)` from Task 1
- Produces: `PlayerViewModel.play()` defers `controller.play()` while a seek is pending
- Produces: `PlayerViewModel` fallback timeout `SEEK_CONFIRMATION_TIMEOUT_MS = 1_500L`
- Changes: Media3 position observer interval from `500L` to `250L`

- [ ] **Step 1: Add failing ViewModel tests**

Add to `PlayerViewModelTest.kt`:

```kotlin
@Test
fun `paused scrub defers play until engine confirms target`() = runTest(dispatcher) {
    val controller = FakePlaybackController()
    val viewModel = PlayerViewModel(
        initialRequest = request(),
        controller = controller,
        positionStore = FakeStore(),
        session = FakePlayerSession(),
        autoStart = false,
    )
    controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
    runCurrent()

    viewModel.beginScrub()
    viewModel.previewScrub(34_000L)
    viewModel.commitScrub()
    viewModel.play()

    assertEquals(listOf(34_000L), controller.seekCalls)
    assertEquals(0, controller.playCalls)
    assertEquals(34_000L, viewModel.uiState.value.displayedPositionMs)

    controller.emit(playback(PlaybackStatus.PAUSED, 33_500L))
    runCurrent()

    assertEquals(1, controller.playCalls)
    assertNull(viewModel.uiState.value.seekSync.pending)
}

@Test
fun `pending seek play falls back after timeout`() = runTest(dispatcher) {
    val controller = FakePlaybackController()
    val viewModel = PlayerViewModel(
        initialRequest = request(),
        controller = controller,
        positionStore = FakeStore(),
        session = FakePlayerSession(),
        autoStart = false,
    )
    controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
    runCurrent()
    viewModel.beginScrub()
    viewModel.previewScrub(34_000L)
    viewModel.commitScrub()
    viewModel.play()

    advanceTimeBy(1_501L)
    runCurrent()

    assertEquals(1, controller.playCalls)
    assertNull(viewModel.uiState.value.seekSync.pending)
}
```

Use the existing fake controller and test dispatcher. Add a small local `playback(status, position)` factory rather than constructing inconsistent states in each assertion.

```kotlin
private fun playback(
    status: PlaybackStatus,
    positionMs: Long,
) = PlaybackState(
    status = status,
    positionMs = positionMs,
    durationMs = 60_000L,
    isSeekable = true,
)
```

- [ ] **Step 2: Run tests and verify the first play is currently immediate**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*PlayerViewModelTest*paused*scrub*' `
  --tests '*PlayerViewModelTest*pending*seek*' `
  --no-daemon
```

Expected: FAIL because `play()` immediately delegates and no timeout/reconciliation exists.

- [ ] **Step 3: Reconcile engine snapshots in the ViewModel**

In both playback-state collectors:

```kotlin
val current = mutableUiState.value
val reconciled = current.seekSync.reconcile(
    mediaKey = currentMediaKey(),
    actualMs = state.positionMs,
    status = state.status,
)
mutableUiState.value = current
    .withEngine(state)
    .copy(seekSync = reconciled)
completeDeferredPlayIfConfirmed()
```

Initialize the first `PlayerUiState` with:

```kotlin
currentMediaKey = initialRequest.mediaKey,
```

so a pending target always belongs to a concrete media item. When the queue collector reports another `currentMediaKey`, clear a pending target before publishing the new item.

Implement these private members:

```kotlin
private var playAfterSeekConfirmation = false
private var seekConfirmationTimeoutJob: Job? = null

private fun currentMediaKey(): String =
    (controller as? QueuePlaybackController)
        ?.sessionState
        ?.value
        ?.currentItem
        ?.mediaKey
        ?: currentRequest.mediaKey

fun play() {
    if (mutableUiState.value.seekSync.pending == null) {
        controller.play()
        return
    }
    playAfterSeekConfirmation = true
    seekConfirmationTimeoutJob?.cancel()
    seekConfirmationTimeoutJob = viewModelScope.launch {
        delay(SEEK_CONFIRMATION_TIMEOUT_MS)
        mutableUiState.value = mutableUiState.value.copy(
            seekSync = mutableUiState.value.seekSync.clear(),
        )
        completeDeferredPlay()
    }
}

private fun completeDeferredPlayIfConfirmed() {
    if (mutableUiState.value.seekSync.pending == null) {
        completeDeferredPlay()
    }
}

private fun completeDeferredPlay() {
    if (!playAfterSeekConfirmation) return
    playAfterSeekConfirmation = false
    seekConfirmationTimeoutJob?.cancel()
    seekConfirmationTimeoutJob = null
    controller.play()
}
```

`completeDeferredPlayIfConfirmed()` only calls `completeDeferredPlay()` when `pending == null`. `pause()`, retry, media switch, error, end and `onCleared()` cancel the timeout and clear `playAfterSeekConfirmation`. `commitScrub()` continues to send exactly one `controller.seekTo(target)`.

- [ ] **Step 4: Tighten actual-position sampling**

In `Media3PlaybackController.startPositionObserver`, replace:

```kotlin
delay(500L)
```

with:

```kotlin
delay(POSITION_OBSERVER_INTERVAL_MS)
```

and define:

```kotlin
private const val POSITION_OBSERVER_INTERVAL_MS = 250L
```

Keep immediate `publish()` calls from Media3 listener callbacks; do not animate or extrapolate position in Compose.

- [ ] **Step 5: Run ViewModel and player mapping tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*PlayerViewModelTest' `
  --tests '*PlayerInteractionReducerTest' `
  --tests '*SeekSyncStateTest' `
  --tests '*Media3StateMapperTest' `
  --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit confirmed seek playback**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt `
  app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt `
  app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt `
  app/src/test/java/com/local/mediaviewer/player/Media3StateMapperTest.kt
git commit -m "fix: resume playback after seek confirmation"
```

### Task 3: Video Surface Refresh on Resume

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlaybackController.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/service/LocalVideoOutputBinder.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/service/ServiceTestDoubles.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/service/LocalVideoOutputBinderTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/LibVlcVideoOutputTest.kt`

**Interfaces:**
- Produces: `PlaybackEngine.refreshVideoOutput(): Unit`
- Produces: `PlaybackController.refreshVideoOutput(): Unit`
- Produces: `LocalVideoOutputBinder.refresh(): Unit`
- Rule: refresh is idempotent and is a no-op when no video layout is attached
- Rule: wait `120L` milliseconds for a new LibVLC `Vout` event before controlled rebind

- [ ] **Step 1: Write failing delegation and resume tests**

Extend `ServiceTestEngine` with `var refreshVideoOutputCalls = 0` and add:

```kotlin
@Test
fun `same uid can refresh attached output without reloading media`() = runTest {
    val engine = ServiceTestEngine()
    val coordinator = serviceTestCoordinator(this, engine)
    val binder = LocalVideoOutputBinder(
        coordinator = coordinator,
        callingUid = { 42 },
        processUid = { 42 },
    )
    val host = FrameLayout(
        ApplicationProvider.getApplicationContext<Context>(),
    )
    binder.attach(host)

    binder.refresh()

    assertEquals(1, engine.refreshVideoOutputCalls)
    assertEquals(0, engine.prepareCalls)
    binder.detach()
    coordinator.close()
}
```

Add a `PlayerViewModelTest` asserting that a video transition from `PAUSED` to user `play()` calls `refreshVideoOutput()` once before play, while an audio request does not.

- [ ] **Step 2: Run focused tests and verify missing method failure**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*LocalVideoOutputBinderTest' `
  --tests '*PlayerViewModelTest*refresh*output*' `
  --no-daemon
```

Expected: compilation FAIL because refresh methods do not exist.

- [ ] **Step 3: Add the refresh command through existing boundaries**

Add:

```kotlin
fun refreshVideoOutput()
```

to `PlaybackEngine`, and add a default no-op:

```kotlin
fun refreshVideoOutput() = Unit
```

to `PlaybackController` so unrelated fakes remain source-compatible. Delegate in `PlaybackCoordinator`:

```kotlin
override fun refreshVideoOutput() = engine.refreshVideoOutput()
```

Add to `LocalVideoOutputBinder` under the same UID and active checks used by `attach`:

```kotlin
fun refresh() {
    enforceSameUid()
    synchronized(lock) {
        checkActive()
        if (attachedHost != null) coordinator.refreshVideoOutput()
    }
}
```

In `Media3PlaybackController.refreshVideoOutput()`, call `localVideoBinder?.refresh()` and keep `VideoOutputConnectionState` unchanged on success; on failure call `failVideoOutput("画面恢复失败")`.

- [ ] **Step 4: Implement LibVLC update and controlled rebind**

In `AndroidVlcPlaybackEngine`:

```kotlin
private var pendingVideoRebind: Runnable? = null

override fun refreshVideoOutput() {
    if (closed.get()) return
    requireMainThread("刷新")
    val layout = videoLayout ?: return
    mediaPlayer.updateVideoSurfaces()
    cancelPendingVideoRebind()
    val rebind = Runnable {
        pendingVideoRebind = null
        if (closed.get() || videoLayout !== layout) return@Runnable
        mediaPlayer.detachViews()
        mediaPlayer.attachViews(layout, null, false, false)
        mediaPlayer.setVideoScale(videoScaleMode.toLibVlcScaleType())
    }
    pendingVideoRebind = rebind
    mainHandler.postDelayed(rebind, VIDEO_REBIND_DELAY_MS)
}

private fun cancelPendingVideoRebind() {
    pendingVideoRebind?.let(mainHandler::removeCallbacks)
    pendingVideoRebind = null
}

private companion object {
    const val VIDEO_REBIND_DELAY_MS = 120L
}
```

On `MediaPlayer.Event.Vout`, call `cancelPendingVideoRebind()` before `updateVideoSurfaces()`. Also cancel the runnable from `detachVideoOutputInternal()` and `close()`. Do not call `prepare`, replace `mediaPlayer.media`, reset the queue, or alter `mediaPlayer.time`.

Update `PlayerViewModel.play()` so it calls `controller.refreshVideoOutput()` only when:

```kotlin
currentRequest.kind == MediaKind.VIDEO &&
lastStatus == PlaybackStatus.PAUSED
```

If play is deferred by a pending seek, perform the refresh immediately before the eventual `controller.play()`, not when the user first taps.

- [ ] **Step 5: Add instrumentation contract**

Extend `LibVlcVideoOutputTest.kt` to attach a host, invoke refresh, and assert the host still has one `VLCVideoLayout` child and the engine state/media identity is unchanged. This is an API/attachment contract; retain the separate真机停帧验收 because instrumentation cannot prove decoded frames visually advanced.

- [ ] **Step 6: Run engine, binder, coordinator, and ViewModel tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*LocalVideoOutputBinderTest' `
  --tests '*PlaybackCoordinatorTest' `
  --tests '*PlayerViewModelTest' `
  --no-daemon
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit video output refresh**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt `
  app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt `
  app/src/main/java/com/local/mediaviewer/player/PlaybackController.kt `
  app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt `
  app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt `
  app/src/main/java/com/local/mediaviewer/service/LocalVideoOutputBinder.kt `
  app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt `
  app/src/test/java/com/local/mediaviewer/service/ServiceTestDoubles.kt `
  app/src/test/java/com/local/mediaviewer/service/LocalVideoOutputBinderTest.kt `
  app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/LibVlcVideoOutputTest.kt
git commit -m "fix: refresh video surface when resuming"
```

### Task 4: Buffering Lifecycle and Single Timeline

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/playback/EngineEventReducer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/playback/EngineEventReducerTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`

**Interfaces:**
- Rule: a forward `TimeChanged` event while `BUFFERING` returns status to `PLAYING`
- Rule: `PlaybackTimeline` contains exactly one slider and no linear buffering indicator
- Produces test tags: `video_buffering_spinner`, `audio_buffering_spinner`

- [ ] **Step 1: Write failing buffering reducer test**

```kotlin
@Test
fun `time advancing exits stale buffering state`() {
    val updated = EngineEventReducer.reduce(
        PlaybackState(
            status = PlaybackStatus.BUFFERING,
            positionMs = 10_000L,
            bufferedPercent = 25f,
        ),
        EngineEvent.TimeChanged(10_250L),
    )

    assertEquals(PlaybackStatus.PLAYING, updated.status)
    assertEquals(10_250L, updated.positionMs)
}
```

- [ ] **Step 2: Run reducer test and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*EngineEventReducerTest*time advancing*' `
  --no-daemon
```

Expected: FAIL with actual status `BUFFERING`.

- [ ] **Step 3: Implement the minimal real-progress transition**

Change `EngineEvent.TimeChanged` handling:

```kotlin
is EngineEvent.TimeChanged -> {
    val nextPosition = event.positionMs.coerceAtLeast(0L)
    state.copy(
        status = if (
            state.status == PlaybackStatus.BUFFERING &&
            nextPosition > state.positionMs
        ) PlaybackStatus.PLAYING else state.status,
        positionMs = nextPosition,
    )
}
```

Do not turn an unchanged or regressing timestamp into `PLAYING`.

- [ ] **Step 4: Remove the second buffering bar**

Delete the `LinearProgressIndicator` import and the entire conditional block from `PlaybackTimeline.kt`. Keep the slider, time text and `displayedPositionMs`.

Add `Modifier.testTag("video_buffering_spinner")` and `Modifier.testTag("audio_buffering_spinner")` to the remaining circular buffering indicators. Update Compose tests to assert:

```kotlin
rule.onNodeWithTag("playback_timeline").assertIsDisplayed()
rule.onNodeWithTag("video_buffering_spinner").assertIsDisplayed()
rule.onNodeWithTag("timeline_buffering_bar").assertDoesNotExist()
```

Do not add `timeline_buffering_bar` to production code; the final assertion documents its permanent absence.

Move the video `BUFFERING` indicator from `Alignment.TopCenter` to `Alignment.Center`; `OPENING` and `BUFFERING` share the central placement but remain separately controlled by `PlaybackStatus`.

- [ ] **Step 5: Run focused tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*EngineEventReducerTest' `
  --no-daemon
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit buffering and timeline changes**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/playback/EngineEventReducer.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt `
  app/src/test/java/com/local/mediaviewer/playback/EngineEventReducerTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt
git commit -m "fix: end stale buffering and simplify timeline"
```

### Task 5: Shared Neon Duotone Player Icon System

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerIcons.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/NeonPlayerIcon.kt`
- Create: `app/src/test/java/com/local/mediaviewer/ui/player/PlayerIconsTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackModeButton.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackSpeedMenu.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoScaleMenu.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`

**Interfaces:**
- Produces: `object PlayerIcons` with `ImageVector` properties `Play`, `Pause`, `Replay`, `Back10`, `Forward10`, `Previous`, `Next`, `Queue`, `Delete`, `Drag`, `Volume`, `Muted`, `Brightness`, `Lock`, `Unlock`, `FullscreenExit`, `Speed`, `Scale`, `RepeatAll`, `RepeatOne`, `Shuffle`, `Sequential`, `Playing`
- Produces: `@Composable fun NeonPlayerIcon(icon: ImageVector, contentDescription: String?, active: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier)`
- Colors: cyan `0xFF48E7FF`, purple `0xFF9B6CFF`

- [ ] **Step 1: Add failing icon inventory test**

Create `PlayerIconsTest.kt`:

```kotlin
class PlayerIconsTest {
    @Test
    fun `player icon inventory has consistent viewport`() {
        val icons = PlayerIcons.all
        assertEquals(23, icons.size)
        assertTrue(icons.all { it.defaultWidth == 24.dp })
        assertTrue(icons.all { it.defaultHeight == 24.dp })
        assertTrue(icons.all { it.viewportWidth == 24f })
        assertTrue(icons.all { it.viewportHeight == 24f })
        assertEquals(icons.size, icons.map { it.name }.distinct().size)
    }
}
```

- [ ] **Step 2: Run inventory test and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*PlayerIconsTest' `
  --no-daemon
```

Expected: compilation FAIL because `PlayerIcons` does not exist.

- [ ] **Step 3: Build the vector inventory**

Use `ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)` and `PathBuilder`/`path` for every listed icon. Define a `filledIcon(name, pathData)` helper that parses SVG path data into a black fill:

```kotlin
private fun filledIcon(
    name: String,
    pathData: String,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()
```

Use these concrete base paths for the high-frequency shapes:

```kotlin
val Play = filledIcon("PlayerPlay", "M8,5 L19,12 L8,19 Z")
val Pause = filledIcon("PlayerPause", "M6,5 H10 V19 H6 Z M14,5 H18 V19 H14 Z")
val Previous = filledIcon("PlayerPrevious", "M6,5 H9 V19 H6 Z M10,12 L19,5 V19 Z")
val Next = filledIcon("PlayerNext", "M15,5 H18 V19 H15 Z M5,5 L14,12 L5,19 Z")
val Volume = filledIcon("PlayerVolume", "M4,9 H8 L13,5 V19 L8,15 H4 Z M15,9 C17,10 17,14 15,15 V12 Z")
val Muted = filledIcon("PlayerMuted", "M4,9 H8 L13,5 V19 L8,15 H4 Z M15,8 L21,14 L19.5,15.5 L13.5,9.5 Z M19.5,8 L21,9.5 L15,15.5 L13.5,14 Z")
val Delete = filledIcon("PlayerDelete", "M7,7 H17 L16,20 H8 Z M9,4 H15 L16,6 H20 V8 H4 V6 H8 Z")
val Drag = filledIcon("PlayerDrag", "M5,7 H19 V9 H5 Z M5,11 H19 V13 H5 Z M5,15 H19 V17 H5 Z")
val Playing = filledIcon("PlayerPlaying", "M5,14 H8 V20 H5 Z M10,9 H13 V20 H10 Z M15,4 H18 V20 H15 Z")

val Replay = filledIcon("PlayerReplay", "M7,4 V7 C9,5 11,4 14,4 C18,4 21,7 21,12 C21,17 17,20 12,20 C8,20 5,18 4,14 H7 C8,16 10,17 12,17 C15,17 18,15 18,12 C18,9 16,7 13,7 C11,7 9,8 8,10 L11,10 L6,15 L1,10 H4 C4,7 5,5 7,4 Z")
val Back10 = filledIcon("PlayerBack10", "M8,5 V2 L3,7 L8,12 V9 C12,6 18,9 18,14 C18,19 12,22 8,18 L10,16 C12,18 15,17 15,14 C15,11 11,10 9,12 L7,10 C7,8 7,7 8,5 Z M5,13 H7 V19 H5 Z")
val Forward10 = filledIcon("PlayerForward10", "M16,5 V2 L21,7 L16,12 V9 C12,6 6,9 6,14 C6,19 12,22 16,18 L14,16 C12,18 9,17 9,14 C9,11 13,10 15,12 L17,10 C17,8 17,7 16,5 Z M17,13 H19 V19 H17 Z")
val Queue = filledIcon("PlayerQueue", "M4,5 H7 V8 H4 Z M9,5 H20 V8 H9 Z M4,10 H7 V13 H4 Z M9,10 H20 V13 H9 Z M4,15 H7 V18 H4 Z M9,15 H16 V18 H9 Z M18,14 L22,17 L18,20 Z")
val Brightness = filledIcon("PlayerBrightness", "M10,2 H14 V6 H10 Z M10,18 H14 V22 H10 Z M2,10 H6 V14 H2 Z M18,10 H22 V14 H18 Z M4,4 L7,6 L6,8 L3,6 Z M17,16 L21,18 L19,21 L16,17 Z M18,3 L21,6 L17,8 L16,6 Z M3,18 L6,16 L8,18 L6,21 Z M8,8 H16 V16 H8 Z")
val Lock = filledIcon("PlayerLock", "M7,10 H17 V21 H7 Z M9,10 V7 C9,3 15,3 15,7 V10 H13 V7 C13,5 11,5 11,7 V10 Z")
val Unlock = filledIcon("PlayerUnlock", "M7,10 H17 V21 H7 Z M9,10 V7 C9,3 15,3 16,7 L14,8 C14,5 11,5 11,7 V10 Z")
val FullscreenExit = filledIcon("PlayerFullscreenExit", "M4,4 H10 V7 H7 V10 H4 Z M14,4 H20 V10 H17 V7 H14 Z M4,14 H7 V17 H10 V20 H4 Z M17,14 H20 V20 H14 V17 H17 Z")
val Speed = filledIcon("PlayerSpeed", "M4,17 C2,11 6,5 12,5 C18,5 22,11 20,17 H17 C18,13 16,8 12,8 C8,8 6,13 7,17 Z M11,16 L16,10 L14,18 H10 Z")
val Scale = filledIcon("PlayerScale", "M3,3 H10 V6 H7 V9 H4 V6 H3 Z M14,3 H21 V10 H18 V7 H15 V4 H14 Z M3,14 H6 V17 H9 V20 H3 Z M18,14 H21 V21 H14 V18 H17 V15 H18 Z")
val RepeatAll = filledIcon("PlayerRepeatAll", "M6,5 H18 L22,9 L18,13 V10 H7 V13 L3,9 Z M18,19 H6 L2,15 L6,11 V14 H17 V11 L21,15 Z")
val RepeatOne = filledIcon("PlayerRepeatOne", "M6,4 H18 L22,8 L18,12 V9 H7 V12 L3,8 Z M18,20 H6 L2,16 L6,12 V15 H17 V12 L21,16 Z M11,10 H14 V18 H11 Z")
val Shuffle = filledIcon("PlayerShuffle", "M3,6 H7 L18,17 H21 V14 L23,18 L21,22 V20 H17 L6,9 H3 Z M15,6 H21 V4 L23,8 L21,12 V9 H15 L12,12 L10,10 Z M3,17 H7 L9,15 L11,17 L8,20 H3 Z")
val Sequential = filledIcon("PlayerSequential", "M4,5 H16 V8 H4 Z M4,10 H16 V13 H4 Z M4,15 H13 V18 H4 Z M16,14 L21,17 L16,20 Z")
```

Keep the inventory test as the completeness gate. Do not import `androidx.compose.material.icons` into `PlayerIcons.kt`.

The exported shape is always a single-color vector; duotone is applied by the shared renderer. The object must expose an explicit inventory:

```kotlin
val all: List<ImageVector>
    get() = listOf(
        Play, Pause, Replay, Back10, Forward10, Previous, Next,
        Queue, Delete, Drag, Volume, Muted, Brightness, Lock,
        Unlock, FullscreenExit, Speed, Scale, RepeatAll, RepeatOne,
        Shuffle, Sequential, Playing,
    )
```

Do not alter home, browser or launcher resources.

- [ ] **Step 4: Implement the shared duotone renderer**

```kotlin
internal val NeonCyan = Color(0xFF48E7FF)
internal val NeonPurple = Color(0xFF9B6CFF)

@Composable
fun NeonPlayerIcon(
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.size(24.dp),
) {
    val foreground = if (enabled) NeonCyan else NeonCyan.copy(alpha = 0.38f)
    Box(modifier = modifier) {
        if (active) {
            Icon(
                icon,
                contentDescription = null,
                tint = NeonPurple.copy(alpha = 0.55f),
                modifier = Modifier.matchParentSize().offset(1.dp, 1.dp),
            )
        }
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.matchParentSize(),
        )
    }
}
```

Preserve all existing Chinese content descriptions.

- [ ] **Step 5: Replace player-scope Material icons**

Replace playback-related `Icons.Default.*` references in the listed player files with `PlayerIcons` and `NeonPlayerIcon`. Keep navigation-only back arrows unchanged. Use `active = true` for the primary play/pause button, current playing item and selected mode; use `enabled` to preserve disabled semantics.

Update Compose tests to continue locating actions by the same descriptions (`播放`, `暂停`, `快退 10 秒`, `下一项`, `锁定控制`), proving visual replacement did not regress accessibility.

- [ ] **Step 6: Run icon and control tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*PlayerIconsTest' `
  --no-daemon
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit the icon system**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/ui/player/PlayerIcons.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/NeonPlayerIcon.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackModeButton.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackSpeedMenu.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoScaleMenu.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt `
  app/src/test/java/com/local/mediaviewer/ui/player/PlayerIconsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt
git commit -m "feat: add neon duotone player icons"
```

### Task 6: Vertical Volume Popup and Fullscreen Gesture Rails

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/VerticalLevelIndicator.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackVolumeControl.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoGestureLayer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/ui/player/PlaybackVolumeControlTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`

**Interfaces:**
- Produces: `@Composable fun VerticalLevelIndicator(fraction: Float, label: String, icon: ImageVector, modifier: Modifier = Modifier)`
- Changes: `PlaybackVolumeControl(...)` adds `onRefresh: () -> Unit`
- Tags: `volume_popup`, `volume_slider_vertical`, `gesture_volume_rail`, `gesture_brightness_rail`

- [ ] **Step 1: Extend volume model tests**

Add:

```kotlin
@Test
fun fractionIsClampedAndSafeWhenMaximumIsZero() {
    assertEquals(0f, VolumeState(0, 0, true).fraction)
    assertEquals(1f, VolumeState(12, 10, false).fraction)
}
```

Add to `VolumeState`:

```kotlin
val fraction: Float
    get() = if (maximum <= 0) 0f
    else (current.toFloat() / maximum).coerceIn(0f, 1f)
```

- [ ] **Step 2: Add failing Compose assertions**

In `PlaybackControlsTest`, open the volume button and assert:

```kotlin
rule.onNodeWithContentDescription("音量，当前 50%，未静音").performClick()
rule.onNodeWithTag("volume_popup").assertIsDisplayed()
rule.onNodeWithTag("volume_slider_vertical").assertIsDisplayed()
rule.onNodeWithText("50%").assertIsDisplayed()
```

In `VideoGestureLayerTest`, perform a right-side vertical drag and assert `gesture_volume_rail` appears; perform a left-side drag and assert `gesture_brightness_rail` appears.

- [ ] **Step 3: Run focused tests and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*PlaybackVolumeControlTest' `
  --no-daemon
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Expected: unit model passes after adding `fraction`; Android test compilation or runtime still fails because vertical tags/components do not exist.

- [ ] **Step 4: Implement the anchored vertical popup**

Keep `DropdownMenu` anchoring. Inside it use a fixed `64.dp x 220.dp` container with a `192.dp` Material `Slider` rotated `-90f`:

```kotlin
Box(
    modifier = Modifier
        .testTag("volume_popup")
        .width(64.dp)
        .height(220.dp),
    contentAlignment = Alignment.Center,
) {
    Slider(
        value = state.fraction,
        onValueChange = onVolumeChanged,
        valueRange = 0f..1f,
        modifier = Modifier
            .width(192.dp)
            .graphicsLayer { rotationZ = -90f }
            .testTag("volume_slider_vertical"),
    )
}
```

Place percentage text above the slider container and a mute icon button below it. Change the trigger button so it only opens/closes the popup; muting happens only on the explicit mute button. Opening calls the existing `refresh()` through `onExpandedChanged(true)`.

While the popup is visible, keep hardware-key changes synchronized without a global observer:

```kotlin
LaunchedEffect(expanded) {
    while (expanded) {
        onRefresh()
        delay(250L)
    }
}
```

Pass `volumeController::refresh` from `AudioPlayerScreen` and `VideoPlayerScreen` through `VideoControlsOverlay`. When the popup is closed, the polling coroutine stops; opening it always performs an immediate refresh.

Hoist one `SystemVolumeController` in `MediaViewerApp` so the player route and mini player share the same state:

```kotlin
val activity = requireNotNull(LocalActivity.current)
val volumeController = remember(activity) {
    SystemVolumeController(
        requireNotNull(activity.getSystemService(AudioManager::class.java)),
    )
}
LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    volumeController.refresh()
}
```

Remove the route-local duplicate controller. Extend `NowPlayingBar` with `volumeState`, `onVolumeRefresh`, `onToggleMute`, and `onVolumeChanged`, keep its own `volumeExpanded` Boolean, and render `PlaybackVolumeControl` before the queue button. Update `PlaybackQueueUiTest` with a 50% `VolumeState` and assert the mini player opens `volume_slider_vertical`.

- [ ] **Step 5: Implement fullscreen side rails**

`VerticalLevelIndicator` draws a 4.dp rounded background track and a bottom-aligned filled track whose height is `fraction.coerceIn(0f, 1f)`. It includes icon, percentage text and merged accessibility description.

In `PlayerGestureFeedbackOverlay`:

- seek remains a compact center pill;
- brightness is aligned to `CenterStart` and tagged `gesture_brightness_rail`;
- volume is aligned to `CenterEnd` and tagged `gesture_volume_rail`.

Change the overlay root from `Row` to a full-size `Box`. Keep the existing delayed feedback clearing in `VideoPlayerScreen`; locked controls already pass `enabled = false`, so no side gesture is accepted.

- [ ] **Step 6: Run volume and gesture tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*PlaybackVolumeControlTest' `
  --tests '*SystemVolumeControllerTest' `
  --no-daemon
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

If an API 36 emulator is available:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.VideoGestureLayerTest `
  --no-daemon
```

Expected: PASS; when no emulator is available, record the connected test as NOT RUN rather than passing.

- [ ] **Step 7: Commit vertical level controls**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/ui/player/VerticalLevelIndicator.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackVolumeControl.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoGestureLayer.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt `
  app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt `
  app/src/test/java/com/local/mediaviewer/ui/player/PlaybackVolumeControlTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt
git commit -m "feat: add vertical player volume controls"
```

### Task 7: Layered Compact Playback Queue

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`

**Interfaces:**
- Keeps: existing `PlaybackQueueSheet(...)` callback signature
- Produces semantics: `队列项 <name>，正在播放`, `队列项 <name>，即将播放`, `拖动排序 <name>`, `删除 <name>`
- Rule: visible up/down buttons are removed, but custom accessibility actions remain

- [ ] **Step 1: Rewrite queue UI expectations first**

Update `PlaybackQueueUiTest`:

```kotlin
rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
rule.onNodeWithText("顺序播放").assertIsDisplayed()
rule.onNodeWithContentDescription("队列项 第一首，正在播放")
    .assertIsDisplayed()
rule.onNodeWithContentDescription("队列项 第二首，即将播放")
    .assertIsDisplayed()
rule.onNodeWithContentDescription("上移 第一首").assertDoesNotExist()
rule.onNodeWithContentDescription("下移 第一首").assertDoesNotExist()
```

Invoke `SemanticsActions.CustomActions` on the whole queue item node and retain the existing current-item delete confirmation test. Change the drag test to swipe the whole row node rather than a separate handle.

- [ ] **Step 2: Run queue UI test and verify failure**

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Expected: compilation succeeds, but the connected test would fail against the current layout because the new header and row semantics do not exist.

- [ ] **Step 3: Build the compact hierarchy**

In `PlaybackQueueSheet`:

- header text is `"播放队列 · ${queue.items.size} 项"`;
- show `queue.mode.label()` beside it;
- calculate `currentIndex` and `nextMediaKey` with the exact rules below;
- render rows as compact `Surface`/`Card` with minimum height 52.dp;
- current row gets a `NeonPurple` border and `PlayerIcons.Playing`;
- next row gets a small `"即将播放"` label;
- subtitle uses the final URL path segment/media kind without exposing authentication data;
- trailing content contains only `PlayerIcons.Delete`.

Apply drag pointer input and semantics to the entire row:

```kotlin
val dragThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }

Modifier
    .semantics {
        contentDescription = rowDescription
        customActions = buildList {
            if (canMoveUp) add(CustomAccessibilityAction("上移") {
                onMove(index - 1); true
            })
            if (canMoveDown) add(CustomAccessibilityAction("下移") {
                onMove(index + 1); true
            })
            add(CustomAccessibilityAction("删除") {
                onRemove(); true
            })
        }
    }
    .pointerInput(item.mediaKey, index, dragThresholdPx) {
        var accumulatedDrag = 0f
        var moveTriggered = false
        detectVerticalDragGestures(
            onDragStart = {
                accumulatedDrag = 0f
                moveTriggered = false
            },
            onVerticalDrag = { change, dragAmount ->
                if (moveTriggered) {
                    change.consume()
                    return@detectVerticalDragGestures
                }
                accumulatedDrag += dragAmount
                val destination = when {
                    accumulatedDrag <= -dragThresholdPx && canMoveUp ->
                        index - 1
                    accumulatedDrag >= dragThresholdPx && canMoveDown ->
                        index + 1
                    else -> null
                }
                if (destination != null) {
                    onMove(destination)
                    moveTriggered = true
                    change.consume()
                }
            },
        )
    }
    .clickable(onClick = onSelect)
```

Compute the next label without mutating the queue:

```kotlin
private fun PlaybackQueue.nextMediaKeyForLabel(): String? = when (mode) {
    PlaybackMode.SEQUENTIAL -> items.getOrNull(currentIndex + 1)?.mediaKey
    PlaybackMode.REPEAT_ALL ->
        items.getOrNull(currentIndex + 1)?.mediaKey
            ?: items.firstOrNull()?.mediaKey
    PlaybackMode.REPEAT_ONE -> items.getOrNull(currentIndex + 1)?.mediaKey
    PlaybackMode.SHUFFLE ->
        shuffleOrder.getOrNull(shuffleCursor + 1)
}
```

Move the threshold implementation into a focused `queueDragModifier(...)` helper in the same file. Remove visible arrow buttons and the standalone drag button. Preserve current-item confirmation and existing clear actions.

- [ ] **Step 4: Run queue tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests '*QueueNavigatorTest' `
  --tests '*PlaybackQueueDaoTest' `
  --no-daemon
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

If an emulator is available:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackQueueUiTest `
  --no-daemon
```

Expected: queue persistence tests PASS; connected UI result is recorded separately.

- [ ] **Step 5: Commit the compact queue**

```powershell
git add -- `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt
git commit -m "feat: redesign compact playback queue"
```

### Task 8: Integrated Verification, Manual Acceptance, and arm64 Release

**Files:**
- Create: `docs/verification/2026-07-31-player-resume-progress-ui-redesign.md`
- Generated, not committed: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- Generated, not committed: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256`
- May regenerate: `docs/verification/2026-07-30-arm64-compressed-release.md`

**Interfaces:**
- Consumes: `scripts/Invoke-AndroidVerification.ps1`
- Consumes: `scripts/Build-PersonalRelease.ps1`
- Produces: signed, aligned, arm64-only APK and SHA-256
- Produces: explicit PASS/FAIL/NOT RUN evidence for automated and device checks

- [ ] **Step 1: Inspect scope and preserve unrelated files**

```powershell
git status --short
git diff --check
git diff --name-only HEAD
```

Expected: only files from Tasks 1–7 plus the pre-existing untracked `.superpowers/brainstorm/` and `docs/verification/2026-07-30-arm64-compressed-release.md`. Do not add `.superpowers/brainstorm/`. Before running the release script, copy the pre-existing untracked verification record to `C:\tmp\mediaviewer-2026-07-30-arm64-release.before-player-redesign.md` so its previous contents remain recoverable.

```powershell
$oldRecord = '.\docs\verification\2026-07-30-arm64-compressed-release.md'
$backupRecord = 'C:\tmp\mediaviewer-2026-07-30-arm64-release.before-player-redesign.md'
if (Test-Path -LiteralPath $oldRecord) {
  Copy-Item -LiteralPath $oldRecord -Destination $backupRecord -Force
}
```

- [ ] **Step 2: Run the complete local Android gate**

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected: JVM tests, Lint, Debug/Release builds, AndroidTest compilation, manifest checks and ABI checks PASS. This command without `-RunDeviceTests` does not count as device acceptance.

- [ ] **Step 3: Run device tests when an emulator is available**

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -RunDeviceTests
```

If the script reports no configured/running emulator, record connected tests as NOT RUN and continue with static Release validation; do not label them PASS.

- [ ] **Step 4: Perform focused manual device acceptance**

On an arm64 device or compatible emulator, record each item independently:

1. pause → play: both video and audio advance;
2. pause → scrub → release: remains paused and shows the target frame;
3. play after that scrub: picture and sound start from the selected point;
4. repeat pause/scrub/play five times without a frozen frame;
5. background video for at least 15 seconds: sound continues; return shows current frame;
6. normal mode vertical volume popup, mute, unmute and hardware volume-key refresh;
7. fullscreen right volume rail and left brightness rail; locked mode rejects both;
8. queue add, whole-row drag, delete normal/current items and app-restart restoration;
9. no secondary loading bar below the timeline; central buffering spinner clears after playback advances.

Unavailable hardware is recorded as NOT RUN with the reason.

- [ ] **Step 5: Build the signed personal Release**

Set the password only in the current process environment, never in the command history or document:

```powershell
.\scripts\Build-PersonalRelease.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected artifacts:

```text
dist/mediaviewer-v1.1.0-arm64-v8a-release.apk
dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256
```

- [ ] **Step 6: Independently verify APK identity, ABI, signing, alignment, and hash**

```powershell
$sdk = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$apk = '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk'
$buildTools = Get-ChildItem -LiteralPath "$sdk\build-tools" -Directory |
  Sort-Object { [version]$_.Name } -Descending |
  Select-Object -First 1
& "$($buildTools.FullName)\apksigner.bat" verify --verbose --print-certs $apk
& "$($buildTools.FullName)\zipalign.exe" -c -P 16 -v 4 $apk
& "$($buildTools.FullName)\aapt.exe" dump badging $apk |
  Select-String "package:|sdkVersion:|targetSdkVersion:|native-code:"
Import-Module .\scripts\ReleaseApkTools.psm1 -Force
Assert-Arm64CompressedArchive -ApkPath $apk -MaximumBytes 70MB
$actual = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$recorded = ((Get-Content -Raw -LiteralPath "$apk.sha256").Trim() -split '\s+')[0]
if ($actual -ne $recorded) { throw 'APK SHA-256 不一致' }
```

Expected: APK signature verifies, zipalign verifies, package is `com.local.mediaviewer`, version is `1.1.0 (3)`, minimum/target SDK are 29/36, only `arm64-v8a` is present, archive is at most 70 MiB, and hashes match.

- [ ] **Step 7: Write the verification record**

Collect the immutable values first:

```powershell
$testedCommit = git rev-parse HEAD
$apk = '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk'
$verifiedSha = (
  Get-FileHash -LiteralPath $apk -Algorithm SHA256
).Hash.ToLowerInvariant()
"Commit=$testedCommit"
"SHA256=$verifiedSha"
```

Create `docs/verification/2026-07-31-player-resume-progress-ui-redesign.md` with the title `# 播放恢复、进度同步与播放器界面改造验收记录` and these concrete fields:

- the exact commit printed above;
- the exact local-gate command and PASS or FAIL;
- connected tests as PASS, FAIL or `NOT RUN：没有可用模拟器/设备`;
- each of the nine manual checks from Step 4 as PASS, FAIL or NOT RUN with a reason;
- APK path `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`;
- ABI `arm64-v8a`;
- actual APK v2/v3 signature result and the personal debug-certificate limitation;
- actual ZIP alignment result;
- the exact lowercase SHA-256 printed above;
- any unrun arm64 device, real-server or problem-video-path check.

Never infer PASS from compilation alone and do not leave angle-bracket markers in the document.

- [ ] **Step 8: Commit only the evidence document**

```powershell
git add -- `
  docs/verification/2026-07-31-player-resume-progress-ui-redesign.md
git commit -m "docs: record player redesign verification"
```

Do not add APK, checksum, `.superpowers/brainstorm/`, keystore files, passwords, or the pre-existing 2026-07-30 record unless its inclusion is separately approved.

- [ ] **Step 9: Final clean-state and artifact report**

```powershell
git status --short --branch
git diff --check
Get-Item -LiteralPath `
  '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk', `
  '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256' |
  Select-Object FullName,Length,LastWriteTime
```

Report the absolute APK path, SHA-256, signing limitation, automated gate result, connected/manual acceptance result, and any preserved unrelated untracked files.
