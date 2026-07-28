package com.local.mediaviewer.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicTransformTest {
    @Test
    fun `统一缩放限制一到五倍并钳制水平偏移`() {
        val zoomed = ComicTransformReducer.gesture(
            current = ComicTransform(),
            zoomChange = 3f,
            panXPx = 2_000f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(3f, zoomed.scale)
        assertEquals(
            1_000f,
            zoomed.horizontalOffsetPx,
        )

        val resetByZoom = ComicTransformReducer.gesture(
            current = zoomed,
            zoomChange = 0.01f,
            panXPx = 500f,
            viewportWidthPx = 1_000f,
        )
        assertEquals(ComicTransform(), resetByZoom)
    }

    @Test
    fun `负向偏移按当前放大宽度钳制`() {
        val transformed = ComicTransformReducer.gesture(
            current = ComicTransform(scale = 5f),
            zoomChange = 1f,
            panXPx = -9_999f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(
            ComicTransform(
                scale = 5f,
                horizontalOffsetPx = -2_000f,
            ),
            transformed,
        )
    }

    @Test
    fun `视口变化重新钳制已有偏移`() {
        val transformed = ComicTransformReducer.clamp(
            current = ComicTransform(
                scale = 3f,
                horizontalOffsetPx = 1_000f,
            ),
            viewportWidthPx = 500f,
        )

        assertEquals(
            ComicTransform(
                scale = 3f,
                horizontalOffsetPx = 500f,
            ),
            transformed,
        )
    }

    @Test
    fun `显式重置返回一倍且零偏移`() {
        assertEquals(
            ComicTransform(),
            ComicTransformReducer.reset(),
        )
    }
}
