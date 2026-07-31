# Media Player and Queue UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将音频、视频普通/全屏、迷你播放器、单一时间轴、竖向音量/亮度和播放队列统一到已批准的媒体化设计系统，并修复缓冲主动作、全屏队列、手势提示、音量浮层和跨多项排序等界面契约。

**Architecture:** 播放状态先映射为一个纯 `PlaybackPrimaryAction`，供普通、全屏和迷你播放器共同消费；Compose 只读取现有 `PlayerUiState`、`PlaybackSessionState` 和窄化回调，不接管 Service、LibVLC、seek 或队列持久化。播放器视觉消费基础计划提供的 `MediaTheme` 和 Material 包装组件，根 `MediaViewerApp.kt` 仍由流程计划 Task 7 的单一集成负责人最终接线。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、Material 3、Media3 1.10.1、LibVLC 4.0.0-eap29、Room 2.8.4、JUnit 4、Compose UI Test、Android SDK 36

## Global Constraints

- 本计划依赖 `docs/superpowers/plans/2026-07-31-media-ui-foundation-pages.md` Tasks 1–3 的稳定主题和共享组件接口。
- `MaterialTheme.colorScheme` 用于普通页面；播放器额外使用 `MediaTheme.playerColors/spacing/sizing/motion`。
- 音频与完整视频播放器共享播放、暂停、重播、前后 10 秒、上一项/下一项、倍速、模式、时间轴、音量和队列视觉语义；迷你播放器只共享它实际呈现的播放/暂停/重播、下一项、队列和实际位置语义，不呈现音量、前后 10 秒、上一项、倍速、模式或可拖动时间轴。
- 亮度、画面比例、锁定、全屏手势只属于视频，不进入音频页。
- 保持 Service 对唯一 LibVLC 实例和播放队列的所有权；不修改 Engine、Surface 刷新、seek 确认、实际位置采样或后台音频语义。
- 视频进入后台继续声音，回前台恢复当前实际位置画面。
- 暂停后拖动松手仍暂停并显示目标帧；再次播放从目标位置继续。
- 只显示一条主播放时间轴，不增加其下方的缓冲进度条。
- 普通音量入口只展开浮层；静音是浮层内独立动作。浮层使用真正竖向调节并在 3 秒无操作、外部点击或返回时关闭。
- 全屏右侧音量和左侧亮度使用不同颜色的临时竖向轨，操作结束约 800ms 后淡出。
- 队列采用分层紧凑列表，保留手动加入、插入下一项、选择、跨多项排序、删除、清空和跨重启恢复。
- 删除普通待播项不确认并提供撤销；删除当前项和停止清空全部保留确认。
- 所有播放器操作触摸区至少 48dp；主播放按钮普通 64dp、全屏 72dp。
- `contentDescription`、`stateDescription`、selected/disabled/toggle/adjustable 和队列上移/下移自定义动作必须可测试。
- 不增加字幕、多音轨、投屏、画中画、均衡器、歌词、在线封面或新播放器业务状态。
- 所有 Gradle 命令由单一执行者串行运行并添加 `'-Pkotlin.incremental=false'`。
- `MediaViewerApp.kt` 只能由流程计划 Task 7 的单一集成负责人修改；本计划 Task 7 给出接线要求但不允许并行代理直接修改。

## File Structure

| File | Responsibility |
|---|---|
| `ui/player/PlaybackPrimaryAction.kt` | 将真实 `PlaybackStatus` 映射为唯一主动作、图标和无障碍状态 |
| `ui/player/PlayerStateOverlay.kt` | Opening/Buffering/Error 的播放器局部状态反馈 |
| `ui/player/AudioArtworkPlaceholder.kt` | 不联网的主题化音频封面占位 |
| `ui/player/PlaybackTransportControls.kt` | 普通音视频主传输控件 |
| `ui/player/PlayerControls.kt` | 自适应普通播放器时间轴与次级操作布局 |
| `ui/player/PlaybackTimeline.kt` | 消费单轨 `MediaTimelineSlider`，左右显示当前/总时长 |
| `ui/player/PlaybackVolumeControl.kt` | 锚定竖向浮层、独立静音、系统音量刷新和无操作关闭 |
| `ui/player/VerticalLevelIndicator.kt` | 全屏音量/亮度临时轨 |
| `ui/player/PlayerGestureFeedback.kt` | 手势反馈分派，不改变手势业务 |
| `ui/player/AudioPlayerScreen.kt` | 音频占位、状态和共享控制的媒体化页面 |
| `ui/player/VideoPlayerScreen.kt` | 普通/全屏播放器壳层、提示关闭和控制显隐 |
| `ui/player/VideoControlsOverlay.kt` | 安全区渐变、真实队列入口、锁定和全屏控制 |
| `ui/player/NowPlayingBar.kt` | 72dp bottom-bar 迷你播放器、实际进度和统一主动作 |
| `ui/player/QueueDragSession.kt` | 纯跨多项拖动累计和目标索引计算 |
| `ui/player/PlaybackQueueSheet.kt` | 自适应 Sheet、拖动把手、分层状态、删除确认和空状态 |
| `app/MediaViewerApp.kt` | 由流程 Task 7 单一所有者完成队列/撤销/迷你条/全屏队列接线 |
| `app/src/test/.../ui/player` | 主动作、拖动累计、音量关闭和纯格式测试 |
| `app/src/androidTest/...` | 普通/全屏/迷你/队列 Compose 行为和语义 |

---

