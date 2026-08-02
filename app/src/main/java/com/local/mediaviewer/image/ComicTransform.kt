package com.local.mediaviewer.image

data class ComicTransform(
    val scale: Float = 1f,
    val horizontalOffsetPx: Float = 0f,
)

data class ComicVisibleItem(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

data class ComicViewportAnchor(
    val itemIndex: Int,
    val itemFraction: Float,
    val centroidYPx: Float,
)

object ComicTransformReducer {
    fun gesture(
        current: ComicTransform,
        zoomChange: Float,
        panXPx: Float,
        viewportWidthPx: Float,
    ): ComicTransform =
        gesture(
            current = current,
            zoomChange = zoomChange,
            panXPx = panXPx,
            centroidXPx = nonNegativeFinite(viewportWidthPx) / 2f,
            viewportWidthPx = viewportWidthPx,
        )

    fun gesture(
        current: ComicTransform,
        zoomChange: Float,
        panXPx: Float,
        centroidXPx: Float,
        viewportWidthPx: Float,
    ): ComicTransform =
        clamp(
            current = ComicTransform(
                scale = boundedScale(
                    boundedScale(current.scale) * finiteOr(zoomChange, 1f),
                ),
                horizontalOffsetPx =
                    anchoredOffset(
                        current = current,
                        newScale = boundedScale(
                            boundedScale(current.scale) * finiteOr(zoomChange, 1f),
                        ),
                        panXPx = panXPx,
                        centroidXPx = centroidXPx,
                        viewportWidthPx = viewportWidthPx,
                    ),
            ),
            viewportWidthPx = viewportWidthPx,
        )

    fun clamp(
        current: ComicTransform,
        viewportWidthPx: Float,
    ): ComicTransform {
        val scale = boundedScale(current.scale)
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
                finiteOr(current.horizontalOffsetPx).coerceIn(
                    -maximumOffset,
                    maximumOffset,
                ),
        )
    }

    fun reset(): ComicTransform = ComicTransform()

    private fun maxOffset(
        scale: Float,
        viewportWidthPx: Float,
    ): Float {
        val viewportWidth = nonNegativeFinite(viewportWidthPx)
        val extent = viewportWidth * scale - viewportWidth
        return if (extent.isFinite()) {
            (extent / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
    }

    private fun anchoredOffset(
        current: ComicTransform,
        newScale: Float,
        panXPx: Float,
        centroidXPx: Float,
        viewportWidthPx: Float,
    ): Float {
        val oldScale = boundedScale(current.scale)
        val ratio = newScale / oldScale
        val relative =
            finiteOr(centroidXPx) - nonNegativeFinite(viewportWidthPx) / 2f
        return finiteOr(current.horizontalOffsetPx) * ratio +
            relative * (1f - ratio) + finiteOr(panXPx)
    }

    private fun boundedScale(scale: Float): Float =
        finiteOr(scale, MIN_SCALE).coerceIn(MIN_SCALE, MAX_SCALE)

    private fun nonNegativeFinite(value: Float): Float =
        finiteOr(value).coerceAtLeast(0f)

    private fun finiteOr(
        value: Float,
        fallback: Float = 0f,
    ): Float = if (value.isFinite()) value else fallback

    private const val MIN_SCALE = 1f
    private const val MAX_SCALE = 5f
}

fun captureComicViewportAnchor(
    items: List<ComicVisibleItem>,
    centroidYPx: Float,
): ComicViewportAnchor? {
    if (!centroidYPx.isFinite()) {
        return null
    }
    val selectableItems = items.filter { it.sizePx > 0 }
    val selected =
        selectableItems.firstOrNull { item ->
            centroidYPx >= item.offsetPx.toFloat() &&
                centroidYPx <= item.offsetPx.toFloat() + item.sizePx.toFloat()
        } ?: selectableItems.minWithOrNull(
            compareBy<ComicVisibleItem> {
                kotlin.math.abs(
                    centroidYPx -
                        (it.offsetPx.toFloat() + it.sizePx.toFloat() / 2f),
                )
            }.thenBy { it.index },
        ) ?: return null
    return ComicViewportAnchor(
        itemIndex = selected.index,
        itemFraction =
            (centroidYPx - selected.offsetPx.toFloat()) / selected.sizePx.toFloat(),
        centroidYPx = centroidYPx,
    )
}

fun comicScrollCorrectionPx(
    anchor: ComicViewportAnchor,
    updatedItem: ComicVisibleItem,
): Float {
    if (
        updatedItem.index != anchor.itemIndex ||
        updatedItem.sizePx <= 0 ||
        !anchor.itemFraction.isFinite() ||
        !anchor.centroidYPx.isFinite()
    ) {
        return 0f
    }
    val updatedAnchorYPx =
        updatedItem.offsetPx.toFloat() + updatedItem.sizePx.toFloat() * anchor.itemFraction
    val correction = updatedAnchorYPx - anchor.centroidYPx
    return if (correction.isFinite()) correction else 0f
}
