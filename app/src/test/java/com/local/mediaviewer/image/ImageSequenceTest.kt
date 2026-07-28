package com.local.mediaviewer.image

import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageSequenceTest {
    @Test
    fun `只保留图片并默认名称升序`() {
        val entries = listOf(
            entry("10.jpg", MediaKind.IMAGE),
            entry("folder", MediaKind.DIRECTORY),
            entry("2.png", MediaKind.IMAGE),
            entry("movie.mp4", MediaKind.VIDEO),
        )

        assertEquals(
            listOf("10.jpg", "2.png"),
            ImageSequence.fromEntries(
                entries,
                ImageSortOrder.NAME_ASC,
            ).map(ImageReaderItem::name),
        )
    }

    @Test
    fun `六种排序使用明确主键`() {
        val items = listOf(
            item(
                name = "a.jpg",
                size = 30L,
                modifiedAt = "2026-07-28T03:00:00Z",
            ),
            item(
                name = "b.jpg",
                size = 10L,
                modifiedAt = "2026-07-28T01:00:00Z",
            ),
            item(
                name = "c.jpg",
                size = 20L,
                modifiedAt = "2026-07-28T02:00:00Z",
            ),
        )

        assertOrder(
            items,
            ImageSortOrder.NAME_ASC,
            "a.jpg",
            "b.jpg",
            "c.jpg",
        )
        assertOrder(
            items,
            ImageSortOrder.NAME_DESC,
            "c.jpg",
            "b.jpg",
            "a.jpg",
        )
        assertOrder(
            items,
            ImageSortOrder.MODIFIED_ASC,
            "b.jpg",
            "c.jpg",
            "a.jpg",
        )
        assertOrder(
            items,
            ImageSortOrder.MODIFIED_DESC,
            "a.jpg",
            "c.jpg",
            "b.jpg",
        )
        assertOrder(
            items,
            ImageSortOrder.SIZE_ASC,
            "b.jpg",
            "c.jpg",
            "a.jpg",
        )
        assertOrder(
            items,
            ImageSortOrder.SIZE_DESC,
            "a.jpg",
            "c.jpg",
            "b.jpg",
        )
    }

    @Test
    fun `相同时间和大小按名称及逻辑 URL 稳定排序`() {
        val sameTime = "2026-07-28T00:00:00Z"
        val items = listOf(
            item(
                name = "a.jpg",
                size = 1L,
                modifiedAt = sameTime,
                logicalUrl = "logical-z",
            ),
            item(
                name = "A.jpg",
                size = 1L,
                modifiedAt = sameTime,
                logicalUrl = "logical-a",
            ),
            item(
                name = "same.jpg",
                size = 1L,
                modifiedAt = sameTime,
                logicalUrl = "logical-2",
            ),
            item(
                name = "same.jpg",
                size = 1L,
                modifiedAt = sameTime,
                logicalUrl = "logical-1",
            ),
        )

        val expected = listOf(
            "logical-a",
            "logical-z",
            "logical-1",
            "logical-2",
        )
        assertEquals(
            expected,
            ImageSequence.sort(
                items,
                ImageSortOrder.MODIFIED_ASC,
            ).map(ImageReaderItem::logicalUrl),
        )
        assertEquals(
            expected,
            ImageSequence.sort(
                items,
                ImageSortOrder.MODIFIED_DESC,
            ).map(ImageReaderItem::logicalUrl),
        )
        assertEquals(
            expected,
            ImageSequence.sort(
                items,
                ImageSortOrder.SIZE_ASC,
            ).map(ImageReaderItem::logicalUrl),
        )
        assertEquals(
            expected,
            ImageSequence.sort(
                items,
                ImageSortOrder.SIZE_DESC,
            ).map(ImageReaderItem::logicalUrl),
        )
    }

    @Test
    fun `名称降序反转名称但同名逻辑 URL 仍稳定`() {
        val items = listOf(
            item(
                name = "A.jpg",
                logicalUrl = "logical-a",
            ),
            item(
                name = "a.jpg",
                logicalUrl = "logical-z",
            ),
            item(
                name = "same.jpg",
                logicalUrl = "logical-2",
            ),
            item(
                name = "same.jpg",
                logicalUrl = "logical-1",
            ),
        )

        assertEquals(
            listOf(
                "logical-1",
                "logical-2",
                "logical-z",
                "logical-a",
            ),
            ImageSequence.sort(
                items,
                ImageSortOrder.NAME_DESC,
            ).map(ImageReaderItem::logicalUrl),
        )
    }

    @Test
    fun `锚点存在时保留否则回退首图`() {
        val items = listOf(
            item("a.jpg", logicalUrl = "logical-a"),
            item("b.jpg", logicalUrl = "logical-b"),
        )

        assertEquals(
            "logical-b",
            ImageSequence.anchorOrFirst(
                items,
                "logical-b",
            ),
        )
        assertEquals(
            "logical-a",
            ImageSequence.anchorOrFirst(
                items,
                "missing",
            ),
        )
        assertNull(
            ImageSequence.anchorOrFirst(
                emptyList(),
                "missing",
            ),
        )
    }

    private fun assertOrder(
        items: List<ImageReaderItem>,
        order: ImageSortOrder,
        vararg expectedNames: String,
    ) {
        assertEquals(
            expectedNames.toList(),
            ImageSequence.sort(items, order)
                .map(ImageReaderItem::name),
        )
    }
}

private fun entry(
    name: String,
    kind: MediaKind,
) = DirectoryEntry(
    name = name,
    size = 1L,
    modifiedAt = Instant.parse("2026-07-28T00:00:00Z"),
    mode = 420L,
    isDirectory = kind == MediaKind.DIRECTORY,
    isSymlink = false,
    logicalUrl = "http://media.example:8080/pik/$name",
    requestUrl = "http://192.0.2.1:8080/pik/$name",
    kind = kind,
)

private fun item(
    name: String,
    size: Long = 1L,
    modifiedAt: String = "2026-07-28T00:00:00Z",
    logicalUrl: String = "logical-$name",
) = ImageReaderItem(
    name = name,
    size = size,
    modifiedAt = Instant.parse(modifiedAt),
    logicalUrl = logicalUrl,
    requestUrl = "request-$logicalUrl",
)