### Task 1: One Playback Primary Action Mapping

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackPrimaryAction.kt`
- Create: `app/src/test/java/com/local/mediaviewer/ui/player/PlaybackPrimaryActionTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`

**Interfaces:**
- Produces: `enum class PlaybackPrimaryCommand { PLAY, PAUSE, REPLAY, NONE }`.
- Produces: `data class PlaybackPrimaryAction(command, icon, contentDescription, stateDescription, enabled, loading)`.
- Produces: `fun playbackPrimaryAction(status: PlaybackStatus): PlaybackPrimaryAction`.
- Consumes only real `PlaybackStatus`; it never accepts or returns `PlayerEntryState.Ready`.
- Produces the preferred, volume-free mini-player contract:

```kotlin
@Composable
fun NowPlayingBar(
    state: PlaybackSessionState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Keeps a source-compatible deprecated overload with the current
  `volumeState/onVolumeRefresh/onToggleMute/onVolumeChanged/onToggle` parameters
  until flow Task 7 switches `MediaViewerApp.kt`. The overload renders no volume
  action and delegates PLAY/PAUSE/REPLAY to `onToggle`; it exists only so Tasks
  1–6 compile before the sole root owner runs. Flow Task 7 must call the preferred
  overload and provide real replay as `{ controller.seekTo(0L); controller.play() }`.

- [ ] **Step 1: Write the failing exhaustive mapping test**

Create `PlaybackPrimaryActionTest.kt`:

```kotlin
class PlaybackPrimaryActionTest {
    @Test
    fun `every real playback status has one approved primary action`() {
        val actions = PlaybackStatus.entries.associateWith(
            ::playbackPrimaryAction,
        )

        assertEquals(PlaybackPrimaryCommand.PLAY, actions.getValue(PlaybackStatus.IDLE).command)
        assertEquals(PlaybackPrimaryCommand.PLAY, actions.getValue(PlaybackStatus.PAUSED).command)
        assertEquals(PlaybackPrimaryCommand.PAUSE, actions.getValue(PlaybackStatus.PLAYING).command)
        assertEquals(PlaybackPrimaryCommand.PAUSE, actions.getValue(PlaybackStatus.BUFFERING).command)
        assertEquals("正在缓冲，可暂停", actions.getValue(PlaybackStatus.BUFFERING).stateDescription)
        assertEquals(PlaybackPrimaryCommand.REPLAY, actions.getValue(PlaybackStatus.ENDED).command)
        assertFalse(actions.getValue(PlaybackStatus.OPENING).enabled)
        assertTrue(actions.getValue(PlaybackStatus.OPENING).loading)
        assertEquals(PlaybackPrimaryCommand.NONE, actions.getValue(PlaybackStatus.ERROR).command)
    }
}
```

- [ ] **Step 2: Run the focused JVM test and verify RED**

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.PlaybackPrimaryActionTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: FAIL because the mapping types do not exist.

- [ ] **Step 3: Implement the pure mapping**

```kotlin
enum class PlaybackPrimaryCommand {
    PLAY,
    PAUSE,
    REPLAY,
    NONE,
}

data class PlaybackPrimaryAction(
    val command: PlaybackPrimaryCommand,
    val icon: ImageVector,
    val contentDescription: String,
    val stateDescription: String? = null,
    val enabled: Boolean = true,
    val loading: Boolean = false,
)

fun playbackPrimaryAction(status: PlaybackStatus): PlaybackPrimaryAction =
    when (status) {
        PlaybackStatus.IDLE,
        PlaybackStatus.PAUSED,
        -> PlaybackPrimaryAction(
            PlaybackPrimaryCommand.PLAY,
            PlayerIcons.Play,
            "播放",
        )
        PlaybackStatus.PLAYING -> PlaybackPrimaryAction(
            PlaybackPrimaryCommand.PAUSE,
            PlayerIcons.Pause,
            "暂停",
        )
        PlaybackStatus.BUFFERING -> PlaybackPrimaryAction(
            PlaybackPrimaryCommand.PAUSE,
            PlayerIcons.Pause,
            "暂停",
            stateDescription = "正在缓冲，可暂停",
        )
        PlaybackStatus.ENDED -> PlaybackPrimaryAction(
            PlaybackPrimaryCommand.REPLAY,
            PlayerIcons.Replay,
            "重新播放",
        )
        PlaybackStatus.OPENING -> PlaybackPrimaryAction(
            PlaybackPrimaryCommand.NONE,
            PlayerIcons.Play,
            "正在打开",
            stateDescription = "正在打开媒体",
            enabled = false,
            loading = true,
        )
        PlaybackStatus.ERROR -> PlaybackPrimaryAction(
            PlaybackPrimaryCommand.NONE,
            PlayerIcons.Play,
            "播放不可用",
            stateDescription = "播放错误",
            enabled = false,
        )
    }
```

- [ ] **Step 4: Make all three surfaces consume the mapping**

Create one internal callback resolver:

```kotlin
fun PlaybackPrimaryCommand.invoke(
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
) = when (this) {
    PlaybackPrimaryCommand.PLAY -> onPlay()
    PlaybackPrimaryCommand.PAUSE -> onPause()
    PlaybackPrimaryCommand.REPLAY -> onReplay()
    PlaybackPrimaryCommand.NONE -> Unit
}
```

Remove independent `playing` booleans and local `when(status)` branches from ordinary transport, fullscreen overlay and the preferred `NowPlayingBar`. Each uses `PlayerIconButton` with the same content/state descriptions. Buffering keeps the pause callback and adds a separate small loading ring without setting `PlayerIconButton.loading = true`, because that foundation flag disables input.

Implement the two mini-player overloads exactly as follows; Task 5 replaces the
temporary row body with the responsive dock but keeps these callbacks:

```kotlin
@Composable
fun NowPlayingBar(
    state: PlaybackSessionState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.currentItem ?: return
    val action = playbackPrimaryAction(state.playback.status)
    NowPlayingBarContent(
        state = state,
        item = item,
        action = action,
        onPrimaryAction = {
            action.command.invoke(onPlay, onPause, onReplay)
        },
        onNext = onNext,
        onOpenQueue = onOpenQueue,
        onOpenPlayer = onOpenPlayer,
        modifier = modifier,
    )
}

@Deprecated("Flow Task 7 switches the root to the volume-free overload")
@Composable
fun NowPlayingBar(
    state: PlaybackSessionState,
    volumeState: VolumeState,
    onVolumeRefresh: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) = NowPlayingBar(
    state = state,
    onPlay = onToggle,
    onPause = onToggle,
    onReplay = onToggle,
    onNext = onNext,
    onOpenQueue = onOpenQueue,
    onOpenPlayer = onOpenPlayer,
    modifier = modifier,
)
```

- [ ] **Step 5: Add executable cross-surface IDLE, Buffering, and ENDED assertions**

In `PlaybackControlsTest.kt`, add this helper inside the test class and call it
from three tests with IDLE, BUFFERING, and ENDED:

```kotlin
private fun showOrdinaryPrimary(
    status: PlaybackStatus,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
) {
    rule.setContent {
        MediaViewerTheme {
            PlayerControls(
                state = PlayerUiState(
                    name = "movie.mp4",
                    kind = MediaKind.VIDEO,
                    status = status,
                    durationMs = 60_000L,
                    isSeekable = true,
                ),
                onPlay = onPlay,
                onPause = onPause,
                onReplay = onReplay,
                onSeekBack = {},
                onSeekForward = {},
                onBeginScrub = {},
                onPreviewScrub = {},
                onCommitScrub = {},
                onPrevious = {},
                onNext = {},
                onSpeedChanged = {},
            )
        }
    }
}

@Test
fun ordinaryBufferingStaysEnabledAndPauses() {
    var pauses = 0
    showOrdinaryPrimary(PlaybackStatus.BUFFERING, {}, { pauses++ }, {})
    rule.onNodeWithContentDescription("暂停")
        .assertIsEnabled()
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "正在缓冲，可暂停",
            ),
        )
        .performClick()
    rule.runOnIdle { assertEquals(1, pauses) }
}
```

Add this exact callback-count assertion to `VideoControlsOverlayTest.kt`:

```kotlin
@Test
fun fullscreenPrimaryUsesRealPlayPauseAndReplayCallbacks() {
    var status by mutableStateOf(PlaybackStatus.IDLE)
    var plays = 0
    var pauses = 0
    var replays = 0
    setOverlay(
        statusProvider = { status },
        onPlay = { plays++ },
        onPause = { pauses++ },
        onReplay = { replays++ },
    )

    rule.onNodeWithContentDescription("播放").performClick()
    rule.runOnIdle {
        assertEquals(1, plays)
        status = PlaybackStatus.BUFFERING
    }
    rule.onNodeWithContentDescription("暂停")
        .assertIsEnabled()
        .performClick()
    rule.runOnIdle {
        assertEquals(1, pauses)
        status = PlaybackStatus.ENDED
    }
    rule.onNodeWithContentDescription("重新播放").performClick()
    rule.runOnIdle { assertEquals(1, replays) }
}
```

Replace that file's existing `setOverlay` helper with the following overload
pair; the existing two tests keep calling the first overload, while the new
test observes recomposition through the second:

```kotlin
private fun setOverlay(status: PlaybackStatus) =
    setOverlay(statusProvider = { status })

private fun setOverlay(
    statusProvider: () -> PlaybackStatus,
    onPlay: () -> Unit = {},
    onPause: () -> Unit = {},
    onReplay: () -> Unit = {},
) {
    rule.setContent {
        MaterialTheme {
            VideoPlayerScreen(
                state = PlayerUiState(
                    name = "视频.mp4",
                    kind = MediaKind.VIDEO,
                    status = statusProvider(),
                    durationMs = 60_000L,
                    isSeekable = true,
                ),
                controller = OverlayPlaybackController(),
                fullscreenController = OverlayFullscreenController(),
                preferences = OverlayPreferencesRepository(),
                volumeController = OverlayVolumeController(),
                brightnessController = OverlayBrightnessController(),
                onPlay = onPlay,
                onPause = onPause,
                onReplay = onReplay,
                onSeekBack = {},
                onSeekForward = {},
                onBeginScrub = {},
                onPreviewScrub = {},
                onCommitScrub = {},
                onPrevious = {},
                onNext = {},
                onSpeedChanged = {},
                onRetry = {},
                onVideoScaleModeChanged = {},
                onBack = {},
            )
        }
    }
}
```

Add `androidx.compose.runtime.getValue`,
`androidx.compose.runtime.mutableStateOf`,
`androidx.compose.runtime.setValue`, `androidx.compose.ui.test.assertIsEnabled`,
and `org.junit.Assert.assertEquals` to that test file. In
`PlaybackQueueUiTest.kt`, replace the old toggle-only test with:

```kotlin
@Test
fun miniPrimaryUsesRealPlayPauseAndReplayCallbacks() {
    val item = item("a", "movie.mp4", MediaKind.VIDEO)
    var plays = 0
    var pauses = 0
    var replays = 0
    var state by mutableStateOf(
        PlaybackSessionState(
            playback = PlaybackState(status = PlaybackStatus.IDLE),
            queue = PlaybackQueue(listOf(item), item.mediaKey),
            currentItem = item,
        ),
    )
    rule.setContent {
        MediaViewerTheme {
            NowPlayingBar(
                state = state,
                onPlay = { plays++ },
                onPause = { pauses++ },
                onReplay = { replays++ },
                onNext = {},
                onOpenQueue = {},
                onOpenPlayer = {},
            )
        }
    }

    rule.onNodeWithContentDescription("播放").performClick()
    rule.runOnIdle {
        assertEquals(1, plays)
        state = state.copy(
            playback = state.playback.copy(status = PlaybackStatus.BUFFERING),
        )
    }
    rule.onNodeWithContentDescription("暂停")
        .assertIsEnabled()
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "正在缓冲，可暂停",
            ),
        )
        .performClick()
    rule.runOnIdle {
        assertEquals(1, pauses)
        state = state.copy(
            playback = state.playback.copy(status = PlaybackStatus.ENDED),
        )
    }
    rule.onNodeWithContentDescription("重新播放").performClick()
    rule.runOnIdle { assertEquals(1, replays) }
}
```

Use this exact helper overload in that file:

```kotlin
private fun item(
    key: String,
    name: String,
    kind: MediaKind = MediaKind.AUDIO,
) = QueueMediaItem(
    mediaKey = key,
    name = name,
    logicalUrl = "http://media.test/$key",
    kind = kind,
)
```

- [ ] **Step 6: Run and commit the mapping**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.PlaybackPrimaryActionTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
git add app/src/main/java/com/local/mediaviewer/ui/player/PlaybackPrimaryAction.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt `
  app/src/test/java/com/local/mediaviewer/ui/player/PlaybackPrimaryActionTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt
git commit -m "fix: unify player primary actions"
```

### Task 2: Media-Themed Audio and Ordinary Video Controls

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/AudioArtworkPlaceholder.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerStateOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NeonPlayerIcon.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackSpeedMenu.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackModeButton.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoScaleMenu.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`

**Interfaces:**
- Consumes: `MediaTheme`, `MediaTopAppBar`, `MediaStatePanel`, `MediaIconButton`, `PlayerIconButton`, `MediaOptionMenu`.
- Keeps all `AudioPlayerScreen` and `VideoPlayerScreen` business callbacks.
- `VlcSurface` call site and key remain stable; no control state wraps or recreates the Surface.

- [ ] **Step 1: Add failing visual hierarchy and audio/video boundary tests**

Extend `PlayerScreenTest.kt`:

```kotlin
@Test
fun audioPlayerHasLocalArtworkAndNoVideoOnlyControls() {
    showAudio(playerState(name = "song.flac", kind = MediaKind.AUDIO))
    rule.onNodeWithTag("audio_artwork").assertIsDisplayed()
    rule.onNodeWithText("song.flac").assertIsDisplayed()
    rule.onNodeWithContentDescription("画面比例").assertDoesNotExist()
    rule.onNodeWithContentDescription("全屏").assertDoesNotExist()
    rule.onNodeWithContentDescription("调节亮度").assertDoesNotExist()
}

@Test
fun ordinaryVideoKeepsOneStableSurfaceAndMovesLowFrequencyOptionsToMenu() {
    showVideo(playerState(name = "movie.mp4", kind = MediaKind.VIDEO))
    rule.onAllNodesWithTag("vlc_surface").assertCountEquals(1)
    rule.onNodeWithContentDescription("更多播放设置").performClick()
    rule.onNodeWithText("播放速度").assertIsDisplayed()
    rule.onNodeWithText("画面比例").assertIsDisplayed()
}

@Test
fun openingBufferingAndErrorKeepNavigationAndSingleTimeline() {
    var state by mutableStateOf(
        playerState(
            name = "movie.mp4",
            kind = MediaKind.VIDEO,
            status = PlaybackStatus.OPENING,
        ),
    )
    showVideo(stateProvider = { state })

    rule.onNodeWithContentDescription("返回").assertIsDisplayed()
    rule.onAllNodesWithTag("playback_timeline").assertCountEquals(1)
    rule.onNodeWithTag("player_state_opening").assertIsDisplayed()

    rule.runOnIdle {
        state = state.copy(status = PlaybackStatus.BUFFERING)
    }
    rule.onNodeWithTag("player_state_buffering").assertIsDisplayed()
    rule.onNodeWithContentDescription("返回").assertIsDisplayed()
    rule.onAllNodesWithTag("playback_timeline").assertCountEquals(1)

    rule.runOnIdle {
        state = state.copy(
            status = PlaybackStatus.ERROR,
            errorMessage = "无法播放该媒体",
        )
    }
    rule.onNodeWithTag("player_state_error").assertIsDisplayed()
    rule.onNodeWithText("无法播放该媒体").assertIsDisplayed()
    rule.onNodeWithContentDescription("返回").assertIsDisplayed()
    rule.onAllNodesWithTag("playback_timeline").assertCountEquals(1)
}
```

Add these exact helpers inside `PlayerScreenTest` before its closing brace. They
only use the fake controllers already defined in that file:

```kotlin
private fun playerState(
    name: String,
    kind: MediaKind,
    status: PlaybackStatus = PlaybackStatus.PAUSED,
) = PlayerUiState(
    name = name,
    kind = kind,
    status = status,
    durationMs = 60_000L,
    isSeekable = true,
)

private fun showAudio(state: PlayerUiState) {
    rule.setContent {
        MediaViewerTheme {
            AudioPlayerScreen(
                state = state,
                onPlay = {},
                onPause = {},
                onReplay = {},
                onSeekBack = {},
                onSeekForward = {},
                onBeginScrub = {},
                onPreviewScrub = {},
                onCommitScrub = {},
                onPrevious = {},
                onNext = {},
                onSpeedChanged = {},
                onRetry = {},
                volumeController = ScreenFakeVolumeController(),
                onBack = {},
            )
        }
    }
}

private fun showVideo(
    stateProvider: () -> PlayerUiState,
) {
    rule.setContent {
        MediaViewerTheme {
            VideoPlayerScreen(
                state = stateProvider(),
                controller = ScreenFakePlaybackController(),
                fullscreenController = ScreenFakeFullscreenController(),
                preferences = ScreenPlayerPreferencesRepository(
                    initiallyShown = true,
                ),
                volumeController = ScreenFakeVolumeController(),
                brightnessController = ScreenFakeBrightnessController(),
                onPlay = {},
                onPause = {},
                onReplay = {},
                onSeekBack = {},
                onSeekForward = {},
                onBeginScrub = {},
                onPreviewScrub = {},
                onCommitScrub = {},
                onPrevious = {},
                onNext = {},
                onSpeedChanged = {},
                playbackMode = PlaybackMode.SEQUENTIAL,
                onPlaybackModeChanged = {},
                onOpenQueue = {},
                onRetry = {},
                onVideoScaleModeChanged = {},
                onBack = {},
            )
        }
    }
}

private fun showVideo(state: PlayerUiState) = showVideo { state }
```

Change the existing private preference fake constructor in the same test file
to the exact compile-safe form used above:

```kotlin
private class ScreenPlayerPreferencesRepository(
    initiallyShown: Boolean = true,
) : PlayerPreferencesRepository {
    private val mutable = MutableStateFlow(initiallyShown)
    override val hasShownVideoGestures: StateFlow<Boolean> = mutable

    override suspend fun markVideoGesturesShown() {
        mutable.value = true
    }
}
```

- [ ] **Step 2: Run the PlayerScreen class and confirm RED**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: new artwork and option-menu assertions fail against the current layout.

- [ ] **Step 3: Build narrow visual components**

`AudioArtworkPlaceholder` is a local 160dp rounded container with a tonal gradient, foundation `MediaIcons.Audio`, and tag `audio_artwork`; it performs no network request.

`PlayerStateOverlay` accepts:

```kotlin
enum class PlayerOverlayKind { OPENING, BUFFERING, ERROR }

@Composable
fun PlayerStateOverlay(
    kind: PlayerOverlayKind,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

Opening/Buffering use compact media-region feedback. Error uses retry as primary
and Back as secondary. It never owns a controller or removes the outer
TopAppBar/timeline. Its root must expose exactly one of
`player_state_opening`, `player_state_buffering`, or `player_state_error`, so
the RED test above observes the state rather than an implementation detail.

- [ ] **Step 4: Recompose ordinary controls with tokens**

`AudioPlayerScreen` uses `MediaTopAppBar`, artwork, title/metadata, shared `PlayerControls` and `PlayerStateOverlay`. It must not import video scale/fullscreen/brightness types.

`VideoPlayerScreen` retains the existing `VlcSurface` as the first child of one stable video Box. Ordinary controls use a responsive `FlowRow`: transport controls stay visible; speed/mode/scale move into one `"更多播放设置"` menu when width is constrained. The ordinary queue action consumes `onOpenQueue` and exposes `Modifier.testTag("queue_entry_ordinary")`; Task 7 uses that owner-produced tag only after navigating to the ordinary Player route.

Replace the remaining player Material Back/Fullscreen/Queue icons with `MediaIconButton`/`PlayerIcons`. `NeonPlayerIcon` consumes `MediaTheme.playerColors` instead of hardcoded `NeonCyan`/`NeonPurple`; keep vector identities and duotone drawing.

- [ ] **Step 5: Run player UI and existing Surface regressions**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.LibVlcVideoOutputTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: new layout tests and existing Surface attach/resume tests PASS.

- [ ] **Step 6: Commit the ordinary player visual unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/player/AudioArtworkPlaceholder.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlayerStateOverlay.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTransportControls.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/NeonPlayerIcon.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackSpeedMenu.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackModeButton.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoScaleMenu.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt
git commit -m "feat: unify ordinary media player visuals"
```

### Task 3: Single Timeline and True Vertical Volume

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackVolumeControl.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VerticalLevelIndicator.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/ui/player/PlaybackVolumeControlTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt`

**Interfaces:**
- Consumes: foundation `MediaTimelineSlider` and `MediaVerticalLevelControl`.
- Keeps: scrub begin/preview/commit and `PlayerVolumeController` callbacks.
- Produces: `VOLUME_POPUP_IDLE_TIMEOUT_MS = 3_000L`.
- Keeps one `playback_timeline` tag; no second buffer/loading track tag is created.

- [ ] **Step 1: Correct the stale volume contract and add executable timeout tests**

Replace any `PlayerScreenTest` expectation that clicking the volume trigger mutes audio. The new contract is:

```kotlin
rule.onNodeWithContentDescription("音量，当前 50%，未静音")
    .performClick()
rule.onNodeWithTag("volume_slider_vertical").assertIsDisplayed()
rule.runOnIdle { assertEquals(0, toggleMuteCalls) }
rule.onNodeWithContentDescription("静音").performClick()
rule.runOnIdle { assertEquals(1, toggleMuteCalls) }
```

Add a JVM test around the pure idle policy:

```kotlin
@Test
fun `volume popup idle policy expires and resets at the approved deadline`() {
    val initial = VolumePopupIdlePolicy(lastInteractionMs = 1_000L)
    assertFalse(initial.shouldClose(nowMs = 3_999L))
    assertTrue(initial.shouldClose(nowMs = 4_000L))

    val reset = initial.interacted(nowMs = 3_500L)
    assertFalse(reset.shouldClose(nowMs = 6_499L))
    assertTrue(reset.shouldClose(nowMs = 6_500L))
}
```

Add these exact connected tests to `PlaybackControlsTest.kt`:

```kotlin
@Test
fun volumePopupClosesAfterThreeSecondsWithoutInteraction() {
    rule.mainClock.autoAdvance = false
    var expanded by mutableStateOf(false)
    rule.setContent {
        MediaViewerTheme {
            PlaybackVolumeControl(
                state = VolumeState(5, 10, false),
                expanded = expanded,
                onExpandedChanged = { expanded = it },
                onRefresh = {},
                onToggleMute = {},
                onVolumeChanged = {},
            )
        }
    }

    rule.onNodeWithContentDescription("音量，当前 50%，未静音")
        .performClick()
    rule.mainClock.advanceTimeBy(2_999L)
    rule.onNodeWithTag("volume_popup").assertIsDisplayed()
    rule.mainClock.advanceTimeBy(2L)
    rule.onNodeWithTag("volume_popup").assertDoesNotExist()
}

@Test
fun verticalAdjustmentResetsVolumePopupDeadline() {
    rule.mainClock.autoAdvance = false
    var expanded by mutableStateOf(false)
    var state by mutableStateOf(VolumeState(5, 10, false))
    rule.setContent {
        MediaViewerTheme {
            PlaybackVolumeControl(
                state = state,
                expanded = expanded,
                onExpandedChanged = { expanded = it },
                onRefresh = {},
                onToggleMute = {},
                onVolumeChanged = { fraction ->
                    state = state.copy(
                        current = (fraction * state.maximum).roundToInt(),
                    )
                },
            )
        }
    }

    rule.onNodeWithContentDescription("音量，当前 50%，未静音")
        .performClick()
    rule.mainClock.advanceTimeBy(2_000L)
    rule.onNodeWithTag("volume_slider_vertical")
        .performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.8f)
        }
    rule.mainClock.advanceTimeBy(2_999L)
    rule.onNodeWithTag("volume_popup").assertIsDisplayed()
    rule.mainClock.advanceTimeBy(2L)
    rule.onNodeWithTag("volume_popup").assertDoesNotExist()
}

@Test
fun muteAndExternalVolumeChangesEachResetThePopupDeadline() {
    rule.mainClock.autoAdvance = false
    var expanded by mutableStateOf(false)
    var state by mutableStateOf(VolumeState(5, 10, false))
    var muteCalls = 0
    rule.setContent {
        MediaViewerTheme {
            PlaybackVolumeControl(
                state = state,
                expanded = expanded,
                onExpandedChanged = { expanded = it },
                onRefresh = {},
                onToggleMute = {
                    muteCalls += 1
                    state = state.copy(muted = !state.muted)
                },
                onVolumeChanged = {},
            )
        }
    }

    rule.onNodeWithContentDescription("音量，当前 50%，未静音")
        .performClick()
    rule.mainClock.advanceTimeBy(2_000L)
    rule.onNodeWithContentDescription("静音").performClick()
    rule.mainClock.advanceTimeBy(2_999L)
    rule.onNodeWithTag("volume_popup").assertIsDisplayed()
    rule.runOnIdle {
        assertEquals(1, muteCalls)
        state = state.copy(current = 7)
    }
    rule.mainClock.advanceTimeBy(2_999L)
    rule.onNodeWithTag("volume_popup").assertIsDisplayed()
    rule.mainClock.advanceTimeBy(2L)
    rule.onNodeWithTag("volume_popup").assertDoesNotExist()
}

@Test
fun systemBackClosesVolumePopupWithoutLeavingPlayer() {
    var expanded by mutableStateOf(false)
    rule.setContent {
        MediaViewerTheme {
            PlaybackVolumeControl(
                state = VolumeState(5, 10, false),
                expanded = expanded,
                onExpandedChanged = { expanded = it },
                onRefresh = {},
                onToggleMute = {},
                onVolumeChanged = {},
            )
        }
    }
    rule.onNodeWithContentDescription("音量，当前 50%，未静音")
        .performClick()
    Espresso.pressBack()
    rule.onNodeWithTag("volume_popup").assertDoesNotExist()
}

@Test
fun tappingOutsideDismissesVolumePopup() {
    var expanded by mutableStateOf(false)
    rule.setContent {
        MediaViewerTheme {
            PlaybackVolumeControl(
                state = VolumeState(5, 10, false),
                expanded = expanded,
                onExpandedChanged = { expanded = it },
                onRefresh = {},
                onToggleMute = {},
                onVolumeChanged = {},
            )
        }
    }
    rule.onNodeWithContentDescription("音量，当前 50%，未静音")
        .performClick()
    rule.onRoot().performTouchInput { click(Offset(2f, 2f)) }
    rule.onNodeWithTag("volume_popup").assertDoesNotExist()
}
```

Add the required imports:

```kotlin
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import kotlin.math.roundToInt
```

- [ ] **Step 2: Verify the old contract fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.PlaybackVolumeControlTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: timeout constant/helper is absent and the current rotated Slider does not satisfy the new vertical component assertions.

- [ ] **Step 3: Replace the timeline and volume control**

`PlaybackTimeline` calls `MediaTimelineSlider` with `state.displayedPositionMs`; it puts `formatPlaybackTime(current)` at start and total duration at end in stable-width containers. Dragging still calls begin once, preview during motion and commit once. Do not modify `PlayerViewModel`, `SeekSyncState` or actual position sampling.

Create the pure policy in `PlaybackVolumeControl.kt`:

```kotlin
const val VOLUME_POPUP_IDLE_TIMEOUT_MS = 3_000L

internal data class VolumePopupIdlePolicy(
    val lastInteractionMs: Long,
) {
    fun interacted(nowMs: Long) = copy(lastInteractionMs = nowMs)

    fun shouldClose(nowMs: Long): Boolean =
        nowMs - lastInteractionMs >= VOLUME_POPUP_IDLE_TIMEOUT_MS
}
```

`PlaybackVolumeControl` renders:

```text
trigger PlayerIconButton
anchored Popup/DropdownMenu
  current percentage
  MediaVerticalLevelControl (48×160 minimum)
  independent mute toggle
```

Use a remembered `interactionEpoch: Long`. Slider movement and mute increment it
before forwarding their callbacks. A `LaunchedEffect(state.current, state.maximum,
state.muted)` increments it only when an already-expanded popup observes an
external state change. Implement polling and dismissal with structured
concurrency so resetting the epoch cancels both old jobs:

```kotlin
LaunchedEffect(expanded, interactionEpoch) {
    if (!expanded) return@LaunchedEffect
    coroutineScope {
        val polling = launch {
            while (isActive) {
                onRefresh()
                delay(250L)
            }
        }
        delay(VOLUME_POPUP_IDLE_TIMEOUT_MS)
        polling.cancel()
        onExpandedChanged(false)
    }
}
```

The popup root owns `testTag("volume_popup")`; the true vertical control owns
`testTag("volume_slider_vertical")`. `DropdownMenu.onDismissRequest` and system
Back call `onExpandedChanged(false)`.

Set trigger `stateDescription` to the existing accessibility description; mute uses toggle/state description `"已静音"` or `"未静音，音量 N%"`.

- [ ] **Step 4: Theme fullscreen rails**

`VerticalLevelIndicator` consumes `MediaTheme.playerColors.volume` for volume and `.brightness` for brightness. Its parent provides `stateDescription` and decorative child glyphs are removed from the accessibility tree. Preserve the existing 800ms feedback clear in `VideoPlayerScreen`.

- [ ] **Step 5: Run timeline, volume, gesture and seek regressions**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.PlaybackVolumeControlTest' `
  --tests 'com.local.mediaviewer.player.SeekSyncStateTest' `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.VideoGestureLayerTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: single timeline, paused seek behavior, true vertical control, independent mute, timeout and distinct gesture rails PASS.

- [ ] **Step 6: Commit timeline and vertical level unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/player/PlaybackTimeline.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackVolumeControl.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VerticalLevelIndicator.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt `
  app/src/test/java/com/local/mediaviewer/ui/player/PlaybackVolumeControlTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackControlsTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/VideoGestureLayerTest.kt
git commit -m "feat: refine timeline and vertical volume"
```

### Task 4: Fullscreen Safe Controls, Queue Entry, and Gesture Help

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/FullscreenWindowPolicy.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/FullscreenController.kt`
- Create: `app/src/test/java/com/local/mediaviewer/ui/player/FullscreenControllerTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`

**Interfaces:**
- Changes `VideoControlsOverlay` to require `onOpenQueue: () -> Unit`.
- Adds a source-compatible defaulted `safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing`
  parameter to `VideoPlayerScreen` and `VideoControlsOverlay`; tests inject fixed
  insets, while the root call site remains unchanged.
- Keeps existing auto-hide rule: three seconds only while playing/buffering, no menu/scrub, and unlocked.
- Keeps existing gesture reducer and full-screen controller.

- [ ] **Step 1: Add failing queue/help/safe-layout tests**

Add two independent tests to `VideoControlsOverlayTest.kt`; do not combine a
modal-help state with a queue click:

```kotlin
@Test
fun fullscreenQueueEntryInvokesTheRootCallback() {
    var openQueueCalls = 0
    showFullscreen(
        hasShownGestureHint = true,
        onOpenQueue = { openQueueCalls++ },
    )
    rule.onNodeWithContentDescription("打开播放队列").performClick()
    rule.runOnIdle { assertEquals(1, openQueueCalls) }
}

@Test
fun dismissingGestureHelpWithBackDoesNotExitFullscreen() {
    val controller = showFullscreen(hasShownGestureHint = false)
    rule.onNodeWithText("视频手势").assertIsDisplayed()
    Espresso.pressBack()
    rule.onNodeWithText("视频手势").assertDoesNotExist()
    rule.onNodeWithTag("video_controls").assertIsDisplayed()
    rule.runOnIdle { assertEquals(0, controller.exitCalls) }
}
```

Add this compile-complete fixture inside `VideoControlsOverlayTest`; it uses the
existing playback/volume/brightness fakes from that file:

```kotlin
private fun showFullscreen(
    hasShownGestureHint: Boolean,
    onOpenQueue: () -> Unit = {},
    safeDrawingInsets: WindowInsets = WindowInsets(
        left = 0.dp,
        top = 0.dp,
        right = 0.dp,
        bottom = 0.dp,
    ),
    stateProvider: () -> PlayerUiState = {
        PlayerUiState(
            name = "movie.mp4",
            kind = MediaKind.VIDEO,
            status = PlaybackStatus.PAUSED,
            durationMs = 60_000L,
            isSeekable = true,
        )
    },
): OverlayFullscreenController {
    val controller = OverlayFullscreenController(initiallyFullscreen = true)
    rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            MediaViewerTheme {
                VideoPlayerScreen(
                    state = stateProvider(),
                    controller = OverlayPlaybackController(),
                    fullscreenController = controller,
                    preferences = OverlayPreferencesRepository(
                        initiallyShown = hasShownGestureHint,
                    ),
                    volumeController = OverlayVolumeController(),
                    brightnessController = OverlayBrightnessController(),
                    onPlay = {},
                    onPause = {},
                    onReplay = {},
                    onSeekBack = {},
                    onSeekForward = {},
                    onBeginScrub = {},
                    onPreviewScrub = {},
                    onCommitScrub = {},
                    onPrevious = {},
                    onNext = {},
                    onSpeedChanged = {},
                    playbackMode = PlaybackMode.SEQUENTIAL,
                    onPlaybackModeChanged = {},
                    onOpenQueue = onOpenQueue,
                    onRetry = {},
                    onVideoScaleModeChanged = {},
                    onBack = {},
                    safeDrawingInsets = safeDrawingInsets,
                )
            }
        }
    }
    return controller
}

private class OverlayFullscreenController(
    initiallyFullscreen: Boolean,
) : FullscreenStateController {
    private val value = MutableStateFlow(initiallyFullscreen)
    override val isFullscreen: StateFlow<Boolean> = value
    var exitCalls = 0
        private set

    override fun enter() {
        value.value = true
    }

    override fun exit() {
        exitCalls += 1
        value.value = false
    }

    override fun close() = Unit
}

private class OverlayPreferencesRepository(
    initiallyShown: Boolean,
) : PlayerPreferencesRepository {
    private val value = MutableStateFlow(initiallyShown)
    override val hasShownVideoGestures: StateFlow<Boolean> = value

    override suspend fun markVideoGesturesShown() {
        value.value = true
    }
}
```

Add deterministic safe-inset, center-position, and Back-priority tests:

```kotlin
@Test
fun fullscreenControlsStayInsideInjectedSafeDrawingInsets() {
    showFullscreen(
        hasShownGestureHint = true,
        safeDrawingInsets = WindowInsets(
            left = 16.dp,
            top = 24.dp,
            right = 16.dp,
            bottom = 32.dp,
        ),
    )
    val root = rule.onNodeWithTag("fullscreen_root")
        .fetchSemanticsNode().boundsInRoot
    val top = rule.onNodeWithTag("fullscreen_top_controls")
        .fetchSemanticsNode().boundsInRoot
    val bottom = rule.onNodeWithTag("fullscreen_bottom_controls")
        .fetchSemanticsNode().boundsInRoot
    assertTrue(top.top >= root.top + 24f)
    assertTrue(bottom.bottom <= root.bottom - 32f)
}

@Test
fun bufferingDoesNotMoveTheCenterTransportGroup() {
    var status by mutableStateOf(PlaybackStatus.PLAYING)
    showFullscreen(
        hasShownGestureHint = true,
        stateProvider = {
            PlayerUiState(
                name = "movie.mp4",
                kind = MediaKind.VIDEO,
                status = status,
                durationMs = 60_000L,
                isSeekable = true,
            )
        },
    )
    val playingCenter = rule.onNodeWithTag("fullscreen_center_controls")
        .fetchSemanticsNode().boundsInRoot.center
    rule.runOnIdle { status = PlaybackStatus.BUFFERING }
    val bufferingCenter = rule.onNodeWithTag("fullscreen_center_controls")
        .fetchSemanticsNode().boundsInRoot.center
    assertEquals(playingCenter.y, bufferingCenter.y, 0.5f)
}

@Test
fun backClosesVolumeBeforeExitingFullscreen() {
    val controller = showFullscreen(hasShownGestureHint = true)
    rule.onNodeWithContentDescription("音量，当前 50%，未静音")
        .performClick()
    Espresso.pressBack()
    rule.onNodeWithTag("volume_popup").assertDoesNotExist()
    rule.runOnIdle { assertEquals(0, controller.exitCalls) }
    Espresso.pressBack()
    rule.runOnIdle { assertEquals(1, controller.exitCalls) }
}
```

Required imports are:

```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.local.mediaviewer.queue.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
```

- [ ] **Step 2: Run focused connected tests and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.FullscreenControllerTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlayerScreenTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: queue is disabled with “即将支持”, and dismissing the help through the dialog's dismissal path exits fullscreen.

- [ ] **Step 3: Replace opaque bars with safe gradient slots**

Top control layer:

- `WindowInsets.safeDrawing.only(Top + Horizontal)` padding;
- gradient `#B3000000 -> transparent`;
- Back, one-line ellipsized title and active queue `PlayerIconButton` with
  `Modifier.testTag("queue_entry_fullscreen")`.
- root/top/bottom/center layers expose `fullscreen_root`,
  `fullscreen_top_controls`, `fullscreen_bottom_controls`, and
  `fullscreen_center_controls` respectively.

Bottom layer:

- gradient `transparent -> #CC000000`;
- horizontal/safe-bottom padding;
- timeline, previous/next and responsive low-frequency options.

Center layer owns seek-back, primary action, seek-forward and a separate buffering ring. Remove the `offset(-72.dp)` branch entirely.

Lock button exposes toggle/state description `"控制已锁定"`/`"控制未锁定"`; locked mode disables progress, volume and brightness gestures and leaves one 48dp unlock action.

Make window effects independently testable without an Android fake. Create
`FullscreenWindowPolicy.kt`:

```kotlin
internal fun interface FullscreenWindowPolicy {
    fun apply(
        fullscreen: Boolean,
        decorFitsSystemWindows: Boolean,
    )
}

internal class AndroidFullscreenWindowPolicy(
    private val activity: Activity,
) : FullscreenWindowPolicy {
    override fun apply(
        fullscreen: Boolean,
        decorFitsSystemWindows: Boolean,
    ) {
        WindowCompat.setDecorFitsSystemWindows(
            activity.window,
            decorFitsSystemWindows,
        )
        val bars = WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        )
        if (fullscreen) {
            activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
```

`FullscreenController` keeps its public `Activity` constructor and adds an
internal policy constructor:

```kotlin
class FullscreenController internal constructor(
    private val windowPolicy: FullscreenWindowPolicy,
) : FullscreenStateController {
    constructor(activity: Activity) : this(
        AndroidFullscreenWindowPolicy(activity),
    )

    private val mutableFullscreen = MutableStateFlow(false)
    override val isFullscreen = mutableFullscreen.asStateFlow()

    override fun enter() {
        windowPolicy.apply(
            fullscreen = true,
            decorFitsSystemWindows = false,
        )
        mutableFullscreen.value = true
    }

    override fun exit() {
        windowPolicy.apply(
            fullscreen = false,
            decorFitsSystemWindows = false,
        )
        mutableFullscreen.value = false
    }

    override fun close() {
        if (mutableFullscreen.value) exit()
    }
}
```

Create `FullscreenControllerTest.kt`:

```kotlin
class FullscreenControllerTest {
    @Test
    fun `exit shows normal orientation but keeps edge to edge`() {
        val calls = mutableListOf<Pair<Boolean, Boolean>>()
        val controller = FullscreenController(
            FullscreenWindowPolicy { fullscreen, decorFits ->
                calls += fullscreen to decorFits
            },
        )
        controller.enter()
        controller.exit()

        assertEquals(
            listOf(true to false, false to false),
            calls,
        )
        assertFalse(controller.isFullscreen.value)
    }
}
```

- [ ] **Step 4: Fix help dismissal and return priority**

Use one `dismissGestureHint()` lambda for both `AlertDialog.onDismissRequest`
and `"知道了"`; it sets `gestureHintDismissed = true` and launches
`preferences.markVideoGesturesShown()`, and never calls
`fullscreenController.exit()`. The explicit top Back and system Back close, in
order, the gesture dialog, volume popup, option menu, and then fullscreen.
The root-owned queue Sheet consumes Back before the Player route sees it. Pass
`onOpenQueue` through `VideoPlayerScreen` to `VideoControlsOverlay`.

- [ ] **Step 5: Run fullscreen and gesture regressions**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.VideoGestureLayerTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: queue callback, help dismissal, safe controls, lock semantics, auto-hide and left/right gesture behavior PASS.

- [ ] **Step 6: Commit fullscreen visual unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/player/VideoControlsOverlay.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlayerGestureFeedback.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/FullscreenWindowPolicy.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/FullscreenController.kt `
  app/src/test/java/com/local/mediaviewer/ui/player/FullscreenControllerTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/VideoControlsOverlayTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt
git commit -m "fix: complete fullscreen player controls"
```

### Task 5: Responsive Mini Player Dock

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`
- Root integration only by flow Task 7 owner: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces:**
- Consumes the existing `PlaybackSessionState.canSkipNext`,
  `playbackPrimaryAction`, `MediaTheme.sizing.miniPlayerHeight`, and the
  foundation root bottom-bar slot.
- Uses the preferred Task 1 `NowPlayingBar(state, onPlay, onPause, onReplay,
  onNext, onOpenQueue, onOpenPlayer, modifier)` overload.
- Does not render or consume volume. The deprecated Task 1 overload retains old
  volume parameters only for source compatibility with the unchanged root and
  discards them; flow Task 7 switches to the preferred overload.

- [ ] **Step 1: Add failing mini-player state and responsive tests**

Update `PlaybackQueueUiTest.kt`:

```kotlin
@Test
fun miniPlayerShowsRealProgressAndDisablesUnavailableNext() {
    val current = item("video", "movie.mp4", MediaKind.VIDEO)
    rule.setContent {
        MediaViewerTheme {
            NowPlayingBar(
                state = PlaybackSessionState(
                    playback = PlaybackState(
                        status = PlaybackStatus.BUFFERING,
                        positionMs = 25_000L,
                        durationMs = 100_000L,
                    ),
                    queue = PlaybackQueue(
                        items = listOf(current),
                        currentMediaKey = current.mediaKey,
                    ),
                    currentItem = current,
                    canSkipNext = false,
                ),
                onPlay = {},
                onPause = {},
                onReplay = {},
                onNext = {},
                onOpenQueue = {},
                onOpenPlayer = {},
            )
        }
    }
    rule.onNodeWithTag("mini_player_progress").assertIsDisplayed()
    rule.onNodeWithContentDescription("暂停")
        .assertIsEnabled()
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "正在缓冲，可暂停",
            ),
        )
    rule.onNodeWithContentDescription("下一项").assertIsNotEnabled()
    rule.onNodeWithContentDescription("调节音量").assertDoesNotExist()
}

