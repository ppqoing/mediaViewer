package com.local.mediaviewer.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.local.mediaviewer.player.GestureClassificationInput
import com.local.mediaviewer.player.PlayerGestureFeedback
import com.local.mediaviewer.player.VideoGestureAxis
import com.local.mediaviewer.player.VideoGestureClassifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VideoGestureLayer(
    enabled: Boolean,
    durationMs: Long,
    positionMs: Long,
    volumeController: PlayerVolumeController,
    brightnessController: PlayerBrightnessController,
    onSingleTap: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onBeginScrub: () -> Unit,
    onPreviewScrub: (Long) -> Unit,
    onCommitScrub: () -> Unit,
    onFeedback: (PlayerGestureFeedback?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnSeekBack by rememberUpdatedState(onSeekBack)
    val currentOnSeekForward by rememberUpdatedState(onSeekForward)
    val currentOnBeginScrub by rememberUpdatedState(onBeginScrub)
    val currentOnPreviewScrub by rememberUpdatedState(onPreviewScrub)
    val currentOnCommitScrub by rememberUpdatedState(onCommitScrub)
    val currentOnFeedback by rememberUpdatedState(onFeedback)
    var feedback by remember { mutableStateOf<PlayerGestureFeedback?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_gesture_layer")
            .then(
                if (enabled) {
                    Modifier.pointerInput(enabled, durationMs) {
                        var pendingTap: PendingTap? = null
                        var pendingTapJob: Job? = null

                        fun emitFeedback(value: PlayerGestureFeedback?) {
                            feedback = value
                            currentOnFeedback(value)
                        }

                        coroutineScope {
                            awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startBrightness = brightnessController.fraction.value
                            val startVolume = volumeController.state.value
                            var axis = VideoGestureAxis.UNDECIDED
                            var changed: PointerInputChange? = down

                            while (changed != null && changed.pressed) {
                                val event = awaitPointerEvent()
                                changed = event.changes.firstOrNull { it.id == down.id }
                                if (changed == null) break
                                val deltaX = changed.position.x - down.position.x
                                val deltaY = changed.position.y - down.position.y
                                if (axis == VideoGestureAxis.UNDECIDED) {
                                    axis = VideoGestureClassifier.classify(
                                        GestureClassificationInput(
                                            startX = down.position.x,
                                            width = size.width.toFloat(),
                                            deltaX = deltaX,
                                            deltaY = deltaY,
                                            thresholdPx = viewConfiguration.touchSlop,
                                        ),
                                    )
                                    if (axis == VideoGestureAxis.SEEK) currentOnBeginScrub()
                                }
                                when (axis) {
                                    VideoGestureAxis.SEEK -> {
                                        val target = previewTarget(positionMs, durationMs, deltaX, size.width.toFloat())
                                        currentOnPreviewScrub(target)
                                        emitFeedback(PlayerGestureFeedback.Seek(target, target - positionMs))
                                        changed.consume()
                                    }

                                    VideoGestureAxis.BRIGHTNESS -> {
                                        val fraction = (startBrightness - deltaY / size.height).coerceIn(0f, 1f)
                                        brightnessController.setFraction(fraction)
                                        emitFeedback(PlayerGestureFeedback.Brightness((fraction * 100).roundToInt()))
                                        changed.consume()
                                    }

                                    VideoGestureAxis.VOLUME -> {
                                        val fraction = (
                                            startVolume.current.toFloat() / startVolume.maximum - deltaY / size.height
                                        ).coerceIn(0f, 1f)
                                        volumeController.setFraction(fraction)
                                        val state = volumeController.state.value
                                        emitFeedback(PlayerGestureFeedback.Volume(state.percent, state.muted))
                                        changed.consume()
                                    }

                                    VideoGestureAxis.UNDECIDED -> Unit
                                }
                            }

                            val endedWithUp = changed?.changedToUpIgnoreConsumed() == true
                            when {
                                axis == VideoGestureAxis.SEEK && endedWithUp -> currentOnCommitScrub()
                                axis == VideoGestureAxis.SEEK -> {
                                    currentOnPreviewScrub(positionMs)
                                    emitFeedback(null)
                                }

                                axis != VideoGestureAxis.UNDECIDED && !endedWithUp -> emitFeedback(null)
                                endedWithUp -> {
                                    val previous = pendingTap
                                    val elapsed = changed.uptimeMillis - (previous?.upTime ?: Long.MIN_VALUE)
                                    if (previous != null && elapsed <= viewConfiguration.doubleTapTimeoutMillis) {
                                        pendingTapJob?.cancel()
                                        pendingTap = null
                                        if (previous.startX < size.width / 2f) {
                                            currentOnSeekBack()
                                            emitFeedback(
                                                PlayerGestureFeedback.Seek(
                                                    (positionMs - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0L),
                                                    -DOUBLE_TAP_SEEK_MS,
                                                ),
                                            )
                                        } else {
                                            currentOnSeekForward()
                                            emitFeedback(
                                                PlayerGestureFeedback.Seek(
                                                    (positionMs + DOUBLE_TAP_SEEK_MS).coerceAtMost(durationMs),
                                                    DOUBLE_TAP_SEEK_MS,
                                                ),
                                            )
                                        }
                                    } else {
                                        pendingTapJob?.cancel()
                                        pendingTap = PendingTap(down.position.x, changed.uptimeMillis)
                                        pendingTapJob = launch {
                                            delay(viewConfiguration.doubleTapTimeoutMillis)
                                            if (pendingTap?.upTime == changed.uptimeMillis) {
                                                pendingTap = null
                                                currentOnSingleTap()
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlayerGestureFeedbackOverlay(feedback)
    }
}

private data class PendingTap(
    val startX: Float,
    val upTime: Long,
)

private fun previewTarget(
    positionMs: Long,
    durationMs: Long,
    deltaX: Float,
    width: Float,
): Long {
    if (width <= 0f) return positionMs
    return (positionMs + deltaX / width * durationMs)
        .roundToInt()
        .toLong()
        .coerceIn(0L, durationMs)
}

private const val DOUBLE_TAP_SEEK_MS = 10_000L
