package com.local.mediaviewer

import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.ui.player.AudioPlayerScreen
import com.local.mediaviewer.ui.player.FullscreenStateController
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PlayerScreenTest {
    @get:Rule
    val rule = createComposeRule()

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
        rule.onNodeWithText("00:30 / 02:00").assertIsDisplayed()
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
        rule.onNodeWithContentDescription("音量，当前 0%，已静音").assertIsDisplayed()
        rule.onNodeWithContentDescription("取消静音").assertIsDisplayed()
        rule.onNodeWithTag("volume_slider").assertIsDisplayed()
        rule.onNodeWithContentDescription("亮度").assertDoesNotExist()
        rule.onNodeWithContentDescription("锁定控制").assertDoesNotExist()
        rule.onNodeWithContentDescription("画面比例").assertDoesNotExist()
        rule.onNodeWithContentDescription("全屏").assertDoesNotExist()
    }

    @Test
    fun videoScaleMenuWorksInNormalAndFullscreen() {
        var selectedMode: VideoScaleMode? = null
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
                    onRetry = {},
                    onVideoScaleModeChanged = {
                        selectedMode = it
                    },
                    onBack = {},
                )
            }
        }

        rule.onNodeWithTag("video_scale_menu")
            .performClick()
        rule.onNodeWithContentDescription("画面比例")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("全屏")
            .assertIsDisplayed()
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
        rule.onNodeWithTag("seek").assertDoesNotExist()
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

private class ScreenPlayerPreferencesRepository : PlayerPreferencesRepository {
    private val mutable = MutableStateFlow(true)
    override val hasShownVideoGestures: StateFlow<Boolean> = mutable

    override suspend fun markVideoGesturesShown() {
        mutable.value = true
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

    override fun refresh() = Unit

    override fun setFraction(value: Float) {
        val maximum = mutable.value.maximum
        val current = (value.coerceIn(0f, 1f) * maximum).toInt()
        mutable.value = mutable.value.copy(current = current, muted = current == 0)
    }

    override fun adjustByFraction(delta: Float) = Unit

    override fun toggleMute() {
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