@Test
fun compactMiniKeepsPrimaryAndQueueWithoutNextOrVolume() {
    val current = item("audio", "long song name.flac", MediaKind.AUDIO)
    rule.setContent {
        CompositionLocalProvider(
            LocalDensity provides Density(density = 1f, fontScale = 2f),
        ) {
            MediaViewerTheme {
                Box(Modifier.width(320.dp)) {
                    NowPlayingBar(
                        state = PlaybackSessionState(
                            playback = PlaybackState(
                                status = PlaybackStatus.PAUSED,
                                positionMs = 10_000L,
                                durationMs = 100_000L,
                            ),
                            queue = PlaybackQueue(
                                items = listOf(
                                    current,
                                    item("next", "next.flac", MediaKind.AUDIO),
                                ),
                                currentMediaKey = current.mediaKey,
                            ),
                            currentItem = current,
                            canSkipNext = true,
                        ),
                        onPlay = {},
                        onPause = {},
                        onReplay = {},
                        onNext = {},
                        onOpenQueue = {},
                        onOpenPlayer = {},
                    )
                }
            }
        }
    }
    rule.onNodeWithContentDescription("播放").assertIsDisplayed()
    rule.onNodeWithTag("queue_entry_mini").assertIsDisplayed()
    rule.onNodeWithContentDescription("下一项").assertDoesNotExist()
    rule.onNodeWithContentDescription("调节音量").assertDoesNotExist()
}
```

Use the exact `item(key, name, kind)` helper defined in Task 1 and add:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
```

