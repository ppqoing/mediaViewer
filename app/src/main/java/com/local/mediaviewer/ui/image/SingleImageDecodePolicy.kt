package com.local.mediaviewer.ui.image

import com.local.mediaviewer.image.ImageDecodePolicy
import com.local.mediaviewer.image.ImageDecodeSize
import kotlin.math.abs

object SingleImageDecodePolicy {
    fun target(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        scale: Float,
        animatedGif: Boolean,
        maxBitmapWidthPx: Int = Int.MAX_VALUE,
        maxBitmapHeightPx: Int = Int.MAX_VALUE,
    ): ImageDecodeSize = ImageDecodePolicy.target(
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        scale = if (animatedGif) 1f else scale,
        maxBitmapWidthPx = maxBitmapWidthPx,
        maxBitmapHeightPx = maxBitmapHeightPx,
    )
}

internal fun isAnimatedGifName(name: String): Boolean =
    name.substringAfterLast(
        delimiter = '.',
        missingDelimiterValue = "",
    ).equals("gif", ignoreCase = true)

internal fun nearestSingleImageIndex(
    itemNames: List<String>,
    currentIndex: Int,
    animatedGif: Boolean,
): Int? = itemNames.indices
    .filter { index ->
        isAnimatedGifName(itemNames[index]) == animatedGif
    }
    .minWithOrNull(
        compareBy<Int> { index ->
            abs(index - currentIndex)
        }.thenBy { it },
    )
