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

    @Test
    fun `设备最大宽高按相同比例约束解码目标`() {
        val target = ImageDecodePolicy.target(
            viewportWidthPx = 8_000,
            viewportHeightPx = 1_000,
            scale = 2f,
            maxBitmapWidthPx = 2_048,
            maxBitmapHeightPx = 300,
        )

        assertEquals(
            ImageDecodeSize(
                widthPx = 1_200,
                heightPx = 300,
            ),
            target,
        )
        assertWithinPixelBudget(target)
    }

    @Test
    fun `条漫解码目标只由视口和设备上限决定`() {
        val target = ImageDecodePolicy.comicTarget(
            viewportWidthPx = 1_440,
            viewportHeightPx = 3_200,
            maxBitmapWidthPx = 2_048,
            maxBitmapHeightPx = 4_096,
        )

        assertEquals(
            ImageDecodePolicy.target(
                viewportWidthPx = 1_440,
                viewportHeightPx = 3_200,
                scale = 1f,
                maxBitmapWidthPx = 2_048,
                maxBitmapHeightPx = 4_096,
            ),
            target,
        )
        assertTrue(target.widthPx <= 2_048)
        assertTrue(target.heightPx <= 4_096)
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