- [ ] **Step 2: Run the mini-player test and verify RED**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackQueueUiTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: current Buffering uses the wrong play icon, no progress tag exists, and Next is always enabled.

- [ ] **Step 3: Implement the responsive dock**

Use one 72dp `Surface` with `surface3` tonal elevation. At the start show a 40dp media glyph, then one-line title and metadata. At the end:

- normal width: primary action, Next, Queue;
- width below 360dp or font scale 2.0: primary action and Queue only.

Put a 2dp actual-position `LinearProgressIndicator` at the top with tag
`mini_player_progress`; this is played position, not a buffer/loading bar.
Queue-empty state returns before rendering. `canSkipNext` controls disabled
semantics. The queue action has `testTag("queue_entry_mini")` and content
description `"打开播放队列"`. No width renders a volume trigger; the complete
player remains the volume owner.

The root owner places this exact component in `MediaAppScaffold.bottomBar`; no absolute overlay remains.

- [ ] **Step 4: Run the mini-player component gate only**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackQueueUiTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: Buffering action, real replay callback, no-next disabled semantics,
320dp/2.0-font responsive actions, real progress, and absence of mini volume
PASS. Browser/Home tail visibility remains RED/deferred until flow Task 7
installs `NowPlayingBar` in `MediaAppScaffold.bottomBar`; it is verified only in
this plan Task 7 and foundation Task 8.

