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
        maxBitmapWidthPx: Int = Int.MAX_VALUE,
        maxBitmapHeightPx: Int = Int.MAX_VALUE,
    ): ImageDecodeSize {
        val safeWidth = viewportWidthPx.coerceAtLeast(1)
        val safeHeight = viewportHeightPx.coerceAtLeast(1)
        val safeMaxWidth =
            maxBitmapWidthPx.coerceAtLeast(1)
        val safeMaxHeight =
            maxBitmapHeightPx.coerceAtLeast(1)
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
        val pixelShrink =
            if (rawPixels > MAX_PIXELS) {
                sqrt(
                    MAX_PIXELS.toDouble() /
                        rawPixels.toDouble(),
                )
            } else {
                1.0
            }
        val shrink = minOf(
            1.0,
            pixelShrink,
            safeMaxWidth.toDouble() /
                rawWidth.toDouble(),
            safeMaxHeight.toDouble() /
                rawHeight.toDouble(),
        )
        return ImageDecodeSize(
            widthPx = (rawWidth * shrink)
                .toInt()
                .coerceIn(1, safeMaxWidth),
            heightPx = (rawHeight * shrink)
                .toInt()
                .coerceIn(1, safeMaxHeight),
        )
    }
}
