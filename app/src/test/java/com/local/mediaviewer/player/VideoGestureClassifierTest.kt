package com.local.mediaviewer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGestureClassifierTest {
    @Test
    fun `阈值内保持未决定且水平位移选择 seek`() {
        assertEquals(
            VideoGestureAxis.UNDECIDED,
            classify(deltaX = 4f, deltaY = 3f, threshold = 12f),
        )
        assertEquals(
            VideoGestureAxis.SEEK,
            classify(deltaX = 20f, deltaY = 4f, threshold = 12f),
        )
    }

    @Test
    fun `左侧纵向是亮度右侧纵向是音量`() {
        assertEquals(
            VideoGestureAxis.BRIGHTNESS,
            classify(startX = 100f, width = 400f, deltaY = -30f),
        )
        assertEquals(
            VideoGestureAxis.VOLUME,
            classify(startX = 300f, width = 400f, deltaY = -30f),
        )
    }

    private fun classify(
        startX: Float = 200f,
        width: Float = 400f,
        deltaX: Float = 0f,
        deltaY: Float = 0f,
        threshold: Float = 12f,
    ): VideoGestureAxis = VideoGestureClassifier.classify(
        GestureClassificationInput(
            startX = startX,
            width = width,
            deltaX = deltaX,
            deltaY = deltaY,
            thresholdPx = threshold,
        ),
    )
}