- [ ] **Step 5: Commit mini-player implementation**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/player/NowPlayingBar.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt
git commit -m "feat: add responsive mini player dock"
```

The root integration change is committed with flow plan Task 7, not this parallel commit.

### Task 6: Adaptive Queue and Cross-Item Drag

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/QueueDragSession.kt`
- Create: `app/src/test/java/com/local/mediaviewer/ui/player/QueueDragSessionTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`
- Root undo integration only by flow Task 7 owner: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces:**
- Produces: `QueueDragSession(mediaKey, startIndex, currentIndex, totalDisplacementPx, residualPx)`,
  `advance(deltaPx, rowExtentPx, lastIndex)`, and `finish(): QueueDrop?`.
- Keeps the current required `PlaybackQueueSheet.onRemove: (String) -> Unit` so
  the unchanged `MediaViewerApp.kt` compiles through Tasks 1–6.
- Adds optional
  `onRemoveRequest: ((QueueMediaItem, originalIndex: Int) -> Unit)? = null`.
  Ordinary pending-item deletion uses it when present and falls back to
  `onRemove(mediaKey)`; current-item confirmation always uses the legacy
  `onRemove(mediaKey)` path and never offers undo.
- Adds the source-compatible test seam
  `navigationBarsInsets: WindowInsets = WindowInsets.navigationBars`.
