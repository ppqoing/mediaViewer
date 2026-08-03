package com.local.mediaviewer.ui.image

import kotlin.math.roundToInt

data class ComicJumpCommand(
    val id: Long,
    val targetIndex: Int,
)

internal fun comicProgressIndex(
    value: Float,
    totalCount: Int,
): Int = (value.roundToInt() - 1)
    .coerceIn(0, (totalCount - 1).coerceAtLeast(0))
