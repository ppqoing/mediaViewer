package com.local.mediaviewer.image

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ImageDecodeSize(
    val widthPx: Int,
    val heightPx: Int,
)

object ImageDecodePolicy {
    const val MAX_PIXELS = 4_194_304L

    fun target(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        scale: Float,
    ): ImageDecodeSize {
        val safeWidth = viewportWidthPx.coerceAtLeast(1)
        val safeHeight = viewportHeightPx.coerceAtLeast(1)
        val boundedScale = scale.coerceIn(1f, 2f)
        val rawWidth =
            (safeWidth * boundedScale)
                .roundToInt()
                .coerceAtLeast(1)
        val rawHeight =
            safeHeight.toLong()
                .times(4L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        val rawPixels =
            rawWidth.toLong() * rawHeight.toLong()
        val shrink = if (rawPixels > MAX_PIXELS) {
            sqrt(
                MAX_PIXELS.toDouble() /
                    rawPixels.toDouble(),
            )
        } else {
            1.0
        }
        return ImageDecodeSize(
            widthPx = (rawWidth * shrink)
                .toInt()
                .coerceAtLeast(1),
            heightPx = (rawHeight * shrink)
                .toInt()
                .coerceAtLeast(1),
        )
    }
}