- Keeps `onMove(mediaKey, toIndex)` and all queue business callbacks.
- Depends on flow Task 6 persistence notices being available globally; it does not map persistence failure to playback error.

The resulting public signature keeps every current required parameter in its
original order and appends only defaulted parameters:

```kotlin
@Composable
fun PlaybackQueueSheet(
    queue: PlaybackQueue,
    onSelect: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onClearExceptCurrent: () -> Unit,
    onStopAndClear: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveRequest: ((QueueMediaItem, Int) -> Unit)? = null,
    navigationBarsInsets: WindowInsets = WindowInsets.navigationBars,
)
```

- [ ] **Step 1: Write the failing pure drag-session test**

Create:

```kotlin
class QueueDragSessionTest {
    @Test
    fun `one gesture crosses several rows and preserves residual movement`() {
        val start = QueueDragSession(
            mediaKey = "c",
            startIndex = 2,
        )
        val update = start.advance(
            deltaPx = 170f,
            rowExtentPx = 60f,
            lastIndex = 6,
        )

        assertEquals(listOf(3, 4), update.crossedIndices)
        assertEquals(4, update.session.currentIndex)
        assertEquals(50f, update.session.residualPx, 0.001f)
        assertEquals(QueueDrop("c", 4), update.session.finish())
    }

    @Test
    fun `drag clamps to list bounds`() {
        val update = QueueDragSession("a", startIndex = 0)
            .advance(-180f, rowExtentPx = 60f, lastIndex = 4)
        assertTrue(update.crossedIndices.isEmpty())
        assertEquals(0, update.session.currentIndex)
        assertNull(update.session.finish())
    }

    @Test
    fun `reversing after overshoot crosses back to the net target`() {
        val down = QueueDragSession("c", startIndex = 2)
            .advance(170f, rowExtentPx = 60f, lastIndex = 6)
        val reversed = down.session
            .advance(-60f, rowExtentPx = 60f, lastIndex = 6)

        assertEquals(listOf(3), reversed.crossedIndices)
        assertEquals(3, reversed.session.currentIndex)
        assertEquals(110f, reversed.session.totalDisplacementPx, 0.001f)
        assertEquals(50f, reversed.session.residualPx, 0.001f)
        assertEquals(QueueDrop("c", 3), reversed.session.finish())
    }
}
```

- [ ] **Step 2: Run the pure test and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.QueueDragSessionTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: FAIL because the session types do not exist.

- [ ] **Step 3: Implement deterministic multi-row accumulation**

```kotlin
data class QueueDragUpdate(
    val session: QueueDragSession,
    val crossedIndices: List<Int>,
)

data class QueueDrop(
    val mediaKey: String,
    val toIndex: Int,
)

data class QueueDragSession(
    val mediaKey: String,
    val startIndex: Int,
    val currentIndex: Int = startIndex,
    val totalDisplacementPx: Float = 0f,
    val residualPx: Float = 0f,
) {
    fun advance(
        deltaPx: Float,
        rowExtentPx: Float,
        lastIndex: Int,
    ): QueueDragUpdate {
        require(rowExtentPx > 0f)
        require(lastIndex >= 0)
        val minimumDisplacement = -startIndex * rowExtentPx
        val maximumDisplacement = (lastIndex - startIndex) * rowExtentPx
        val total = (totalDisplacementPx + deltaPx)
            .coerceIn(minimumDisplacement, maximumDisplacement)
        val offsetFromStart = (total / rowExtentPx).toInt()
        val index = (startIndex + offsetFromStart)
            .coerceIn(0, lastIndex)
        val residual = total - (index - startIndex) * rowExtentPx
        val crossedIndices = when {
            index > currentIndex -> (currentIndex + 1..index).toList()
            index < currentIndex -> (currentIndex - 1 downTo index).toList()
            else -> emptyList()
        }
        return QueueDragUpdate(
            copy(
                currentIndex = index,
                totalDisplacementPx = total,
                residualPx = residual,
            ),
            crossedIndices,
        )
    }

    fun finish(): QueueDrop? =
        if (currentIndex == startIndex) {
            null
        } else {
            QueueDrop(mediaKey, currentIndex)
        }
}
```

- [ ] **Step 4: Add executable queue layout, drag, semantic, and removal tests**

Replace the old whole-row drag test in `PlaybackQueueUiTest.kt` with:

```kotlin
@Test
fun handleDragAcrossThreeRowsCommitsOneFinalMoveAndNeverSelects() {
    val moves = mutableListOf<Pair<String, Int>>()
    var selected: String? = null
    val queue = PlaybackQueue(
        items = (0..5).map { index ->
            item("$index", "第${index + 1}首")
        },
        currentMediaKey = "0",
    )
    showQueue(
        queue = queue,
        onSelect = { selected = it },
        onMove = { key, index -> moves += key to index },
    )

    rule.onNodeWithContentDescription("拖动排序 第一首")
        .performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            moveBy(Offset(0f, height * 3.2f), delayMillis = 300L)
            up()
        }

    rule.runOnIdle {
        assertEquals(listOf("0" to 3), moves)
        assertNull(selected)
    }
}

@Test
fun handleOvershootThenReverseCommitsTheNetIndexExactlyOnce() {
    val moves = mutableListOf<Pair<String, Int>>()
    val queue = PlaybackQueue(
        items = (0..5).map { index ->
            item("$index", "第${index + 1}首")
        },
        currentMediaKey = "0",
    )
    showQueue(
        queue = queue,
        onMove = { key, index -> moves += key to index },
    )
    val rowExtent = rule.onNodeWithTag("queue_row:2")
        .fetchSemanticsNode().boundsInRoot.height

    rule.onNodeWithContentDescription("拖动排序 第三首")
        .performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            moveBy(
                Offset(0f, rowExtent * 2.8f),
                delayMillis = 200L,
            )
            moveBy(
                Offset(0f, -rowExtent),
                delayMillis = 200L,
            )
            up()
        }

    rule.runOnIdle {
        assertEquals(listOf("2" to 3), moves)
    }
}
```

Add adaptive-height/inset and auto-scroll tests:

