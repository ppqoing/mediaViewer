package com.local.mediaviewer.image

import androidx.compose.ui.geometry.Offset

data class ZoomTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

object ZoomReducer {
    fun gesture(
        current: ZoomTransform,
        zoomChange: Float,
        panChange: Offset,
    ): ZoomTransform {
        val scale =
            (current.scale * zoomChange).coerceIn(1f, 5f)
        if (scale == 1f) {
            return ZoomTransform()
        }
        return ZoomTransform(
            scale = scale,
            offset = current.offset + panChange,
        )
    }

    fun reset(): ZoomTransform = ZoomTransform()
}
