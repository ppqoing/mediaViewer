package com.local.mediaviewer.ui.image

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.comicTransformGestures(
    onDoubleTap: () -> Unit,
    onGesture: (
        zoomChange: Float,
        panXPx: Float,
    ) -> Unit,
): Modifier = composed {
    val currentOnGesture by
        rememberUpdatedState(onGesture)
    val currentOnDoubleTap by
        rememberUpdatedState(onDoubleTap)
    pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                currentOnDoubleTap()
            },
        )
    }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val pressed =
                        event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        currentOnGesture(
                            event.calculateZoom(),
                            event.calculatePan().x,
                        )
                        event.changes.forEach {
                            it.consume()
                        }
                    }
                } while (
                    event.changes.any { it.pressed }
                )
            }
        }
}
