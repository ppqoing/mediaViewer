package com.local.mediaviewer.ui.pdf

data class PdfTransform(
    val scale: Float = 1f,
    val horizontalOffsetPx: Float = 0f,
)

object PdfTransformReducer {
    fun gesture(
        current: PdfTransform,
        zoomChange: Float,
        panXPx: Float,
        centroidXPx: Float,
        viewportWidthPx: Float,
    ): PdfTransform {
        val oldScale = current.scale.coerceIn(
            MIN_SCALE,
            MAX_SCALE,
        )
        val newScale = (oldScale * zoomChange).coerceIn(
            MIN_SCALE,
            MAX_SCALE,
        )
        val centroidFromCenter =
            centroidXPx - viewportWidthPx / 2f
        val scaleChange = newScale / oldScale
        val anchoredOffset = centroidFromCenter -
            (centroidFromCenter - current.horizontalOffsetPx) *
            scaleChange

        return clamp(
            current = PdfTransform(
                scale = newScale,
                horizontalOffsetPx = anchoredOffset + panXPx,
            ),
            viewportWidthPx = viewportWidthPx,
        )
    }

    fun clamp(
        current: PdfTransform,
        viewportWidthPx: Float,
    ): PdfTransform {
        val scale = current.scale.coerceIn(
            MIN_SCALE,
            MAX_SCALE,
        )
        if (scale <= MIN_SCALE) return PdfTransform()

        val maximumOffset =
            (viewportWidthPx * (scale - MIN_SCALE) / 2f)
                .coerceAtLeast(0f)
        return PdfTransform(
            scale = scale,
            horizontalOffsetPx =
                current.horizontalOffsetPx.coerceIn(
                    -maximumOffset,
                    maximumOffset,
                ),
        )
    }

    private const val MIN_SCALE = 1f
    private const val MAX_SCALE = 5f
}
