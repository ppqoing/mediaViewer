package com.local.mediaviewer.ui.image

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.comicTransformGestures(
    onGesture: (
        zoomChange: Float,
        panXPx: Float,
    ) -> Unit,
): Modifier = pointerInput(onGesture) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressed =
                event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                onGesture(
                    event.calculateZoom(),
                    event.calculatePan().x,
                )
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
