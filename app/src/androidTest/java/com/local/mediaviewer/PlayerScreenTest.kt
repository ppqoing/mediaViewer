package com.local.mediaviewer

import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun audioScreenShowsControlsWithoutVideoSurface() {
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
                    onSeek = {},
                    onBack = {},
                )
            }
        }

        rule.onNodeWithText("音乐.flac").assertIsDisplayed()
        rule.onNodeWithText("00:30 / 02:00").assertIsDisplayed()
        rule.onNodeWithText("已从 00:30 继续播放").assertIsDisplayed()
        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        rule.onNodeWithTag("vlc_surface").assertDoesNotExist()
        rule.onNodeWithTag("video_scale_menu")
            .assertDoesNotExist()
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
                    onPlay = {},
                    onPause = {},
                    onSeek = {},
                    onVideoScaleModeChanged = {
                        selectedMode = it
                    },
                    onBack = {},
                )
            }
        }

        rule.onNodeWithTag("video_scale_menu")
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
        rule.onNodeWithTag("seek").assertDoesNotExist()
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
