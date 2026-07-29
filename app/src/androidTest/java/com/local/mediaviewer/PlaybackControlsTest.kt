package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.player.PlayerControls
import org.junit.Rule
import org.junit.Test

class PlaybackControlsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun playingUnseekablePlayerShowsAllSharedControls() {
        rule.setContent {
            MaterialTheme {
                PlayerControls(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = PlaybackStatus.PLAYING,
                        durationMs = 60_000L,
                    ),
                    onPlay = {},
                    onPause = {},
                    onSeek = {},
                )
            }
        }

        rule.onNodeWithContentDescription("暂停").assertIsDisplayed()
        rule.onNodeWithContentDescription("快退 10 秒")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("快进 10 秒")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("上一项")
            .assertIsNotEnabled()
        rule.onNodeWithContentDescription("下一项")
            .assertIsNotEnabled()
        rule.onNodeWithContentDescription("播放速度，当前 1.0 倍")
            .assertIsDisplayed()
    }

    @Test
    fun endedPlayerShowsReplayAction() {
        rule.setContent {
            MaterialTheme {
                PlayerControls(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = PlaybackStatus.ENDED,
                    ),
                    onPlay = {},
                    onPause = {},
                    onSeek = {},
                )
            }
        }

        rule.onNodeWithContentDescription("重新播放")
            .assertIsDisplayed()
    }

}
