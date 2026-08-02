package com.local.mediaviewer.ui.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun `指针桥接在第三指加入和离开时只使用公共指针的前一中心`() {
        val joining = PointerEvent(
            listOf(
                pointerChange(0, Offset(100f, 100f), Offset(110f, 100f)),
                pointerChange(1, Offset(300f, 100f), Offset(310f, 100f)),
                pointerChange(
                    id = 2,
                    previous = Offset(1_000f, 100f),
                    current = Offset(1_000f, 100f),
                    previousPressed = false,
                    pressed = true,
                ),
            ),
        )

        val joiningSample = joining.toPdfGestureSample()
        assertNotNull(joiningSample)
        assertEquals(
            Offset(200f, 100f),
            joiningSample?.previousCentroid,
        )
        assertEquals(Offset(10f, 0f), joiningSample?.pan)

        val leaving = PointerEvent(
            listOf(
                pointerChange(0, Offset(110f, 100f), Offset(120f, 100f)),
                pointerChange(1, Offset(310f, 100f), Offset(320f, 100f)),
                pointerChange(
                    id = 2,
                    previous = Offset(1_000f, 100f),
                    current = Offset(1_000f, 100f),
                    previousPressed = true,
                    pressed = false,
                ),
            ),
        )

        val leavingSample = leaving.toPdfGestureSample()
        assertNotNull(leavingSample)
        assertEquals(
            Offset(210f, 100f),
            leavingSample?.previousCentroid,
        )
        assertEquals(Offset(10f, 0f), leavingSample?.pan)
    }

    @Test
    fun `指针桥接忽略单指且不消费事件`() {
        val change = pointerChange(
            id = 0,
            previous = Offset(100f, 100f),
            current = Offset(120f, 100f),
        )

        val sample = PointerEvent(listOf(change))
            .toPdfGestureSample()

        assertEquals(null, sample)
        assertFalse(change.isConsumed)
    }

    private fun pointerChange(
        id: Long,
        previous: Offset,
        current: Offset,
        previousPressed: Boolean = true,
        pressed: Boolean = true,
    ): PointerInputChange = PointerInputChange(
        id = PointerId(id),
        uptimeMillis = 16L,
        position = current,
        pressed = pressed,
        previousUptimeMillis = 0L,
        previousPosition = previous,
        previousPressed = previousPressed,
        isInitiallyConsumed = false,
    )
}
