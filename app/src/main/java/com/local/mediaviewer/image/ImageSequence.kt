package com.local.mediaviewer.image

import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind

object ImageSequence {
    fun fromEntries(
        entries: List<DirectoryEntry>,
        order: ImageSortOrder,
    ): List<ImageReaderItem> =
        entries
            .asSequence()
            .filter { it.kind == MediaKind.IMAGE }
            .map { entry ->
                ImageReaderItem(
                    name = entry.name,
                    size = entry.size,
                    modifiedAt = entry.modifiedAt,
                    logicalUrl = entry.logicalUrl,
                    requestUrl = entry.requestUrl,
                )
            }
            .toList()
            .sortedWith(comparator(order))

    fun sort(
        items: List<ImageReaderItem>,
        order: ImageSortOrder,
    ): List<ImageReaderItem> =
        items.sortedWith(comparator(order))

    fun anchorOrFirst(
        items: List<ImageReaderItem>,
        requestedLogicalUrl: String,
    ): String? =
        items.firstOrNull {
            it.logicalUrl == requestedLogicalUrl
        }?.logicalUrl ?: items.firstOrNull()?.logicalUrl

    private fun compareNames(
        left: ImageReaderItem,
        right: ImageReaderItem,
        descending: Boolean,
    ): Int {
        val primary =
            String.CASE_INSENSITIVE_ORDER
                .compare(left.name, right.name)
        return (
            if (descending) {
                -primary
            } else {
                primary
            }
        )
            .takeIf { it != 0 }
            ?: left.name.compareTo(right.name)
                .takeIf { it != 0 }
            ?: left.logicalUrl.compareTo(right.logicalUrl)
    }

    private val nameAscending =
        Comparator<ImageReaderItem> { left, right ->
            compareNames(
                left = left,
                right = right,
                descending = false,
            )
        }

    private fun comparator(
        order: ImageSortOrder,
    ): Comparator<ImageReaderItem> =
        when (order) {
            ImageSortOrder.NAME_ASC -> nameAscending
            ImageSortOrder.NAME_DESC ->
                Comparator { left, right ->
                    compareNames(
                        left = left,
                        right = right,
                        descending = true,
                    )
                }
            ImageSortOrder.MODIFIED_ASC ->
                compareBy<ImageReaderItem>(
                    ImageReaderItem::modifiedAt,
                ).then(nameAscending)
            ImageSortOrder.MODIFIED_DESC ->
                compareByDescending<ImageReaderItem>(
                    ImageReaderItem::modifiedAt,
                ).then(nameAscending)
            ImageSortOrder.SIZE_ASC ->
                compareBy<ImageReaderItem>(
                    ImageReaderItem::size,
                ).then(nameAscending)
            ImageSortOrder.SIZE_DESC ->
                compareByDescending<ImageReaderItem>(
                    ImageReaderItem::size,
                ).then(nameAscending)
        }
}
