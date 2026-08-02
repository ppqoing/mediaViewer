package com.local.mediaviewer.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfReaderPolicyTest {
    @Test
    fun `可见面积最大的页面成为当前页`() {
        assertEquals(
            1,
            mostVisiblePdfPage(
                items = listOf(
                    VisiblePdfPage(0, -500, 700),
                    VisiblePdfPage(1, 200, 700),
                ),
                viewportStartPx = 0,
                viewportEndPx = 1_000,
            ),
        )
    }

    @Test
    fun `页面面积相同时稳定选择列表中的前一页`() {
        assertEquals(
            3,
            mostVisiblePdfPage(
                items = listOf(
                    VisiblePdfPage(3, -200, 400),
                    VisiblePdfPage(4, 800, 400),
                ),
                viewportStartPx = 0,
                viewportEndPx = 1_000,
            ),
        )
    }

    @Test
    fun `没有页面与视口相交时不返回当前页`() {
        assertNull(
            mostVisiblePdfPage(
                items = listOf(
                    VisiblePdfPage(0, -300, 100),
                    VisiblePdfPage(1, 1_200, 100),
                ),
                viewportStartPx = 0,
                viewportEndPx = 1_000,
            ),
        )
    }
}
