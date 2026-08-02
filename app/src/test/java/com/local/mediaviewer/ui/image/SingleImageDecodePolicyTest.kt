package com.local.mediaviewer.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleImageDecodePolicyTest {
    @Test
    fun `animated gif keeps same decode size across pinch zoom`() {
        val original = SingleImageDecodePolicy.target(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            scale = 1f,
            animatedGif = true,
        )
        val zoomed = SingleImageDecodePolicy.target(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            scale = 4f,
            animatedGif = true,
        )

        assertEquals(original, zoomed)
    }

    @Test
    fun `static image keeps existing scale decode buckets`() {
        val original = SingleImageDecodePolicy.target(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            scale = 1f,
            animatedGif = false,
        )
        val zoomed = SingleImageDecodePolicy.target(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            scale = 2f,
            animatedGif = false,
        )

        assertNotEquals(original, zoomed)
    }

    @Test
    fun `nearest content type selection stays inside available images`() {
        val names = listOf(
            "cover.jpg",
            "motion.gif",
            "page.png",
            "loop.GIF",
        )

        assertEquals(
            1,
            nearestSingleImageIndex(
                itemNames = names,
                currentIndex = 0,
                animatedGif = true,
            ),
        )
        assertEquals(
            2,
            nearestSingleImageIndex(
                itemNames = names,
                currentIndex = 3,
                animatedGif = false,
            ),
        )
        assertNull(
            nearestSingleImageIndex(
                itemNames = listOf("still.jpg"),
                currentIndex = 0,
                animatedGif = true,
            ),
        )
    }
}
