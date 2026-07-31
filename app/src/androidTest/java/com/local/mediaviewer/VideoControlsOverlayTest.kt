package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import android.view.ViewGroup
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import com.local.mediaviewer.ui.player.FullscreenStateController
import com.local.mediaviewer.ui.player.PlayerBrightnessController
import com.local.mediaviewer.ui.player.PlayerVolumeController
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import com.local.mediaviewer.ui.player.VolumeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VideoControlsOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun playingControlsAutoHideAfterThreeSeconds() {
        rule.mainClock.autoAdvance = false
        setOverlay(status = PlaybackStatus.PLAYING)

        rule.mainClock.advanceTimeBy(2_999)
        rule.onNodeWithTag("video_controls").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(2)
        rule.onNodeWithTag("video_controls").assertDoesNotExist()
    }

    @Test
    fun pausedControlsRemainVisibleAndLockShowsOnlyUnlockAction() {
        rule.mainClock.autoAdvance = false
        setOverlay(status = PlaybackStatus.PAUSED)

        rule.mainClock.advanceTimeBy(5_000)
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
        rule.onNodeWithContentDescription("重新播放").performClick()
        rule.runOnIdle { assertEquals(1, replays) }
    }

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
}

private class OverlayFullscreenController : FullscreenStateController {
    private val value = MutableStateFlow(true)
    override val isFullscreen: StateFlow<Boolean> = value

    override fun enter() {
        value.value = true
    }

    override fun exit() {
        value.value = false
    }

    override fun close() = Unit
}

private class OverlayPreferencesRepository : PlayerPreferencesRepository {
    private val value = MutableStateFlow(true)
    override val hasShownVideoGestures: StateFlow<Boolean> = value

    override suspend fun markVideoGesturesShown() {
        value.value = true
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
