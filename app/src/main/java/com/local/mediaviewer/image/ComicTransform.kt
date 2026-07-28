package com.local.mediaviewer.image

data class ComicTransform(
    val scale: Float = 1f,
    val horizontalOffsetPx: Float = 0f,
)

object ComicTransformReducer {
    fun gesture(
        current: ComicTransform,
        zoomChange: Float,
        panXPx: Float,
        viewportWidthPx: Float,
    ): ComicTransform =
        clamp(
            current = ComicTransform(
                scale = (current.scale * zoomChange)
                    .coerceIn(MIN_SCALE, MAX_SCALE),
                horizontalOffsetPx =
                    current.horizontalOffsetPx + panXPx,
            ),
            viewportWidthPx = viewportWidthPx,
        )

    fun clamp(
        current: ComicTransform,
        viewportWidthPx: Float,
    ): ComicTransform {
        val scale =
            current.scale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (scale <= MIN_SCALE) {
            return ComicTransform()
        }
        val maximumOffset = maxOffset(
            scale = scale,
            viewportWidthPx = viewportWidthPx,
        )
        return ComicTransform(
            scale = scale,
            horizontalOffsetPx =
                current.horizontalOffsetPx.coerceIn(
                    -maximumOffset,
                    maximumOffset,
                ),
        )
    }

    fun reset(): ComicTransform = ComicTransform()

    private fun maxOffset(
        scale: Float,
        viewportWidthPx: Float,
    ): Float =
        (viewportWidthPx * (scale - MIN_SCALE) / 2f)
            .coerceAtLeast(0f)

    private const val MIN_SCALE = 1f
    private const val MAX_SCALE = 5f
}
