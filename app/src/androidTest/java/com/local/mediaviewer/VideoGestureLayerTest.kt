package com.local.mediaviewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.local.mediaviewer.ui.player.PlayerBrightnessController
import com.local.mediaviewer.ui.player.PlayerVolumeController
import com.local.mediaviewer.ui.player.VideoGestureLayer
import com.local.mediaviewer.ui.player.VolumeState
import com.local.mediaviewer.player.PlayerGestureFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VideoGestureLayerTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun doubleTapUsesFirstTapSideAndDoesNotAlsoTriggerSingleTap() {
        var singleTapCalls = 0
        var seekBackCalls = 0
        var seekForwardCalls = 0
        setGestureLayer(
            onSingleTap = { singleTapCalls++ },
            onSeekBack = { seekBackCalls++ },
            onSeekForward = { seekForwardCalls++ },
        )

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.25f, height * 0.5f))
            up()
            advanceEventTime(100)
            down(Offset(width * 0.25f, height * 0.5f))
            up()
        }
        rule.runOnIdle {
            assertEquals(1, seekBackCalls)
            assertEquals(0, singleTapCalls)
        }

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.75f, height * 0.5f))
            up()
            advanceEventTime(100)
            down(Offset(width * 0.75f, height * 0.5f))
            up()
        }
        rule.runOnIdle {
            assertEquals(1, seekForwardCalls)
            assertEquals(0, singleTapCalls)
        }
    }

    @Test
    fun horizontalDragPreviewsMoreThanOnceAndCommitsExactlyOnceOnUp() {
        var beginCalls = 0
        var previewCalls = 0
        var commitCalls = 0
        setGestureLayer(
            onBeginScrub = { beginCalls++ },
            onPreviewScrub = { previewCalls++ },
            onCommitScrub = { commitCalls++ },
        )

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            val start = Offset(width * 0.4f, height * 0.5f)
            down(start)
            moveTo(Offset(width * 0.55f, height * 0.5f))
            moveTo(Offset(width * 0.7f, height * 0.5f))
            up()
        }

        rule.runOnIdle {
            assertEquals(1, beginCalls)
            assertTrue(previewCalls >= 2)
            assertEquals(1, commitCalls)
        }
    }

    @Test
    fun verticalDragsUseLeftForBrightnessAndRightForVolume() {
        val brightness = FakeBrightnessController(0.5f)
        val volume = FakeVolumeController(5, 10)
        setGestureLayer(
            volumeController = volume,
            brightnessController = brightness,
        )

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.25f, height * 0.7f))
            moveTo(Offset(width * 0.25f, height * 0.3f))
        }
        rule.onNodeWithTag("gesture_brightness_rail").assertIsDisplayed()
        rule.onNodeWithTag("gesture_brightness_rail")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("亮度"),
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "90%",
                ),
            )
        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            up()
        }
        rule.onNodeWithTag("gesture_brightness_rail").assertIsDisplayed()
        rule.runOnIdle { assertTrue(brightness.fraction.value > 0.5f) }

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.75f, height * 0.7f))
            moveTo(Offset(width * 0.75f, height * 0.3f))
        }
        rule.onNodeWithTag("gesture_volume_rail").assertIsDisplayed()
        rule.onNodeWithTag("gesture_volume_rail")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("音量"),
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "90%",
                ),
            )
        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            up()
        }
        rule.onNodeWithTag("gesture_volume_rail").assertIsDisplayed()
        rule.runOnIdle { assertTrue(volume.state.value.current > 5) }
    }

    @Test
    fun normalVerticalGestureReleaseLeavesFeedbackForScreenOwner() {
        rule.mainClock.autoAdvance = false
        var singleTapCalls = 0
        val feedbackEvents = mutableListOf<PlayerGestureFeedback?>()
        setGestureLayer(
            onSingleTap = { singleTapCalls++ },
            onFeedback = { feedbackEvents += it },
        )

        rule.onNodeWithTag("video_gesture_layer").performTouchInput {
            down(Offset(width * 0.75f, height * 0.7f))
            moveTo(Offset(width * 0.75f, height * 0.3f))
            up()
        }

        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("gesture_volume_rail").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(1_000L)
        rule.runOnIdle {
            assertTrue(feedbackEvents.any { it is PlayerGestureFeedback.Volume })
            assertTrue(feedbackEvents.last() is PlayerGestureFeedback.Volume)
            assertEquals(0, singleTapCalls)
        }
        rule.onNodeWithTag("gesture_volume_rail").assertIsDisplayed()
    }

    private fun setGestureLayer(
        volumeController: PlayerVolumeController = FakeVolumeController(5, 10),
        brightnessController: PlayerBrightnessController = FakeBrightnessController(0.5f),
        onSingleTap: () -> Unit = {},
        onSeekBack: () -> Unit = {},
        onSeekForward: () -> Unit = {},
        onBeginScrub: () -> Unit = {},
        onPreviewScrub: (Long) -> Unit = {},
        onCommitScrub: () -> Unit = {},
        onFeedback: (PlayerGestureFeedback?) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                var feedback by remember {
                    mutableStateOf<PlayerGestureFeedback?>(null)
                }
                VideoGestureLayer(
                    enabled = true,
                    durationMs = 100_000L,
                    positionMs = 50_000L,
                    volumeController = volumeController,
                    brightnessController = brightnessController,
                    onSingleTap = onSingleTap,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onBeginScrub = onBeginScrub,
                    onPreviewScrub = onPreviewScrub,
                    onCommitScrub = onCommitScrub,
                    feedback = feedback,
                    onFeedback = { value ->
                        feedback = value
                        onFeedback(value)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private class FakeBrightnessController(initial: Float) : PlayerBrightnessController {
    private val mutableFraction = MutableStateFlow(initial)
    override val fraction: StateFlow<Float> = mutableFraction

    override fun setFraction(value: Float) {
        mutableFraction.value = value.coerceIn(0f, 1f)
    }

    override fun adjustByFraction(delta: Float) {
        setFraction(fraction.value + delta)
    }

    override fun close() = Unit
}

private class FakeVolumeController(current: Int, maximum: Int) : PlayerVolumeController {
    private val mutableState = MutableStateFlow(VolumeState(current, maximum, current == 0))
    override val state: StateFlow<VolumeState> = mutableState

    override fun refresh() = Unit

    override fun setFraction(value: Float) {
        val maximum = state.value.maximum
        val next = (value.coerceIn(0f, 1f) * maximum).toInt()
        mutableState.value = VolumeState(next, maximum, next == 0)
    }

    override fun adjustByFraction(delta: Float) {
        val currentFraction = state.value.current.toFloat() / state.value.maximum
        setFraction(currentFraction + delta)
    }

    override fun toggleMute() = Unit
}
