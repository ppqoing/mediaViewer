package com.local.mediaviewer.ui.image

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.trackReaderInteraction(
    onBeginInteraction: () -> Unit,
    onEndInteraction: () -> Unit,
): Modifier = composed {
    val currentOnBeginInteraction by
        rememberUpdatedState(onBeginInteraction)
    val currentOnEndInteraction by
        rememberUpdatedState(onEndInteraction)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentOnBeginInteraction()
            try {
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
            } finally {
                currentOnEndInteraction()
            }
        }
    }
}
