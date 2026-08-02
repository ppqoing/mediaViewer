package com.local.mediaviewer

import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import com.local.mediaviewer.ui.player.AudioPlayerScreen
import com.local.mediaviewer.ui.player.FullscreenStateController
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import com.local.mediaviewer.settings.VideoControlsAutoHide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun audioUsesWarmArtworkAndNeverShowsVideoOnlyControls() {
        showAudio(playerState(name = "song.flac", kind = MediaKind.AUDIO))

        rule.onNodeWithTag("audio_artwork_card").assertIsDisplayed()
        rule.onNodeWithText("song.flac").assertIsDisplayed()
        rule.onNodeWithContentDescription("画面比例").assertDoesNotExist()
        rule.onNodeWithContentDescription("进入全屏").assertDoesNotExist()
        rule.onNodeWithContentDescription("锁定控制").assertDoesNotExist()
    }

    @Test
    fun normalVideoKeepsOptionsInMoreMenuAndUsesPrimaryTransportAction() {
        showVideo(playerState(name = "movie.mp4", kind = MediaKind.VIDEO))

        rule.onAllNodesWithTag("vlc_surface").assertCountEquals(1)
        rule.onNodeWithTag("video_primary_action")
            .assertIsDisplayed()
            .assertHasClickAction()
        rule.onNodeWithTag("video_top_controls_ordinary")
            .assertIsDisplayed()
        rule.onNodeWithTag("video_bottom_controls_ordinary")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("更多播放选项").performClick()
        rule.onNodeWithText("后台播放").assertIsDisplayed()
        rule.onNodeWithText("播放速度").assertIsDisplayed()
        rule.onNodeWithText("播放模式").assertIsDisplayed()
        rule.onNodeWithText("画面比例").assertIsDisplayed()
    }

    @Test
    fun ordinaryVideoGroupsQueueAndVolumeOppositeFullscreen() {
        showVideo(playerState(name = "movie.mp4", kind = MediaKind.VIDEO))

        assertControlInsideGroup("打开队列", "player_utility_start_group")
        assertControlInsideGroup(
            "音量，当前 50%，未静音",
            "player_utility_start_group",
        )
        assertControlInsideGroup("全屏", "player_utility_end_group")

        val queueCenter = rule.onNodeWithContentDescription("打开队列")
            .fetchSemanticsNode().boundsInRoot.center.x
        val fullscreenCenter = rule.onNodeWithContentDescription("全屏")
            .fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(queueCenter < fullscreenCenter)
    }

    @Test
    fun audioGroupsPlaybackOptionsOppositeQueueAndVolume() {
        rule.setContent {
            MaterialTheme {
                AudioPlayerScreen(
                    state = PlayerUiState(
                        name = "音乐.flac",
                        kind = MediaKind.AUDIO,
                        status = PlaybackStatus.PAUSED,
                        durationMs = 60_000L,
                        isSeekable = true,
                    ),
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
                    volumeController = ScreenFakeVolumeController(),
                    onBack = {},
                )
            }
        }

        assertControlInsideGroup(
            "播放速度，当前 1.0 倍",
            "player_utility_start_group",
        )
        assertControlInsideGroup(
            "播放模式：顺序播放",
            "player_utility_start_group",
        )
        assertControlInsideGroup("打开队列", "player_utility_end_group")
        assertControlInsideGroup(
            "音量，当前 50%，未静音",
            "player_utility_end_group",
        )
        rule.onNodeWithContentDescription("画面比例").assertDoesNotExist()
        rule.onNodeWithContentDescription("全屏").assertDoesNotExist()
        rule.onNodeWithContentDescription("锁定控制").assertDoesNotExist()
    }

    @Test
    fun ordinaryVideoUsesStableCanvasAndTapTogglesOverlays() {
        rule.mainClock.autoAdvance = false
        showVideo(
            playerState(
                name = "movie.mp4",
                kind = MediaKind.VIDEO,
                status = PlaybackStatus.PAUSED,
            ),
        )

        val visibleBounds = rule.onNodeWithTag("vlc_surface")
            .fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("video_top_controls_ordinary")
            .assertIsDisplayed()
        rule.onNodeWithTag("video_bottom_controls_ordinary")
            .assertIsDisplayed()

        rule.mainClock.advanceTimeBy(3_100L)
        rule.onNodeWithTag("video_top_controls_ordinary")
            .assertDoesNotExist()
        rule.onNodeWithTag("video_bottom_controls_ordinary")
            .assertDoesNotExist()
        assertEquals(
            visibleBounds,
            rule.onNodeWithTag("vlc_surface")
                .fetchSemanticsNode().boundsInRoot,
        )

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.5f, height * 0.5f))
            up()
        }
        rule.mainClock.advanceTimeBy(400L)
        rule.onNodeWithTag("video_top_controls_ordinary")
            .assertIsDisplayed()
        rule.onNodeWithTag("video_bottom_controls_ordinary")
            .assertIsDisplayed()
        assertEquals(
            visibleBounds,
            rule.onNodeWithTag("vlc_surface")
                .fetchSemanticsNode().boundsInRoot,
        )
    }

    @Test
    fun ordinaryVideoDoubleTapTogglesPlayPauseWithoutSeek() {
        var state by mutableStateOf(
            playerState(
                name = "movie.mp4",
                kind = MediaKind.VIDEO,
                status = PlaybackStatus.PAUSED,
            ),
        )
        var playCalls = 0
        var pauseCalls = 0
        var seekBackCalls = 0
        var seekForwardCalls = 0
        showVideo(
            stateProvider = { state },
            onPlay = {
                playCalls += 1
                state = state.copy(status = PlaybackStatus.PLAYING)
            },
            onPause = {
                pauseCalls += 1
                state = state.copy(status = PlaybackStatus.PAUSED)
            },
            onSeekBack = { seekBackCalls += 1 },
            onSeekForward = { seekForwardCalls += 1 },
        )

        fun doubleTap() {
            rule.onNodeWithTag("video_gesture_layer")
                .performTouchInput {
                    down(Offset(width * 0.25f, height * 0.5f))
                    up()
                    advanceEventTime(100)
                    down(Offset(width * 0.75f, height * 0.5f))
                    up()
                }
        }

        doubleTap()
        rule.runOnIdle {
            assertEquals(1, playCalls)
            assertEquals(0, pauseCalls)
            assertEquals(0, seekBackCalls)
            assertEquals(0, seekForwardCalls)
        }

        doubleTap()
        rule.runOnIdle {
            assertEquals(1, playCalls)
            assertEquals(1, pauseCalls)
            assertEquals(0, seekBackCalls)
            assertEquals(0, seekForwardCalls)
        }
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

    @Test
    fun audioErrorActionsRemainReachableAt320DpAndTwoXFont() {
        var retries = 0
        var backs = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = 1f,
                    fontScale = 2f,
                ),
            ) {
                MediaViewerTheme {
                    Box(
                        modifier = androidx.compose.ui.Modifier.size(
                            width = 320.dp,
                            height = 568.dp,
                        ),
                    ) {
                        AudioPlayerScreen(
                            state = PlayerUiState(
                                name = "一首文件名很长的无损音乐文件.flac",
                                kind = MediaKind.AUDIO,
                                status = PlaybackStatus.ERROR,
                                durationMs = 60_000L,
                                isSeekable = true,
                                errorMessage =
                                    "无法播放该媒体，请检查网络连接后重试",
                            ),
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
                            onRetry = { retries++ },
                            volumeController = ScreenFakeVolumeController(),
                            onBack = { backs++ },
                        )
                    }
                }
            }
        }

        rule.onNodeWithContentDescription("返回").assertIsDisplayed()
        rule.onNodeWithText("重试")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        rule.runOnIdle { assertEquals(1, retries) }
        rule.onNodeWithContentDescription("返回")
            .assertIsDisplayed()
            .performClick()
        rule.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun audioScreenShowsControlsWithoutVideoSurface() {
        val volumeController = ScreenFakeVolumeController()
        rule.setContent {
            MaterialTheme {
                AudioPlayerScreen(
                    state = PlayerUiState(
                        name = "音乐.flac",
                        kind = MediaKind.AUDIO,
                        status = PlaybackStatus.PAUSED,
                        positionMs = 30_000,
                        durationMs = 120_000,
                        isSeekable = true,
                        resumedFromMs = 30_000,
                    ),
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
                    volumeController = volumeController,
                    onBack = {},
                )
            }
        }

        rule.onNodeWithText("音乐.flac").assertIsDisplayed()
        rule.onNodeWithText("00:30").assertIsDisplayed()
        rule.onNodeWithText("02:00").assertIsDisplayed()
        rule.onNodeWithText("00:30 / 02:00").assertDoesNotExist()
        rule.onNodeWithText("已从 00:30 继续播放").assertIsDisplayed()
        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        rule.onNodeWithContentDescription("快退 10 秒")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("快进 10 秒")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("播放速度，当前 1.0 倍")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("上一项").assertIsNotEnabled()
        rule.onNodeWithContentDescription("下一项").assertIsNotEnabled()
        rule.onNodeWithTag("vlc_surface").assertDoesNotExist()
        rule.onNodeWithContentDescription("画面比例")
            .assertDoesNotExist()
        rule.onNodeWithContentDescription("全屏")
            .assertDoesNotExist()
    }

    @Test
    fun audioScreenExposesVolumeControlsWithoutVideoCapabilities() {
        val volumeController = ScreenFakeVolumeController()
        rule.setContent {
            MaterialTheme {
                AudioPlayerScreen(
                    state = PlayerUiState(
                        name = "音乐.flac",
                        kind = MediaKind.AUDIO,
                        status = PlaybackStatus.PAUSED,
                    ),
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
                    volumeController = volumeController,
                    onBack = {},
                )
            }
        }

        rule.onNodeWithContentDescription("音量，当前 50%，未静音").assertIsDisplayed()
        rule.onNodeWithContentDescription("音量，当前 50%，未静音").performClick()
        rule.onNodeWithTag("volume_slider_vertical").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, volumeController.toggleMuteCalls) }
        rule.onNodeWithContentDescription("静音").performClick()
        rule.runOnIdle { assertEquals(1, volumeController.toggleMuteCalls) }
        rule.onNodeWithContentDescription("音量，当前 0%，已静音").assertIsDisplayed()
        rule.onNodeWithContentDescription("取消静音").assertIsDisplayed()
        rule.onNodeWithContentDescription("亮度").assertDoesNotExist()
        rule.onNodeWithContentDescription("锁定控制").assertDoesNotExist()
        rule.onNodeWithContentDescription("画面比例").assertDoesNotExist()
        rule.onNodeWithContentDescription("全屏").assertDoesNotExist()
    }

    @Test
    fun lowFrequencyOptionsWorkInNormalAndFullscreen() {
        var selectedMode: VideoScaleMode? = null
        var backgroundPlaybackEnabled by mutableStateOf(false)
        val fullscreenController =
            ScreenFakeFullscreenController()
        rule.setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = PlaybackStatus.PAUSED,
                        durationMs = 60_000,
                        isSeekable = true,
                    ),
                    controller = ScreenFakePlaybackController(),
                    fullscreenController =
                        fullscreenController,
                    preferences = ScreenPlayerPreferencesRepository(),
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
                    backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                    onBackgroundPlaybackChanged = {
                        backgroundPlaybackEnabled = it
                    },
                    onRetry = {},
                    onVideoScaleModeChanged = {
                        selectedMode = it
                    },
                    onBack = {},
                )
            }
        }

        rule.onNodeWithContentDescription("全屏")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("更多播放选项")
            .performClick()
        rule.onNodeWithTag("video_background_playback")
            .performClick()
        rule.runOnIdle { assertTrue(backgroundPlaybackEnabled) }
        rule.onNodeWithText("画面比例")
            .assertIsDisplayed()
            .performClick()
        rule.onNodeWithText("等比适应")
            .assertIsDisplayed()
        rule.onNodeWithText("裁剪铺满")
            .performClick()
        rule.runOnIdle {
            assertEquals(
                VideoScaleMode.FILL_CROP,
                selectedMode,
            )
        }

        rule.onNodeWithContentDescription("全屏")
            .performClick()
        rule.onNodeWithTag("video_scale_menu")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("更多播放设置")
            .performClick()
        rule.onNodeWithText("后台播放").assertIsDisplayed()
        rule.onNodeWithTag("video_background_playback")
            .performClick()
        rule.runOnIdle { assertFalse(backgroundPlaybackEnabled) }
        rule.onNodeWithText("播放速度").assertDoesNotExist()
        rule.onNodeWithTag("fullscreen_playback_configuration")
            .assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithContentDescription("画面比例")
            .performClick()
        rule.onNodeWithText("强制拉伸")
            .performClick()
        rule.runOnIdle {
            assertEquals(
                VideoScaleMode.STRETCH,
                selectedMode,
            )
        }
    }

    @Test
    fun errorStateShowsMessageRetryAndBack() {
        rule.setContent {
            MaterialTheme {
                AudioPlayerScreen(
                    state = PlayerUiState(
                        name = "音乐.flac",
                        kind = MediaKind.AUDIO,
                        status = PlaybackStatus.ERROR,
                        errorMessage = "无法播放该媒体",
                    ),
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

        rule.onNodeWithText("无法播放该媒体").assertIsDisplayed()
        rule.onNodeWithText("重试").assertIsDisplayed()
        rule.onNodeWithText("返回").assertIsDisplayed()
    }

    @Test
    fun videoBufferingShowsCentralSpinnerAndSingleTimeline() {
        rule.setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = PlaybackStatus.BUFFERING,
                        durationMs = 60_000L,
                        isSeekable = true,
                    ),
                    controller = ScreenFakePlaybackController(),
                    fullscreenController = ScreenFakeFullscreenController(),
                    preferences = ScreenPlayerPreferencesRepository(),
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
                    onRetry = {},
                    onVideoScaleModeChanged = {},
                    onBack = {},
                )
            }
        }

        rule.onNodeWithTag("playback_timeline").assertIsDisplayed()
        rule.onNodeWithTag("video_buffering_spinner").assertIsDisplayed()
        rule.onNodeWithTag("timeline_buffering_bar").assertDoesNotExist()
    }

    @Test
    fun fullscreenBufferingKeepsCentralSpinnerClearOfTransportControls() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = PlaybackStatus.BUFFERING,
                        durationMs = 60_000L,
                        isSeekable = true,
                    ),
                    controller = ScreenFakePlaybackController(),
                    fullscreenController = ScreenFakeFullscreenController(),
                    preferences = ScreenPlayerPreferencesRepository(),
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
                    onRetry = {},
                    onVideoScaleModeChanged = {},
                    onBack = {},
                )
            }
        }

        rule.onNodeWithContentDescription("全屏").performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag("video_buffering_spinner").assertIsDisplayed()
        rule.onNodeWithTag("video_controls").assertIsDisplayed()
        rule.onNodeWithContentDescription("暂停").assertIsDisplayed()
        rule.onNodeWithContentDescription("退出全屏").assertIsDisplayed()
        rule.onNodeWithTag("playback_timeline").assertIsDisplayed()

        val spinnerBounds = rule
            .onNodeWithTag("video_buffering_spinner")
            .fetchSemanticsNode()
            .boundsInRoot
        val pauseBounds = rule
            .onNodeWithContentDescription("暂停")
            .fetchSemanticsNode()
            .boundsInRoot
        assertFalse(
            "全屏缓冲 spinner 不应与中央暂停按钮重叠",
            spinnerBounds.overlaps(pauseBounds),
        )
    }

    @Test
    fun fullscreenBufferingHasOneFeedbackOwnerAndDoesNotOverlapPrimary() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = PlaybackStatus.BUFFERING,
                        durationMs = 60_000L,
                        isSeekable = true,
                    ),
                    controller = ScreenFakePlaybackController(),
                    fullscreenController = ScreenFakeFullscreenController(),
                    preferences = ScreenPlayerPreferencesRepository(),
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
                    onRetry = {},
                    onVideoScaleModeChanged = {},
                    onBack = {},
                )
            }
        }

        rule.onNodeWithContentDescription("全屏").performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag("video_buffering_spinner").assertIsDisplayed()
        rule.onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        ).assertCountEquals(1)
        val spinnerBounds = rule
            .onNodeWithTag("video_buffering_spinner")
            .fetchSemanticsNode()
            .boundsInRoot
        val primaryBounds = rule
            .onNodeWithContentDescription("暂停")
            .fetchSemanticsNode()
            .boundsInRoot
        assertFalse(
            "唯一的缓冲反馈不应与固定居中的主动作重叠",
            spinnerBounds.overlaps(primaryBounds),
        )
    }

    @Test
    fun audioBufferingShowsAudioSpinner() {
        rule.setContent {
            MaterialTheme {
                AudioPlayerScreen(
                    state = PlayerUiState(
                        name = "音乐.flac",
                        kind = MediaKind.AUDIO,
                        status = PlaybackStatus.BUFFERING,
                        durationMs = 60_000L,
                        isSeekable = true,
                    ),
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

        rule.onNodeWithTag("audio_buffering_spinner").assertIsDisplayed()
    }

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

    private fun showAudio(state: PlayerUiState, darkTheme: Boolean? = null) {
        rule.setContent {
            MediaViewerTheme(darkTheme = darkTheme ?: isSystemInDarkTheme()) {
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
        darkTheme: Boolean? = null,
        onPlay: () -> Unit = {},
        onPause: () -> Unit = {},
        onSeekBack: () -> Unit = {},
        onSeekForward: () -> Unit = {},
    ) {
        rule.setContent {
            MediaViewerTheme(darkTheme = darkTheme ?: isSystemInDarkTheme()) {
                VideoPlayerScreen(
                    state = stateProvider(),
                    controller = ScreenFakePlaybackController(),
                    fullscreenController = ScreenFakeFullscreenController(),
                    preferences = ScreenPlayerPreferencesRepository(
                        initiallyShown = true,
                    ),
                    volumeController = ScreenFakeVolumeController(),
                    brightnessController = ScreenFakeBrightnessController(),
                    onPlay = onPlay,
                    onPause = onPause,
                    onReplay = {},
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
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

    private fun showVideo(
        state: PlayerUiState,
        darkTheme: Boolean? = null,
    ) = showVideo(stateProvider = { state }, darkTheme = darkTheme)

    @Test
    fun ordinaryVideoPlayerKeepsControlsOnDarkCanvasInLightTheme() {
        showVideo(
            playerState(name = "movie.mp4", kind = MediaKind.VIDEO),
            darkTheme = false,
        )

        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        assertControlsRegionStaysOnDarkCanvas()
    }

    @Test
    fun ordinaryAudioPlayerUsesWarmPaperCanvasInLightTheme() {
        showAudio(
            playerState(name = "song.flac", kind = MediaKind.AUDIO),
            darkTheme = false,
        )

        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        val averageLuminance = controlsRegionAverageLuminance()
        assertTrue(
            "audio player should use a warm paper canvas in light theme " +
                "(average controls-region luminance=$averageLuminance)",
            averageLuminance > 0.5,
        )
    }

    private fun assertControlsRegionStaysOnDarkCanvas() {
        val averageLuminance = controlsRegionAverageLuminance()
        assertTrue(
            "ordinary player must keep the dark player canvas in light theme " +
                "(average controls-region luminance=$averageLuminance)",
            averageLuminance < 0.5,
        )
    }

    private fun controlsRegionAverageLuminance(): Double {
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val regionTop = pixels.height * 3 / 5
        var luminanceSum = 0.0
        var samples = 0
        var y = regionTop
        while (y < pixels.height) {
            var x = 0
            while (x < pixels.width) {
                val color = pixels[x, y]
                luminanceSum +=
                    0.2126 * color.red +
                    0.7152 * color.green +
                    0.0722 * color.blue
                samples++
                x += 8
            }
            y += 4
        }
        return luminanceSum / samples
    }

    private fun assertControlInsideGroup(
        contentDescription: String,
        groupTag: String,
    ) {
        val control = rule.onNodeWithContentDescription(contentDescription)
            .fetchSemanticsNode().boundsInRoot
        val group = rule.onNodeWithTag(groupTag)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$contentDescription should be inside $groupTag: " +
                "control=$control group=$group",
            control.left >= group.left &&
                control.top >= group.top &&
                control.right <= group.right &&
                control.bottom <= group.bottom,
        )
    }
}

private class ScreenFakeFullscreenController :
    FullscreenStateController {
    private val mutable = MutableStateFlow(false)
    override val isFullscreen: StateFlow<Boolean> = mutable

    override fun enter() {
        mutable.value = true
    }

    override fun exit() {
        mutable.value = false
    }

    override fun close() {
        mutable.value = false
    }
}

private class ScreenPlayerPreferencesRepository(
    initiallyShown: Boolean = true,
) : PlayerPreferencesRepository {
    private val mutable = MutableStateFlow(initiallyShown)
    override val hasShownVideoGestures: StateFlow<Boolean> = mutable
    private val autoHide = MutableStateFlow(
        VideoControlsAutoHide.THREE_SECONDS,
    )
    override val videoControlsAutoHide: StateFlow<VideoControlsAutoHide> =
        autoHide

    override suspend fun markVideoGesturesShown() {
        mutable.value = true
    }

    override suspend fun setVideoControlsAutoHide(
        value: VideoControlsAutoHide,
    ) {
        autoHide.value = value
    }
}

private class ScreenFakePlaybackController : PlaybackController {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable

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

private class ScreenFakeVolumeController :
    com.local.mediaviewer.ui.player.PlayerVolumeController {
    private val mutable = MutableStateFlow(
        com.local.mediaviewer.ui.player.VolumeState(
            current = 5,
            maximum = 10,
            muted = false,
        ),
    )
    override val state: StateFlow<com.local.mediaviewer.ui.player.VolumeState> = mutable
    var toggleMuteCalls = 0
        private set

    override fun refresh() = Unit

    override fun setFraction(value: Float) {
        val maximum = mutable.value.maximum
        val current = (value.coerceIn(0f, 1f) * maximum).toInt()
        mutable.value = mutable.value.copy(current = current, muted = current == 0)
    }

    override fun adjustByFraction(delta: Float) = Unit

    override fun toggleMute() {
        toggleMuteCalls += 1
        mutable.value = if (mutable.value.muted) {
            mutable.value.copy(current = 5, muted = false)
        } else {
            mutable.value.copy(current = 0, muted = true)
        }
    }
}

private class ScreenFakeBrightnessController :
    com.local.mediaviewer.ui.player.PlayerBrightnessController {
    override val fraction = MutableStateFlow(0.5f)

    override fun setFraction(value: Float) = Unit

    override fun adjustByFraction(delta: Float) = Unit

    override fun close() = Unit
}
