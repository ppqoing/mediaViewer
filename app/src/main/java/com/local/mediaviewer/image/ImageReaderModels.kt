package com.local.mediaviewer.image

import java.time.Instant

enum class ImageSortOrder {
    NAME_ASC,
    NAME_DESC,
    MODIFIED_ASC,
    MODIFIED_DESC,
    SIZE_ASC,
    SIZE_DESC,
}

data class ImageReaderItem(
    val name: String,
    val size: Long,
    val modifiedAt: Instant,
    val logicalUrl: String,
    val requestUrl: String,
)
