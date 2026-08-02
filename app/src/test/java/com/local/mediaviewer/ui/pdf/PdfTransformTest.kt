package com.local.mediaviewer.ui.pdf

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTransformTest {
    @Test
    fun `缩放围绕手势中心保持文档内容位置`() {
        val zoomed = PdfTransformReducer.gesture(
            current = PdfTransform(),
            zoomChange = 2f,
            panXPx = 0f,
            centroidXPx = 750f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(2f, zoomed.scale, 0.001f)
        assertEquals(
            -250f,
            zoomed.horizontalOffsetPx,
            0.001f,
        )
    }

    @Test
    fun `缩放限制为一到五倍且一倍时水平偏移归零`() {
        val maximum = PdfTransformReducer.gesture(
            current = PdfTransform(),
            zoomChange = 20f,
            panXPx = 0f,
            centroidXPx = 500f,
            viewportWidthPx = 1_000f,
        )
        assertEquals(5f, maximum.scale, 0.001f)

        val reset = PdfTransformReducer.gesture(
            current = maximum,
            zoomChange = 0.01f,
            panXPx = 800f,
            centroidXPx = 500f,
            viewportWidthPx = 1_000f,
        )
        assertEquals(PdfTransform(), reset)
    }

    @Test
    fun `水平拖动限制在放大后溢出宽度的一半`() {
        val transformed = PdfTransformReducer.gesture(
            current = PdfTransform(scale = 5f),
            zoomChange = 1f,
            panXPx = -9_999f,
            centroidXPx = 500f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(
            PdfTransform(
                scale = 5f,
                horizontalOffsetPx = -2_000f,
            ),
            transformed,
        )
    }

    @Test
    fun `平移与缩放中心修正共同作用于水平偏移`() {
        val transformed = PdfTransformReducer.gesture(
            current = PdfTransform(
                scale = 2f,
                horizontalOffsetPx = 100f,
            ),
            zoomChange = 1.5f,
            panXPx = 40f,
            centroidXPx = 400f,
            viewportWidthPx = 1_000f,
        )

        assertEquals(3f, transformed.scale, 0.001f)
        assertEquals(240f, transformed.horizontalOffsetPx, 0.001f)
    }

    @Test
    fun `第三指加入时屏幕适配保持精确变化前中心`() {
        val previousCentroid = Offset(400f, 300f)
        val update = reducePdfScreenGesture(
            current = PdfTransform(
                scale = 2f,
                horizontalOffsetPx = 100f,
            ),
            zoomChange = 1.5f,
            pan = Offset(40f, 12f),
            previousCentroid = previousCentroid,
            viewportWidthPx = 1_000f,
        )

        assertEquals(3f, update.transform.scale, 0.001f)
        assertEquals(
            240f,
            update.transform.horizontalOffsetPx,
            0.001f,
        )
        assertEquals(previousCentroid, update.anchorCentroid)
    }
}
