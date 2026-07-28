package com.local.mediaviewer.image

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomStateTest {
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
