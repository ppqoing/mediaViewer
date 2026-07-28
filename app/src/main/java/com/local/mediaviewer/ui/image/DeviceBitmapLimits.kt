package com.local.mediaviewer.ui.image

import android.graphics.Canvas

internal data class DeviceBitmapLimits(
    val maxWidthPx: Int,
    val maxHeightPx: Int,
)

internal fun queryDeviceBitmapLimits():
    DeviceBitmapLimits {
    val canvas = Canvas()
    return DeviceBitmapLimits(
        maxWidthPx =
            canvas.maximumBitmapWidth.coerceAtLeast(1),
        maxHeightPx =
            canvas.maximumBitmapHeight.coerceAtLeast(1),
    )
}
