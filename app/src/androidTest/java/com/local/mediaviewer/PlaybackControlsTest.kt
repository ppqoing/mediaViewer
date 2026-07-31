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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.player.PlaybackVolumeControl
import com.local.mediaviewer.ui.player.PlayerControls
import com.local.mediaviewer.ui.player.VolumeState
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackControlsTest {
    @get:Rule
    val rule = createComposeRule()

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
    fun ordinaryIdleUsesPlayCallback() {
        var plays = 0
        showOrdinaryPrimary(PlaybackStatus.IDLE, { plays++ }, {}, {})

        rule.onNodeWithContentDescription("播放").performClick()
        rule.runOnIdle { assertEquals(1, plays) }
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

    @Test
    fun ordinaryEndedUsesReplayCallback() {
        var replays = 0
        showOrdinaryPrimary(PlaybackStatus.ENDED, {}, {}, { replays++ })

        rule.onNodeWithContentDescription("重新播放").performClick()
        rule.runOnIdle { assertEquals(1, replays) }
    }

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
                    onReplay = {},
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
                    onReplay = {},
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

        rule.onNodeWithContentDescription("重新播放")
            .assertIsDisplayed()
    }

    @Test
    fun bufferingPlayerKeepsSingleTimelineWithoutSecondBufferingBar() {
        rule.setContent {
            MaterialTheme {
                PlayerControls(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
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
                )
            }
        }

        rule.onNodeWithTag("playback_timeline").assertIsDisplayed()
        rule.onNodeWithTag("timeline_buffering_bar").assertDoesNotExist()
    }

    @Test
    fun volumeButtonOpensVerticalVolumePopup() {
        var expanded by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                PlaybackVolumeControl(
                    state = VolumeState(current = 5, maximum = 10, muted = false),
                    expanded = expanded,
                    onExpandedChanged = { expanded = it },
                    onRefresh = {},
                    onToggleMute = {},
                    onVolumeChanged = {},
                )
            }
        }

        rule.onNodeWithContentDescription("音量，当前 50%，未静音").performClick()
        rule.onNodeWithTag("volume_popup").assertIsDisplayed()
        rule.onNodeWithTag("volume_slider_vertical").assertIsDisplayed()
        rule.onNodeWithText("50%").assertIsDisplayed()

        val popupHeight = rule.onNodeWithTag("volume_popup")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val sliderHeight = rule.onNodeWithTag("volume_slider_vertical")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        assertTrue(
            "旋转后的音量滑块有效长度应至少达到弹层轨道容器的 80%",
            sliderHeight >= popupHeight * 0.8f,
        )
    }

    @Test
    fun volumeTriggerDoesNotToggleMute() {
        var expanded by mutableStateOf(false)
        var muteCalls = 0
        rule.setContent {
            MaterialTheme {
                PlaybackVolumeControl(
                    state = VolumeState(current = 5, maximum = 10, muted = false),
                    expanded = expanded,
                    onExpandedChanged = { expanded = it },
                    onRefresh = {},
                    onToggleMute = { muteCalls++ },
                    onVolumeChanged = {},
                )
            }
        }

        rule.onNodeWithContentDescription("音量，当前 50%，未静音").performClick()
        rule.runOnIdle { assertEquals(0, muteCalls) }
        rule.onNodeWithContentDescription("静音").performClick()
        rule.runOnIdle { assertEquals(1, muteCalls) }
    }

    @Test
    fun volumePollingContinuesWhileExpandedAndStopsWhenClosed() {
        rule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(false)
        var refreshCalls = 0
        rule.setContent {
            MaterialTheme {
                PlaybackVolumeControl(
                    state = VolumeState(current = 5, maximum = 10, muted = false),
                    expanded = expanded,
                    onExpandedChanged = { expanded = it },
                    onRefresh = { refreshCalls++ },
                    onToggleMute = {},
                    onVolumeChanged = {},
                )
            }
        }

        rule.onNodeWithContentDescription("音量，当前 50%，未静音").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val callsAfterOpening = refreshCalls
        assertTrue("展开后应立即刷新", callsAfterOpening >= 1)

        rule.mainClock.advanceTimeBy(251L)
        rule.waitForIdle()
        assertTrue("展开期间应持续刷新", refreshCalls > callsAfterOpening)

        rule.runOnIdle { expanded = false }
        rule.waitForIdle()
        val callsAfterClosing = refreshCalls
        rule.mainClock.advanceTimeBy(1_000L)
        rule.waitForIdle()
        assertEquals(callsAfterClosing, refreshCalls)
    }

}
