package com.local.mediaviewer.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomStateTest {
    @Test
    fun `偏心手势缩放保持手指下的内容锚定`() {
        val zoomed = ZoomReducer.gesture(
            current = ZoomTransform(),
            zoomChange = 2f,
            panChange = Offset.Zero,
            centroid = Offset(750f, 250f),
            viewportSize = Size(1_000f, 1_000f),
            fittedContentSize = Size(1_000f, 1_000f),
        )

        assertEquals(2f, zoomed.scale, 0.001f)
        assertEquals(-250f, zoomed.offset.x, 0.001f)
        assertEquals(250f, zoomed.offset.y, 0.001f)
    }

    @Test
    fun `非一倍旧缩放按新旧比例保持偏心锚定`() {
        val zoomed = ZoomReducer.gesture(
            current = ZoomTransform(scale = 2f, offset = Offset(100f, -50f)),
            zoomChange = 1.5f,
            panChange = Offset.Zero,
            centroid = Offset(700f, 200f),
            viewportSize = Size(1_000f, 1_000f),
            fittedContentSize = Size(2_000f, 2_000f),
        )

        assertEquals(3f, zoomed.scale, 0.001f)
        assertEquals(50f, zoomed.offset.x, 0.001f)
        assertEquals(75f, zoomed.offset.y, 0.001f)
    }

    @Test
    fun `新缩放接口回到一倍时归零`() {
        val zoomed = ZoomReducer.gesture(
            current = ZoomTransform(scale = 2f, offset = Offset(300f, -200f)),
            zoomChange = 0.1f,
            panChange = Offset(50f, 50f),
            centroid = Offset(700f, 200f),
            viewportSize = Size(1_000f, 1_000f),
            fittedContentSize = Size(1_000f, 1_000f),
        )

        assertEquals(ZoomTransform(), zoomed)
    }

    @Test
    fun `新缩放接口上限五倍并钳制偏移`() {
        val zoomed = ZoomReducer.gesture(
            current = ZoomTransform(scale = 4f, offset = Offset(1_500f, 0f)),
            zoomChange = 2f,
            panChange = Offset(1_000f, 0f),
            centroid = Offset(500f, 500f),
            viewportSize = Size(1_000f, 1_000f),
            fittedContentSize = Size(1_000f, 1_000f),
        )

        assertEquals(5f, zoomed.scale, 0.001f)
        assertEquals(2_000f, zoomed.offset.x, 0.001f)
    }

    @Test
    fun `较窄的适配内容分别钳制横纵偏移`() {
        val clamped = ZoomReducer.clamp(
            current = ZoomTransform(
                scale = 2f,
                offset = Offset(999f, 999f),
            ),
            viewportSize = Size(1_000f, 1_000f),
            fittedContentSize = Size(400f, 600f),
        )

        assertEquals(2f, clamped.scale, 0.001f)
        assertEquals(0f, clamped.offset.x, 0.001f)
        assertEquals(100f, clamped.offset.y, 0.001f)
    }

    @Test
    fun `零尺寸内容不保留越界偏移`() {
        val clamped = ZoomReducer.clamp(
            current = ZoomTransform(
                scale = 2f,
                offset = Offset(999f, -999f),
            ),
            viewportSize = Size.Zero,
            fittedContentSize = Size.Zero,
        )

        assertEquals(2f, clamped.scale, 0.001f)
        assertEquals(Offset.Zero, clamped.offset)
    }

    @Test
    fun `缩放限制在一到五倍且一倍时偏移归零`() {
        val zoomed = ZoomReducer.gesture(
            current = ZoomTransform(),
            zoomChange = 10f,
            panChange = Offset(20f, -10f),
        )
        assertEquals(5f, zoomed.scale)
        assertEquals(Offset(20f, -10f), zoomed.offset)

        val resetByZoom = ZoomReducer.gesture(
            current = zoomed,
            zoomChange = 0.01f,
            panChange = Offset(100f, 100f),
        )
        assertEquals(ZoomTransform(), resetByZoom)
        assertEquals(ZoomTransform(), ZoomReducer.reset())
    }
}
