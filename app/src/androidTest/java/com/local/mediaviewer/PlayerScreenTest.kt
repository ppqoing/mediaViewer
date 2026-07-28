package com.local.mediaviewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.player.AudioPlayerScreen
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
    }
}