```kotlin
@Test
fun longQueueUsesAvailableHeightAndStaysAboveInjectedNavigationInset() {
    val queue = PlaybackQueue(
        items = (0 until 30).map { item("$it", "第${it + 1}首") },
        currentMediaKey = "0",
    )
    rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            MediaViewerTheme {
                Box(Modifier.height(600.dp).testTag("queue_test_root")) {
                    PlaybackQueueSheet(
                        queue = queue,
                        onSelect = {},
                        onMove = { _, _ -> },
                        onRemove = {},
                        onRemoveRequest = { _, _ -> },
                        onClearExceptCurrent = {},
                        onStopAndClear = {},
                        onDismiss = {},
                        navigationBarsInsets = WindowInsets(bottom = 32.dp),
                    )
                }
            }
        }
    }
    val root = rule.onNodeWithTag("queue_test_root")
        .fetchSemanticsNode().boundsInRoot
    val sheet = rule.onNodeWithTag("queue_sheet")
        .fetchSemanticsNode().boundsInRoot
    val list = rule.onNodeWithTag("queue_list")
        .fetchSemanticsNode().boundsInRoot
    assertTrue(sheet.height > 480f)
    assertTrue(sheet.height <= root.height * 0.9f + 1f)
    assertTrue(list.bottom <= root.bottom - 32f)
}

@Test
fun draggingNearViewportEdgeAutoScrollsButStillCommitsOnce() {
    val moves = mutableListOf<Pair<String, Int>>()
    val queue = PlaybackQueue(
        items = (0 until 40).map { item("$it", "第${it + 1}首") },
        currentMediaKey = "0",
    )
    showQueue(queue, onMove = { key, index -> moves += key to index })
    rule.onNodeWithContentDescription("拖动排序 第一首")
        .performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            repeat(12) {
                moveTo(
                    Offset(center.x, height + 420f),
                    delayMillis = 100L,
                )
            }
            up()
        }
    rule.onNodeWithText("第10首").assertIsDisplayed()
    rule.runOnIdle {
        assertEquals(1, moves.size)
        assertTrue(moves.single().second >= 8)
    }
}
```

Add semantics, empty, and deletion tests:

```kotlin
@Test
fun queueStatesCustomActionsAndRemovalContractsAreExplicit() {
    val current = item("a", "第一首")
    val next = item("b", "第二首")
    val later = item("c", "第三首")
    val fourth = item("d", "第四首")
    val accessibilityMoves = mutableListOf<Pair<String, Int>>()
    var ordinaryRemoval: Pair<QueueMediaItem, Int>? = null
    var currentRemoval: String? = null
    showQueue(
        queue = PlaybackQueue(
            items = listOf(current, next, later, fourth),
            currentMediaKey = current.mediaKey,
        ),
        onMove = { key, index -> accessibilityMoves += key to index },
        onRemove = { currentRemoval = it },
        onRemoveRequest = { item, index ->
            ordinaryRemoval = item to index
        },
    )

    rule.onNodeWithContentDescription("队列项 第一首，正在播放")
        .assertIsSelected()
    rule.onNodeWithContentDescription("队列项 第二首，即将播放")
        .assertIsDisplayed()
    val actions = rule.onNodeWithContentDescription(
        "队列项 第三首",
    ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
    assertEquals(listOf("上移", "下移", "删除"), actions.map { it.label })
    actions.first { it.label == "上移" }.action()
    rule.runOnIdle {
        assertEquals(listOf("c" to 1), accessibilityMoves)
    }
    rule.onNodeWithTag("queue_move_announcement")
        .assertTextEquals("已移动到第 2 项")

    rule.onNodeWithContentDescription("删除 第三首").performClick()
    rule.runOnIdle {
        assertEquals(later to 2, ordinaryRemoval)
        assertNull(currentRemoval)
    }
    rule.onNodeWithContentDescription("删除 第一首").performClick()
    rule.onNodeWithText("删除正在播放的项目？").assertIsDisplayed()
    rule.onNodeWithText("删除").performClick()
    rule.runOnIdle { assertEquals("a", currentRemoval) }
}

@Test
fun oneItemDisablesClearOtherAndEmptyQueueHasAState() {
    showQueue(
        PlaybackQueue(
            items = listOf(item("a", "第一首")),
            currentMediaKey = "a",
        ),
    )
    rule.onNodeWithText("清空其他").assertIsNotEnabled()
}

@Test
fun emptyQueueShowsExplicitStateAndCanClose() {
    var dismissCalls = 0
    showQueue(
        queue = PlaybackQueue(),
        onDismiss = { dismissCalls++ },
    )
    rule.onNodeWithText("播放队列为空").assertIsDisplayed()
    rule.onNodeWithContentDescription("关闭播放队列").performClick()
    rule.runOnIdle { assertEquals(1, dismissCalls) }
}
```

Use this exact helper inside the test class; it includes both the legacy and
new removal paths so the old root remains compilable:

```kotlin
private fun showQueue(
    queue: PlaybackQueue,
    onSelect: (String) -> Unit = {},
    onMove: (String, Int) -> Unit = { _, _ -> },
    onRemove: (String) -> Unit = {},
    onRemoveRequest: ((QueueMediaItem, Int) -> Unit)? = null,
    onDismiss: () -> Unit = {},
) {
    rule.setContent {
        MediaViewerTheme {
            PlaybackQueueSheet(
                queue = queue,
                onSelect = onSelect,
                onMove = onMove,
                onRemove = onRemove,
                onRemoveRequest = onRemoveRequest,
                onClearExceptCurrent = {},
                onStopAndClear = {},
                onDismiss = onDismiss,
            )
        }
    }
}
```

Required imports:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNull
```

- [ ] **Step 5: Rebuild the adaptive queue**

Use `MediaBottomSheet` with at most 90% available height and one `LazyColumn`
consuming `navigationBarsInsets`. The sheet and list expose `queue_sheet` and
`queue_list`. Header keeps title, count and mode; “清空其他” moves into the more
menu and is disabled when only current remains.

Each 64dp row has:

- stable `Modifier.testTag("queue_row:${item.mediaKey}")` on the row root;
- current surface/selected semantics and playing glyph;
- next label/state description;
- title/metadata;
- visible 48dp drag handle;
- delete action.

Attach long-press drag only to the handle. Keep a remembered
`QueueDragSession` by stable `mediaKey` plus a local preview list. Every
`crossedIndices` result reorders only that preview list; it must not call the
controller. Near the first/last visible row, call `LazyListState.scrollBy` so a
long queue can continue moving. Apply 1.02 scale, surface4 elevation and one
haptic feedback at drag start; animate back in 180ms.

The gesture terminal paths are exact:

```kotlin
onDragEnd = {
    dragSession?.finish()?.let { drop ->
        onMove(drop.mediaKey, drop.toIndex)
    }
    dragSession = null
    previewItems = queue.items
}
onDragCancel = {
    dragSession = null
    previewItems = queue.items
}
```

Thus one completed drag calls `onMove` zero or one time, and cancellation never
persists. TalkBack “上移/下移” remains one immediate `onMove` per accessibility
action and sets a polite live-region text `"已移动到第 ${target + 1} 项"` with tag
`queue_move_announcement`.

- [ ] **Step 6: Specify root undo without adding queue APIs**

The flow Task 7 root owner supplies both `onRemove =
controller::remove` (current-item confirmation/fallback) and
`onRemoveRequest = { item, index -> ... }` for an ordinary pending item:

```kotlin
controller.remove(item.mediaKey)
scope.launch {
    val result = globalSnackbarHostState.showSnackbar(
        MediaSnackbarVisuals(
            message = "已从队列删除 ${item.name}",
            kind = MediaSnackbarKind.SUCCESS,
            actionLabel = "撤销",
            withDismissAction = true,
        ),
    )
    if (result == SnackbarResult.ActionPerformed) {
        controller.append(item)
        controller.move(item.mediaKey, index)
    }
}
```

The two existing controller commands execute in order; do not add a second queue repository or copy queue state into Compose. Current-item and clear-all confirmation paths do not offer undo.

- [ ] **Step 7: Run queue unit, UI and persistence regressions**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.QueueDragSessionTest' `
  --tests 'com.local.mediaviewer.queue.QueueNavigatorTest' `
  --tests 'com.local.mediaviewer.queue.PlaybackQueueDaoTest' `
  --tests 'com.local.mediaviewer.queue.PlaybackCoordinatorTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackQueueUiTest,com.local.mediaviewer.MediaSessionControlsTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: pure queue persistence regressions and component-level multi-row
drag, one-drop callback, auto-scroll, accessibility announcement, adaptive
insets, and both removal contracts PASS. Root undo and global persistence
Snackbar remain deferred to flow Task 7/player Task 7.

- [ ] **Step 8: Commit queue visual/drag unit**

```powershell
git add app/src/main/java/com/local/mediaviewer/ui/player/QueueDragSession.kt `
  app/src/main/java/com/local/mediaviewer/ui/player/PlaybackQueueSheet.kt `
  app/src/test/java/com/local/mediaviewer/ui/player/QueueDragSessionTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt
git commit -m "feat: add adaptive draggable playback queue"
```

Root callback/undo integration is committed only by flow plan Task 7 owner.

### Task 7: Player Integration and Verification

**Files:**
- Integration modify only by flow Task 7 owner: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Test/harness modify only by that same flow Task 7 owner:
  `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`,
  `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`, and
  `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`
- Create: `docs/verification/2026-07-31-media-player-queue-ui.md`

**Interfaces:**
- Consumes: Tasks 1–6, foundation root scaffold, flow Tasks 2/6, and the
  preferred volume-free `NowPlayingBar` overload.
- Produces: ordinary/fullscreen/mini queue access through one root queue Sheet.

- [ ] **Step 1: Give the sole flow Task 7 owner exact fake hooks**

Steps 1–3 are a handoff executed inside flow Task 7 by its one owner, before
that owner edits `MediaViewerApp.kt`. The later player Task 7 verifier begins at
Step 4 and does not reopen root/test ownership.

Add `initialHasShownVideoGestures: Boolean = false` to `FakeAppContainer` and
construct the preference fake with it:

```kotlin
class FakeAppContainer(
    context: Context,
    initialReaderMode: ImageReaderMode = ImageReaderMode.COMIC,
    directoryContent: DirectoryContent = defaultDirectoryContent(),
    initialHasShownVideoGestures: Boolean = false,
) : AppContainer, AutoCloseable {
    // existing members stay unchanged
    override val playerPreferencesRepository: PlayerPreferencesRepository =
        FakePlayerPreferencesRepository(initialHasShownVideoGestures)
}

private class FakePlayerPreferencesRepository(
    initiallyShown: Boolean,
) : PlayerPreferencesRepository {
    private val mutable = MutableStateFlow(initiallyShown)
    override val hasShownVideoGestures: Flow<Boolean> = mutable

    override suspend fun markVideoGesturesShown() {
        mutable.value = true
    }
}
```

