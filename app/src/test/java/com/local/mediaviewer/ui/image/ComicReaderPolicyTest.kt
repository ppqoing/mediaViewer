package com.local.mediaviewer.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicReaderPolicyTest {
    @Test
    fun `选择视口内可见高度最大的图片`() {
        val result = mostVisibleLogicalUrl(
            items = listOf(
                VisibleImageBounds(
                    logicalUrl = "a",
                    offsetPx = -500,
                    sizePx = 700,
                ),
                VisibleImageBounds(
                    logicalUrl = "b",
                    offsetPx = 200,
                    sizePx = 700,
                ),
                VisibleImageBounds(
                    logicalUrl = "c",
                    offsetPx = 900,
                    sizePx = 500,
                ),
            ),
            viewportStartPx = 0,
            viewportEndPx = 1_000,
        )

        assertEquals("b", result)
    }

    @Test
    fun `完全离开视口或空列表没有锚点`() {
        assertNull(
            mostVisibleLogicalUrl(
                items = listOf(
                    VisibleImageBounds(
                        logicalUrl = "outside",
                        offsetPx = 1_100,
                        sizePx = 100,
                    ),
                ),
                viewportStartPx = 0,
                viewportEndPx = 1_000,
            ),
        )
        assertNull(
            mostVisibleLogicalUrl(
                items = emptyList(),
                viewportStartPx = 0,
                viewportEndPx = 1_000,
            ),
        )
    }
}
