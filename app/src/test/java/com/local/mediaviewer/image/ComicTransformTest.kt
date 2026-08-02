package com.local.mediaviewer.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicTransformTest {
    @Test
    fun `偏心手势缩放保持横向手指锚定`() {
        val zoomed = ComicTransformReducer.gesture(
            current = ComicTransform(),
            zoomChange = 2f,
            panXPx = 0f,
            centroidXPx = 750f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(2f, zoomed.scale, 0.001f)
        assertEquals(-250f, zoomed.horizontalOffsetPx, 0.001f)
    }

    @Test
    fun `非一倍旧缩放按新旧比例保持横向偏心锚定`() {
        val zoomed = ComicTransformReducer.gesture(
            current = ComicTransform(scale = 2f, horizontalOffsetPx = 100f),
            zoomChange = 1.5f,
            panXPx = 0f,
            centroidXPx = 700f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(3f, zoomed.scale, 0.001f)
        assertEquals(50f, zoomed.horizontalOffsetPx, 0.001f)
    }

    @Test
    fun `新连续阅读缩放接口回到一倍时归零`() {
        val zoomed = ComicTransformReducer.gesture(
            current = ComicTransform(scale = 2f, horizontalOffsetPx = 300f),
            zoomChange = 0.1f,
            panXPx = 50f,
            centroidXPx = 700f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(ComicTransform(), zoomed)
    }

    @Test
    fun `新连续阅读缩放接口上限五倍并钳制偏移`() {
        val zoomed = ComicTransformReducer.gesture(
            current = ComicTransform(scale = 4f, horizontalOffsetPx = 1_500f),
            zoomChange = 2f,
            panXPx = 1_000f,
            centroidXPx = 500f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(5f, zoomed.scale, 0.001f)
        assertEquals(2_000f, zoomed.horizontalOffsetPx, 0.001f)
    }

    @Test
    fun `连续阅读锚点记录质心所在项目中的比例并计算纵向校正`() {
        val anchor = captureComicViewportAnchor(
            items = listOf(ComicVisibleItem(index = 4, offsetPx = 100, sizePx = 400)),
            centroidYPx = 250f,
        )

        assertEquals(0.375f, anchor!!.itemFraction, 0.001f)
        assertEquals(
            100f,
            comicScrollCorrectionPx(
                anchor = anchor,
                updatedItem = ComicVisibleItem(index = 4, offsetPx = 50, sizePx = 800),
            ),
            0.001f,
        )
    }

    @Test
    fun `连续阅读质心落在不等尺寸间隙时选择中心最近页面并限制比例`() {
        val anchor = captureComicViewportAnchor(
            items = listOf(
                ComicVisibleItem(index = 1, offsetPx = 0, sizePx = 100),
                ComicVisibleItem(index = 2, offsetPx = 200, sizePx = 500),
            ),
            centroidYPx = 170f,
        )

        assertEquals(1, anchor!!.itemIndex)
        assertEquals(1f, anchor.itemFraction, 0.001f)
    }

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
