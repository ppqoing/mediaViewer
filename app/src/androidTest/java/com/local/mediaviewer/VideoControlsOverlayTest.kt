package com.local.mediaviewer

import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import com.local.mediaviewer.settings.VideoControlsAutoHide
import com.local.mediaviewer.ui.player.FullscreenStateController
import com.local.mediaviewer.ui.player.PlayerBrightnessController
import com.local.mediaviewer.ui.player.PlayerVolumeController
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import com.local.mediaviewer.ui.player.VolumeState
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VideoControlsOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun playingControlsAutoHideAfterThreeSeconds() {
        rule.mainClock.autoAdvance = false
        showFullscreen(
            hasShownGestureHint = true,
            stateProvider = { fullscreenState(PlaybackStatus.PLAYING) },
        )

        rule.mainClock.advanceTimeBy(2_999)
        rule.onNodeWithTag("video_controls").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(2)
        rule.onNodeWithTag("video_controls").assertDoesNotExist()
    }

    @Test
    fun bufferingKeepsControlsAndNecessaryStatusVisible() {
        rule.mainClock.autoAdvance = false
        showFullscreen(
            hasShownGestureHint = true,
            stateProvider = { fullscreenState(PlaybackStatus.BUFFERING) },
        )

        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithTag("video_controls").assertIsDisplayed()
        rule.onNodeWithTag("video_buffering_spinner").assertIsDisplayed()
    }

    @Test
    fun pausedControlsAutoHideAndLockShowsOnlyUnlockActionAfterReveal() {
        rule.mainClock.autoAdvance = false
        showFullscreen(
            hasShownGestureHint = true,
            stateProvider = { fullscreenState(PlaybackStatus.PAUSED) },
        )

        rule.mainClock.advanceTimeBy(2_999)
        rule.onNodeWithTag("video_controls").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(2)
        rule.onNodeWithTag("video_controls").assertDoesNotExist()
        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.5f, height * 0.5f))
            up()
        }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("video_controls").assertIsDisplayed()
        rule.onNodeWithContentDescription("锁定控制").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("播放").assertDoesNotExist()
        rule.onNodeWithTag("playback_timeline").assertDoesNotExist()
        rule.onNodeWithContentDescription("解锁控制").assertIsDisplayed()
        rule.onNodeWithContentDescription("解锁控制").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun fullscreenPrimaryUsesRealPlayPauseAndReplayCallbacks() {
        rule.mainClock.autoAdvance = false
        var status by mutableStateOf(PlaybackStatus.IDLE)
        var plays = 0
        var pauses = 0
        var replays = 0
        showFullscreen(
            hasShownGestureHint = true,
            stateProvider = { fullscreenState(status) },
            onPlay = { plays++ },
            onPause = { pauses++ },
            onReplay = { replays++ },
        )

        rule.onNodeWithContentDescription("播放").performClick()
        rule.runOnIdle {
            assertEquals(1, plays)
            status = PlaybackStatus.BUFFERING
        }
        rule.mainClock.advanceTimeByFrame()
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
            status = PlaybackStatus.ENDED
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithContentDescription("重新播放").performClick()
        rule.runOnIdle { assertEquals(1, replays) }
    }

    @Test
    fun fullscreenQueueEntryInvokesTheRootCallback() {
        var openQueueCalls = 0
        showFullscreen(
            hasShownGestureHint = true,
            onOpenQueue = { openQueueCalls++ },
        )

        rule.onNodeWithTag("queue_entry_fullscreen").assertIsDisplayed()
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

        assertTrue("顶部控制应位于注入 top inset 之下", top.top >= root.top + 24f)
        assertTrue("顶部控制应位于注入 left inset 之右", top.left >= root.left + 16f)
        assertTrue("顶部控制应位于注入 right inset 之左", top.right <= root.right - 16f)
        assertTrue(
            "底部控制应位于注入 bottom inset 之上",
            bottom.bottom <= root.bottom - 32f,
        )
        assertTrue("底部控制应位于注入 left inset 之右", bottom.left >= root.left + 16f)
        assertTrue("底部控制应位于注入 right inset 之左", bottom.right <= root.right - 16f)
    }

    @Test
    fun bufferingDoesNotMoveTheCenterTransportGroup() {
        rule.mainClock.autoAdvance = false
        var status by mutableStateOf(PlaybackStatus.PLAYING)
        showFullscreen(
            hasShownGestureHint = true,
            stateProvider = { fullscreenState(status) },
        )

        val playingCenter = rule.onNodeWithTag("fullscreen_center_controls")
            .fetchSemanticsNode().boundsInRoot.center
        rule.runOnIdle { status = PlaybackStatus.BUFFERING }
        rule.mainClock.advanceTimeByFrame()
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

    @Test
    fun backWhileLockedRevealsUnlockInsteadOfExitingFullscreen() {
        val controller = showFullscreen(hasShownGestureHint = true)

        rule.onNodeWithContentDescription("锁定控制").performClick()
        rule.onNodeWithContentDescription("解锁控制").assertIsDisplayed()

        Espresso.pressBack()

        // 规格 §10：锁定时返回只提示解锁，不退出全屏。
        rule.runOnIdle { assertEquals(0, controller.exitCalls) }
        rule.onNodeWithContentDescription("解锁控制").assertIsDisplayed()

        rule.onNodeWithContentDescription("解锁控制").performClick()
        Espresso.pressBack()
        rule.runOnIdle { assertEquals(1, controller.exitCalls) }
    }

    @Test
    fun backClosesFullscreenOptionMenuBeforeExit() {
        val controller = showFullscreen(hasShownGestureHint = true)

        rule.onNodeWithContentDescription("更多播放设置").performClick()
        rule.onNodeWithText("后台播放").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("后台播放").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, controller.exitCalls) }
        Espresso.pressBack()
        rule.runOnIdle { assertEquals(1, controller.exitCalls) }
    }

    @Test
    fun fullscreenKeepsBackgroundInMenuAndOptionsBelowTimeline() {
        var selectedSpeed: Float? = null
        var selectedMode: PlaybackMode? = null
        var selectedScale: VideoScaleMode? = null
        showFullscreen(
            hasShownGestureHint = true,
            backgroundPlaybackEnabled = false,
            onBackgroundPlaybackChanged = {},
            onSpeedChanged = { selectedSpeed = it },
            onPlaybackModeChanged = { selectedMode = it },
            onVideoScaleModeChanged = { selectedScale = it },
        )

        rule.onNodeWithTag("video_scale_menu").assertIsDisplayed()
        rule.onNodeWithContentDescription("播放速度，当前 1.0 倍")
            .assertIsDisplayed()
        rule.onNodeWithTag("fullscreen_inline_playback_options")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("更多播放设置").performClick()
        rule.onNodeWithText("后台播放").assertIsDisplayed()
        rule.onNodeWithText("播放速度").assertDoesNotExist()
        rule.onNodeWithText("播放模式").assertDoesNotExist()
        rule.onNodeWithText("画面比例").assertDoesNotExist()

        Espresso.pressBack()
        rule.onNodeWithContentDescription("播放速度，当前 1.0 倍")
            .performClick()
        rule.onNodeWithText("1.5 倍").performClick()
        rule.runOnIdle { assertEquals(1.5f, selectedSpeed) }

        rule.onNodeWithContentDescription("播放模式：顺序播放")
            .performClick()
        rule.runOnIdle {
            assertEquals(PlaybackMode.REPEAT_ALL, selectedMode)
        }

        rule.onNodeWithContentDescription("画面比例").performClick()
        rule.onNodeWithText("裁剪铺满").performClick()
        rule.runOnIdle { assertEquals(VideoScaleMode.FILL_CROP, selectedScale) }
    }

    @Test
    fun fullscreenLockExposesToggleStateAndOnlyOneUnlockAction() {
        showFullscreen(hasShownGestureHint = true)

        rule.onNodeWithContentDescription("锁定控制")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "控制未锁定",
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Off,
                ),
            )
            .performClick()
        rule.onNodeWithContentDescription("解锁控制")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "控制已锁定",
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                ),
            )
        val unlockBounds = rule.onNodeWithContentDescription("解锁控制")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("解锁动作至少 48dp 宽", unlockBounds.width >= 48f)
        assertTrue("解锁动作至少 48dp 高", unlockBounds.height >= 48f)
        rule.onNodeWithTag("playback_timeline").assertDoesNotExist()
        rule.onNodeWithContentDescription("音量，当前 50%，未静音")
            .assertDoesNotExist()
        rule.onNodeWithContentDescription("打开播放队列").assertDoesNotExist()
        rule.onNodeWithContentDescription("锁定控制").assertDoesNotExist()
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun lockClearsGestureFeedbackImmediately() {
        showFullscreen(hasShownGestureHint = true)

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.75f, height * 0.7f))
            moveTo(Offset(width * 0.75f, height * 0.3f))
            up()
        }
        rule.onNodeWithTag("gesture_volume_rail").assertIsDisplayed()

        rule.onNodeWithContentDescription("锁定控制").performClick()
        rule.onNodeWithTag("gesture_volume_rail").assertDoesNotExist()
        rule.onNodeWithContentDescription("解锁控制").assertIsDisplayed()
    }

    @Test
    fun fullscreenPrimaryUsesSeventyTwoDpTouchTarget() {
        showFullscreen(hasShownGestureHint = true)

        val primary = rule.onNodeWithContentDescription("播放")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("主动作宽度至少 72dp", primary.width >= 72f)
        assertTrue("主动作高度至少 72dp", primary.height >= 72f)
        listOf("快退 10 秒", "快进 10 秒", "打开播放队列", "返回", "锁定控制")
            .forEach { description ->
                val bounds = rule.onNodeWithContentDescription(description)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("$description 宽度至少 48dp", bounds.width >= 48f)
                assertTrue("$description 高度至少 48dp", bounds.height >= 48f)
            }
    }

    @Test
    fun fullscreenGestureRailLivesUntilEightHundredMillisecondDeadline() {
        rule.mainClock.autoAdvance = false
        showFullscreen(hasShownGestureHint = true)

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.75f, height * 0.7f))
            moveTo(Offset(width * 0.75f, height * 0.3f))
            up()
        }
        // 手势处理与重组完成后，Screen 的 800ms 反馈生命周期开始计时。
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("gesture_volume_rail").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(650)
        rule.onNodeWithTag("gesture_volume_rail").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("gesture_volume_rail").assertDoesNotExist()
    }

    private fun fullscreenState(status: PlaybackStatus) = PlayerUiState(
        name = "movie.mp4",
        kind = MediaKind.VIDEO,
        status = status,
        durationMs = 60_000L,
        isSeekable = true,
    )

    private fun showFullscreen(
        hasShownGestureHint: Boolean,
        backgroundPlaybackEnabled: Boolean = false,
        onBackgroundPlaybackChanged: (Boolean) -> Unit = {},
        onOpenQueue: () -> Unit = {},
        safeDrawingInsets: WindowInsets = WindowInsets(
            left = 0.dp,
            top = 0.dp,
            right = 0.dp,
            bottom = 0.dp,
        ),
        stateProvider: () -> PlayerUiState = {
            fullscreenState(PlaybackStatus.PAUSED)
        },
        onPlay: () -> Unit = {},
        onPause: () -> Unit = {},
        onReplay: () -> Unit = {},
        onSpeedChanged: (Float) -> Unit = {},
        onPlaybackModeChanged: (PlaybackMode) -> Unit = {},
        onVideoScaleModeChanged: (VideoScaleMode) -> Unit = {},
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
                        onSpeedChanged = onSpeedChanged,
                        backgroundPlaybackEnabled =
                            backgroundPlaybackEnabled,
                        onBackgroundPlaybackChanged =
                            onBackgroundPlaybackChanged,
                        playbackMode = PlaybackMode.SEQUENTIAL,
                        onPlaybackModeChanged = onPlaybackModeChanged,
                        onOpenQueue = onOpenQueue,
                        onRetry = {},
                        onVideoScaleModeChanged = onVideoScaleModeChanged,
                        onBack = {},
                        safeDrawingInsets = safeDrawingInsets,
                    )
                }
            }
        }
        return controller
    }
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
    private val autoHide = MutableStateFlow(
        VideoControlsAutoHide.THREE_SECONDS,
    )
    override val videoControlsAutoHide: StateFlow<VideoControlsAutoHide> =
        autoHide

    override suspend fun markVideoGesturesShown() {
        value.value = true
    }

    override suspend fun setVideoControlsAutoHide(
        value: VideoControlsAutoHide,
    ) {
        autoHide.value = value
    }
}

private class OverlayPlaybackController : PlaybackController {
    private val value = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = value

    override fun prepare(url: String) = Unit

    override fun attachVideoOutput(host: ViewGroup) = Unit

    override fun detachVideoOutput() = Unit

    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit

    override fun setPlaybackSpeed(speed: Float) = Unit

    override fun play() = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun close() = Unit
}

private class OverlayVolumeController : PlayerVolumeController {
    override val state: StateFlow<VolumeState> = MutableStateFlow(VolumeState(5, 10, false))

    override fun refresh() = Unit

    override fun setFraction(value: Float) = Unit

    override fun adjustByFraction(delta: Float) = Unit

    override fun toggleMute() = Unit
}

private class OverlayBrightnessController : PlayerBrightnessController {
    override val fraction: StateFlow<Float> = MutableStateFlow(0.5f)

    override fun setFraction(value: Float) = Unit

    override fun adjustByFraction(delta: Float) = Unit

    override fun close() = Unit
}