In `FakeQueuePlaybackController`, keep the flow plan's
`MutableSharedFlow<PlaybackNotice>(extraBufferCapacity = 4)`,
`reconnectCalls`, `retryPersistenceCalls`, and `emitNotice`. Also make the fake
queue commands observable and stateful so the root tests assert behavior rather
than callback reachability:

```kotlin
val appendCalls = mutableListOf<QueueMediaItem>()
val moveCalls = mutableListOf<Pair<String, Int>>()
val removeCalls = mutableListOf<String>()
var retryPersistenceCalls = 0
    private set

override fun append(item: QueueMediaItem) {
    appendCalls += item
    val queue = mutableSession.value.queue
    updateQueue(
        queue.items.filterNot { it.mediaKey == item.mediaKey } + item,
        queue.currentMediaKey,
    )
}

override fun move(mediaKey: String, toIndex: Int) {
    moveCalls += mediaKey to toIndex
    val queue = mutableSession.value.queue
    val from = queue.items.indexOfFirst { it.mediaKey == mediaKey }
    if (from < 0) return
    val reordered = queue.items.toMutableList()
    val item = reordered.removeAt(from)
    reordered.add(toIndex.coerceIn(0, reordered.size), item)
    updateQueue(reordered, queue.currentMediaKey)
}

override fun remove(mediaKey: String) {
    removeCalls += mediaKey
    val queue = mutableSession.value.queue
    val removedIndex = queue.items.indexOfFirst { it.mediaKey == mediaKey }
    if (removedIndex < 0) return
    val remaining = queue.items.filterNot { it.mediaKey == mediaKey }
    val nextCurrent = if (queue.currentMediaKey == mediaKey) {
        remaining.getOrNull(removedIndex.coerceAtMost(remaining.lastIndex))
            ?.mediaKey
    } else {
        queue.currentMediaKey
    }
    updateQueue(remaining, nextCurrent)
}

override fun retryPersistence() {
    retryPersistenceCalls += 1
}

fun emitNotice(notice: PlaybackNotice) {
    check(mutableNotices.tryEmit(notice))
}
```

- [ ] **Step 2: Add executable root tests in their real navigation order**

Add these members to `PlaybackQueueUiTest`; each test calls `setContent` exactly
once and every created container is closed:

```kotlin
private val appContainers = mutableListOf<FakeAppContainer>()

@After
fun closeAppContainers() {
    appContainers.forEach(FakeAppContainer::close)
    appContainers.clear()
}

private fun rootItems() = listOf(
    item("video", "movie.mp4", MediaKind.VIDEO),
    item("second", "第二首.mp3", MediaKind.AUDIO),
    item("third", "第三首.mp3", MediaKind.AUDIO),
)

private fun launchRootQueueApp(
    items: List<QueueMediaItem> = rootItems(),
): FakeAppContainer {
    val container = FakeAppContainer(
        context = ApplicationProvider.getApplicationContext(),
        initialHasShownVideoGestures = true,
    )
    appContainers += container
    container.fakePlaybackController.replaceQueue(
        items = items,
        startMediaKey = items.first().mediaKey,
    )
    rule.setContent { MediaViewerApp(container) }
    rule.onNodeWithTag("queue_entry_mini").assertIsDisplayed()
    return container
}

@Test
fun miniThenOrdinaryThenFullscreenOpenTheSameRootQueueSheet() {
    launchRootQueueApp()

    rule.onNodeWithTag("queue_entry_mini").performClick()
    rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
    rule.onNodeWithContentDescription("关闭播放队列").performClick()

    rule.onNodeWithContentDescription("打开播放器：movie.mp4").performClick()
    rule.onNodeWithTag("queue_entry_ordinary").performClick()
    rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
    rule.onNodeWithContentDescription("关闭播放队列").performClick()

    rule.onNodeWithContentDescription("全屏").performClick()
    rule.onNodeWithTag("queue_entry_fullscreen").performClick()
    rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
    rule.onNodeWithContentDescription("关闭播放队列").performClick()
}

@Test
fun ordinaryDeleteUndoRestoresTheOriginalItemAndIndex() {
    val container = launchRootQueueApp()
    rule.onNodeWithTag("queue_entry_mini").performClick()
    rule.onNodeWithContentDescription("删除 第二首.mp3").performClick()
    rule.onNodeWithText("已从队列删除 第二首.mp3").assertIsDisplayed()
    rule.onNodeWithText("撤销").performClick()

    rule.waitUntil(5_000) {
        container.fakePlaybackController.sessionState.value.queue.items
            .map(QueueMediaItem::mediaKey) ==
            listOf("video", "second", "third")
    }
    assertEquals(
        listOf("second"),
        container.fakePlaybackController.removeCalls,
    )
    assertEquals(
        listOf("second"),
        container.fakePlaybackController.appendCalls.map { it.mediaKey },
    )
    assertEquals(
        listOf("second" to 1),
        container.fakePlaybackController.moveCalls,
    )
}

@Test
fun persistenceNoticeRetryKeepsTheRootQueueSheetOpenAndDeduplicatesId() {
    val container = launchRootQueueApp()
    rule.onNodeWithTag("queue_entry_mini").performClick()
    val notice = PlaybackNotice(
        id = 9L,
        kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
        message = "播放队列保存失败",
        action = PlaybackNoticeAction.RETRY_PERSISTENCE,
    )
    container.fakePlaybackController.emitNotice(notice)
    container.fakePlaybackController.emitNotice(notice)

    rule.onNodeWithText("播放队列保存失败").assertIsDisplayed()
    rule.onNodeWithText("重试").performClick()
    rule.runOnIdle {
        assertEquals(
            1,
            container.fakePlaybackController.retryPersistenceCalls,
        )
    }
    rule.onNodeWithText("播放队列 · 3 项").assertIsDisplayed()
}
```

Add imports for `ApplicationProvider`, `MediaViewerApp`, `FakeAppContainer`,
`PlaybackNotice`, `PlaybackNoticeAction`, `PlaybackNoticeKind`, and `After`.
Task 2 owns `queue_entry_ordinary`, Task 4 owns
`queue_entry_fullscreen`, and Task 5 owns `queue_entry_mini`; no test invents a
tag during root integration. `MediaViewerNavigationTest` remains owned by this
same flow task for its navigation/tail tests and reuses its one existing
`setContent`.

- [ ] **Step 3: Verify RED, then let the single root owner wire all entries**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackQueueUiTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected before the root edit: AndroidTest sources compile; the real sequence
fails because the three root entries/undo are not all wired. In the flow Task 7
root integration:

- one remembered `queueSheetVisible` controls the shared Sheet;
- ordinary audio/video, fullscreen overlay and mini dock all call `{ queueSheetVisible = true }`;
- `NowPlayingBar` lives in `MediaAppScaffold.bottomBar` and uses the preferred
  overload with `onPlay = playbackController::play`,
  `onPause = playbackController::pause`, and
  `onReplay = { playbackController.seekTo(0L); playbackController.play() }`;
- `PlaybackQueueSheet` receives both
  `onRemove = playbackController::remove` for the compatibility/current-item
  path and Task 6's `onRemoveRequest = { item, originalIndex -> ... }` undo
  handler; ordinary removal calls `remove`, Snackbar action calls `append`
  followed by `move(item.mediaKey, originalIndex)`;
- flow Task 6 `PlaybackNotice` is collected once into `globalSnackbarHostState`;
- queue empty closes the Sheet and hides the mini dock;
- no player component creates a second SnackbarHost or queue copy.

Flow Task 7 commits these root, fake, and test changes in its one integration
commit. Only then does this player Task 7 verifier continue.

- [ ] **Step 4: Run complete player/queue gates after root integration**

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.ui.player.*' `
  --tests 'com.local.mediaviewer.player.*' `
  --tests 'com.local.mediaviewer.queue.*' `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  '-Pkotlin.incremental=false' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest,com.local.mediaviewer.PlaybackControlsTest,com.local.mediaviewer.VideoControlsOverlayTest,com.local.mediaviewer.VideoGestureLayerTest,com.local.mediaviewer.PlaybackQueueUiTest,com.local.mediaviewer.MediaViewerNavigationTest,com.local.mediaviewer.BackgroundPlaybackTest,com.local.mediaviewer.LibVlcVideoOutputTest' `
  '-Pkotlin.incremental=false' `
  --no-daemon
```

Expected: local suites and available connected classes PASS; background audio, Surface and seek tests remain unchanged.

- [ ] **Step 5: Write and commit player UI evidence**

Use `apply_patch` to create `docs/verification/2026-07-31-media-player-queue-ui.md` with exact commit, test commands and PASS/FAIL/NOT RUN for:

- IDLE/Buffering main actions on three surfaces;
- audio/video visual boundaries;
- single timeline and paused seek regression;
- normal vertical volume and fullscreen rails;
- fullscreen queue/help/lock/safe-inset behavior;
- responsive mini dock;
- multi-row queue drag, delete/undo and persistence feedback;
- background/Suface tests.

Then:

```powershell
git add docs/verification/2026-07-31-media-player-queue-ui.md
git commit -m "docs: record player queue ui verification"
```

Do not add screenshots, APKs, `.superpowers/brainstorm/` or the pre-existing 2026-07-30 verification record.

---

## Requirement Traceability

| Requirement | Owner |
|---|---|
| exact shared primary action including IDLE/Buffering | Task 1 |
| audio/video ordinary visual system and stable Surface | Task 2 |
| one timeline, true vertical volume, idle close and gesture colors | Task 3 |
| fullscreen safe scrims, queue, lock and help dismissal | Task 4 |
| bottom-bar mini player, progress, no-next and responsive density | Task 5 |
| adaptive queue, drag handle, cross-item order, semantics and undo | Task 6 |
| one shared queue Sheet, global Snackbar and final regression | Task 7 + flow plan Task 7 single owner |

Task execution order is 1 → 2 → 3 → 4 → 5 → 6 → 7. Task 6 starts only after the flow plan F6 playback-notice controller changes are complete; Task 7 waits for the flow plan root integration owner.
