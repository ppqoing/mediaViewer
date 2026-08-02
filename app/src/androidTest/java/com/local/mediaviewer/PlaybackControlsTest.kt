package com.local.mediaviewer

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlin.math.roundToInt

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
    fun timelineUsesSeparateStableCurrentAndDurationLabels() {
        rule.setContent {
            MediaViewerTheme {
                PlayerControls(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
                        status = PlaybackStatus.PAUSED,
                        positionMs = 5_000L,
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
        rule.onNodeWithText("00:05").assertIsDisplayed()
        rule.onNodeWithText("01:00").assertIsDisplayed()
        rule.onNodeWithText("00:05 / 01:00").assertDoesNotExist()
    }

    @Test
    fun sharedControlsLayerTimelineTransportAndUtilitiesByFrequency() {
        rule.setContent {
            MaterialTheme {
                PlayerControls(
                    state = PlayerUiState(
                        name = "视频.mp4",
                        kind = MediaKind.VIDEO,
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
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("utility_probe"),
                    )
                }
            }
        }

        val timeline = rule.onNodeWithTag("player_timeline_layer")
            .fetchSemanticsNode().boundsInRoot
        val transport = rule.onNodeWithTag("player_transport_layer")
            .fetchSemanticsNode().boundsInRoot
        val utilities = rule.onNodeWithTag("player_utility_layer")
            .fetchSemanticsNode().boundsInRoot
        val startGroup = rule.onNodeWithTag("player_utility_start_group")
            .fetchSemanticsNode().boundsInRoot
        val endGroup = rule.onNodeWithTag("player_utility_end_group")
            .fetchSemanticsNode().boundsInRoot
        val primary = rule.onNodeWithContentDescription("播放")
            .fetchSemanticsNode().boundsInRoot
        val seekBack = rule.onNodeWithContentDescription("快退 10 秒")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(timeline.bottom <= transport.top)
        assertTrue(transport.bottom <= utilities.top)
        assertTrue(startGroup.right <= endGroup.left)
        assertTrue(primary.width > seekBack.width)
    }

    @Test
    fun ordinaryPrimaryUsesWarmSixtyFourDpAction() {
        showOrdinaryPrimary(PlaybackStatus.PAUSED, {}, {}, {})

        val primary = rule.onNodeWithTag("playback_primary_action")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val icon = rule.onNodeWithTag(
            "playback_primary_icon",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("普通主动作宽度至少 64dp", primary.width >= 64f)
        assertTrue("普通主动作高度至少 64dp", primary.height >= 64f)
        assertEquals(32.0, icon.width.toDouble(), 0.5)
        assertEquals(32.0, icon.height.toDouble(), 0.5)
    }

    @Test
    fun darkWarmPrimaryMaintainsThreeToOneIconContrast() {
        rule.setContent {
            MediaViewerTheme(darkTheme = true) {
                PlayerControls(
                    state = PlayerUiState(
                        name = "movie.mp4",
                        kind = MediaKind.VIDEO,
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
                )
            }
        }

        val pixels = rule.onNodeWithContentDescription("播放")
            .captureToImage()
            .toPixelMap()
        val background = pixels[8, pixels.height / 2]
        var strongestContrast = 1.0
        for (y in pixels.height / 4 until pixels.height * 3 / 4) {
            for (x in pixels.width / 4 until pixels.width * 3 / 4) {
                strongestContrast = maxOf(
                    strongestContrast,
                    contrastRatio(background, pixels[x, y]),
                )
            }
        }
        assertTrue(
            "深色暖纸主动作图标对比度应至少 3:1，实际 $strongestContrast",
            strongestContrast >= 3.0,
        )
    }

    @Test
    fun volumeButtonOpensTrueVerticalVolumePopup() {
        var expanded by mutableStateOf(false)
        rule.setContent {
            MediaViewerTheme {
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
    }

    @Test
    fun volumeTriggerDoesNotToggleMute() {
        var expanded by mutableStateOf(false)
        var muteCalls = 0
        rule.setContent {
            MediaViewerTheme {
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
        rule.onNodeWithTag("volume_slider_vertical").assertIsDisplayed()
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
            MediaViewerTheme {
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

    @Test
    fun volumePopupClosesAfterThreeSecondsWithoutInteraction() {
        rule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(false)
        var dismissCalls = 0
        rule.setContent {
            MediaViewerTheme {
                PlaybackVolumeControl(
                    state = VolumeState(5, 10, false),
                    expanded = expanded,
                    onExpandedChanged = { nextExpanded ->
                        if (!nextExpanded) {
                            dismissCalls += 1
                        }
                        expanded = nextExpanded
                    },
                    onRefresh = {},
                    onToggleMute = {},
                    onVolumeChanged = {},
                )
            }
        }

        rule.onNodeWithContentDescription("音量，当前 50%，未静音")
            .performClick()
        // Start the expanded composition and its LaunchedEffect before
        // measuring the idle interval.
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(
            milliseconds = 2_999L,
            ignoreFrameDuration = true,
        )
        rule.onNodeWithTag("volume_popup").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(
            milliseconds = 2L,
            ignoreFrameDuration = true,
        )
        // One frame resumes the deadline coroutine; the next applies the
        // parent's expanded=false state.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(
                "3-second idle deadline must invoke onExpandedChanged(false)",
                1,
                dismissCalls,
            )
            assertTrue(!expanded)
        }
        // DropdownMenu keeps its independent popup composition alive for its
        // Material exit transition after expanded becomes false.
        rule.mainClock.advanceTimeBy(500L)
        rule.waitForIdle()
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
        // Parent-owned volume state becomes observable to the control on
        // the next composition frame; the idle deadline starts there.
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onNodeWithText("80%").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(
            milliseconds = 2_999L,
            ignoreFrameDuration = true,
        )
        rule.onNodeWithTag("volume_popup").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(
            milliseconds = 2L,
            ignoreFrameDuration = true,
        )
        // One frame resumes the deadline coroutine; the next applies the
        // parent's expanded=false state.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(!expanded) }
        rule.mainClock.advanceTimeBy(500L)
        rule.waitForIdle()
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
        // The mute result is observed by the control on this frame.
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("取消静音").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(
            // Leave enough time for the next frame to observe the external
            // state update before this mute-reset deadline can expire.
            milliseconds = 2_980L,
            ignoreFrameDuration = true,
        )
        rule.onNodeWithTag("volume_popup").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, muteCalls)
            state = state.copy(current = 7)
        }
        // External state resets the deadline once the child observes it.
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("音量，当前 70%，已静音")
            .assertIsDisplayed()
        rule.mainClock.advanceTimeBy(
            milliseconds = 2_999L,
            ignoreFrameDuration = true,
        )
        rule.onNodeWithTag("volume_popup").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(
            milliseconds = 2L,
            ignoreFrameDuration = true,
        )
        // One frame resumes the deadline coroutine; the next applies the
        // parent's expanded=false state.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(!expanded) }
        rule.mainClock.advanceTimeBy(500L)
        rule.waitForIdle()
        rule.onNodeWithTag("volume_popup").assertDoesNotExist()
    }

    @Test
    fun systemBackClosesVolumePopupWithoutLeavingPlayer() {
        rule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(false)
        var dismissCalls = 0
        rule.setContent {
            MediaViewerTheme {
                PlaybackVolumeControl(
                    state = VolumeState(5, 10, false),
                    expanded = expanded,
                    onExpandedChanged = { nextExpanded ->
                        if (!nextExpanded) {
                            dismissCalls += 1
                        }
                        expanded = nextExpanded
                    },
                    onRefresh = {},
                    onToggleMute = {},
                    onVolumeChanged = {},
                )
            }
        }
        rule.onNodeWithContentDescription("音量，当前 50%，未静音")
            .performClick()
        rule.mainClock.advanceTimeBy(500L)
        rule.waitForIdle()
        rule.onNodeWithTag("volume_popup").assertIsDisplayed()
        Espresso.pressBack()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, dismissCalls)
            assertTrue(!expanded)
        }
        rule.mainClock.advanceTimeBy(500L)
        rule.waitForIdle()
        rule.onNodeWithTag("volume_popup").assertDoesNotExist()
    }

    @Test
    fun tappingOutsideDismissesVolumePopup() {
        rule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(false)
        var dismissCalls = 0
        rule.setContent {
            MediaViewerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("volume_test_host"),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaybackVolumeControl(
                        state = VolumeState(5, 10, false),
                        expanded = expanded,
                        onExpandedChanged = { nextExpanded ->
                            if (!nextExpanded) {
                                dismissCalls += 1
                            }
                            expanded = nextExpanded
                        },
                        onRefresh = {},
                        onToggleMute = {},
                        onVolumeChanged = {},
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("音量，当前 50%，未静音")
            .performClick()
        rule.mainClock.advanceTimeBy(500L)
        rule.waitForIdle()
        rule.onNodeWithTag("volume_popup").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val displayMetrics = instrumentation.targetContext.resources.displayMetrics
        // The anchor is centered. Ten percent across and twenty percent down
        // stays in app content while remaining well clear of its popup.
        val outsideX = (displayMetrics.widthPixels * 0.10f).roundToInt()
        val outsideY = (displayMetrics.heightPixels * 0.20f).roundToInt()
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "input tap $outsideX $outsideY",
            ),
        ).use { output ->
            output.readBytes()
        }
        instrumentation.waitForIdleSync()
        // The 500 ms enter settle plus this 1,000 ms remains below the
        // 3-second idle deadline, so only the outside tap can dismiss it.
        rule.mainClock.advanceTimeBy(1_000L)
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, dismissCalls)
            assertTrue(!expanded)
        }
        rule.mainClock.advanceTimeBy(500L)
        rule.waitForIdle()
        rule.onNodeWithTag("volume_popup").assertDoesNotExist()
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
        val darker = minOf(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double = if (value <= 0.04045f) {
            value / 12.92
        } else {
            Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
