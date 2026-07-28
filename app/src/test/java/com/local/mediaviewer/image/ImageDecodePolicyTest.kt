package com.local.mediaviewer.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDecodePolicyTest {
    @Test
    fun `一倍使用视口宽且五倍仍限制两倍宽`() {
        assertEquals(
            ImageDecodeSize(1080, 3600),
            ImageDecodePolicy.target(
                viewportWidthPx = 1080,
                viewportHeightPx = 900,
                scale = 1f,
            ),
        )

        val zoomed = ImageDecodePolicy.target(
            viewportWidthPx = 1080,
            viewportHeightPx = 1000,
            scale = 5f,
        )
        assertTrue(zoomed.widthPx <= 2160)
        assertTrue(zoomed.heightPx <= 4000)
        assertWithinPixelBudget(zoomed)
    }

    @Test
    fun `零和负视口至少产生一像素安全目标`() {
        val target = ImageDecodePolicy.target(
            viewportWidthPx = 0,
            viewportHeightPx = -500,
            scale = 1f,
        )

        assertEquals(ImageDecodeSize(1, 4), target)
        assertWithinPixelBudget(target)
    }

    @Test
    fun `整型极值不会溢出且仍受总像素限制`() {
        val target = ImageDecodePolicy.target(
            viewportWidthPx = Int.MAX_VALUE,
            viewportHeightPx = Int.MAX_VALUE,
            scale = 5f,
        )

        assertTrue(target.widthPx > 0)
        assertTrue(target.heightPx > 0)
        assertTrue(target.widthPx <= Int.MAX_VALUE)
        assertTrue(target.heightPx <= Int.MAX_VALUE)
        assertWithinPixelBudget(target)
    }

    private fun assertWithinPixelBudget(
        target: ImageDecodeSize,
    ) {
        assertTrue(
            target.widthPx.toLong() * target.heightPx <=
                ImageDecodePolicy.MAX_PIXELS,
        )
    }
}
