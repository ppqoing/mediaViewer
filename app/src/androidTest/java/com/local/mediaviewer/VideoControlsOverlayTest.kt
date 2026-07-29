package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
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
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        rule.onNodeWithContentDescription("播放").assertDoesNotExist()
        rule.onNodeWithTag("playback_timeline").assertDoesNotExist()
        rule.onNodeWithContentDescription("解锁控制").assertIsDisplayed()
        rule.onNodeWithContentDescription("解锁控制").performClick()
        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    private fun setOverlay(status: PlaybackStatus) {
        rule.setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = status,
                        durationMs = 60_000L,
                        isSeekable = true,
                    ),
                    controller = OverlayPlaybackController(),
                    fullscreenController = OverlayFullscreenController(),
                    preferences = OverlayPreferencesRepository(),
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
