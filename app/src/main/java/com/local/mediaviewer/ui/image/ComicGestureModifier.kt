package com.local.mediaviewer.ui.image

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

fun Modifier.comicTransformGestures(
    onDoubleTap: () -> Unit,
    onTap: () -> Unit,
    onGesture: suspend (
        centroid: Offset,
        zoomChange: Float,
        panChange: Offset,
    ) -> Unit,
): Modifier = composed {
    val currentOnGesture by
        rememberUpdatedState(onGesture)
    val currentOnDoubleTap by
        rememberUpdatedState(onDoubleTap)
    val currentOnTap by
        rememberUpdatedState(onTap)
    val gestureScope = rememberCoroutineScope()
    pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                currentOnDoubleTap()
            },
            onTap = {
                currentOnTap()
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
                        val centroid =
                            event.calculateCentroid(
                                useCurrent = false,
                            )
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        gestureScope.launch(
                            start = CoroutineStart.UNDISPATCHED,
                        ) {
                            currentOnGesture(
                                centroid,
                                zoom,
                                pan,
                            )
                        }
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
