package com.local.mediaviewer.ui.pdf

import kotlin.math.max
import kotlin.math.min

data class VisiblePdfPage(
    val pageIndex: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

fun mostVisiblePdfPage(
    items: List<VisiblePdfPage>,
    viewportStartPx: Int,
    viewportEndPx: Int,
): Int? = items
    .map { item ->
        val visibleSize =
            min(item.offsetPx + item.sizePx, viewportEndPx) -
                max(item.offsetPx, viewportStartPx)
        item.pageIndex to visibleSize.coerceAtLeast(0)
    }
    .filter { (_, visibleSize) -> visibleSize > 0 }
    .maxByOrNull { (_, visibleSize) -> visibleSize }
    ?.first
