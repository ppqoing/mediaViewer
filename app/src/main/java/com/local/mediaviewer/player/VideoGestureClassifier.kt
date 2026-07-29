package com.local.mediaviewer.player

import kotlin.math.abs
import kotlin.math.max

object VideoGestureClassifier {
    fun classify(input: GestureClassificationInput): VideoGestureAxis {
        val absX = abs(input.deltaX)
        val absY = abs(input.deltaY)
        if (max(absX, absY) < input.thresholdPx) {
            return VideoGestureAxis.UNDECIDED
        }
        if (absX > absY) {
            return VideoGestureAxis.SEEK
        }
        return if (input.startX < input.width / 2f) {
            VideoGestureAxis.BRIGHTNESS
        } else {
            VideoGestureAxis.VOLUME
        }
    }
}
