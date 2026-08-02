package com.local.mediaviewer.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

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

    fun gesture(
        current: ZoomTransform,
        zoomChange: Float,
        panChange: Offset,
        centroid: Offset,
        viewportSize: Size,
        fittedContentSize: Size,
    ): ZoomTransform {
        val oldScale = boundedScale(current.scale)
        val newScale = boundedScale(oldScale * finiteOr(zoomChange, 1f))
        if (newScale <= MIN_SCALE) {
            return ZoomTransform()
        }

        val viewportWidth = nonNegativeFinite(viewportSize.width)
        val viewportHeight = nonNegativeFinite(viewportSize.height)
        val ratio = newScale / oldScale
        val relative = Offset(
            x = finiteOr(centroid.x) - viewportWidth / 2f,
            y = finiteOr(centroid.y) - viewportHeight / 2f,
        )
        val anchoredOffset = Offset(
            x = finiteOr(current.offset.x) * ratio +
                relative.x * (1f - ratio) + finiteOr(panChange.x),
            y = finiteOr(current.offset.y) * ratio +
                relative.y * (1f - ratio) + finiteOr(panChange.y),
        )
        return clamp(
            current = ZoomTransform(newScale, anchoredOffset),
            viewportSize = Size(viewportWidth, viewportHeight),
            fittedContentSize = fittedContentSize,
        )
    }

    fun clamp(
        current: ZoomTransform,
        viewportSize: Size,
        fittedContentSize: Size,
    ): ZoomTransform {
        val scale = boundedScale(current.scale)
        if (scale <= MIN_SCALE) {
            return ZoomTransform()
        }
        val maxX = maxOffset(
            fittedSize = fittedContentSize.width,
            viewportSize = viewportSize.width,
            scale = scale,
        )
        val maxY = maxOffset(
            fittedSize = fittedContentSize.height,
            viewportSize = viewportSize.height,
            scale = scale,
        )
        return ZoomTransform(
            scale = scale,
            offset = Offset(
                x = clampOffset(current.offset.x, maxX),
                y = clampOffset(current.offset.y, maxY),
            ),
        )
    }

    fun reset(): ZoomTransform = ZoomTransform()

    private fun boundedScale(scale: Float): Float =
        finiteOr(scale, MIN_SCALE).coerceIn(MIN_SCALE, MAX_SCALE)

    private fun maxOffset(
        fittedSize: Float,
        viewportSize: Float,
        scale: Float,
    ): Float {
        val extent =
            nonNegativeFinite(fittedSize) * scale -
                nonNegativeFinite(viewportSize)
        return if (extent.isFinite()) {
            (extent / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
    }

    private fun nonNegativeFinite(value: Float): Float =
        finiteOr(value).coerceAtLeast(0f)

    private fun clampOffset(
        value: Float,
        maximum: Float,
    ): Float =
        if (maximum == 0f) 0f else finiteOr(value).coerceIn(-maximum, maximum)

    private fun finiteOr(
        value: Float,
        fallback: Float = 0f,
    ): Float = if (value.isFinite()) value else fallback

    private const val MIN_SCALE = 1f
    private const val MAX_SCALE = 5f
}
