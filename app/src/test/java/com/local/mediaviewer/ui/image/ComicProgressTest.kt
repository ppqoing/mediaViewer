package com.local.mediaviewer.ui.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicProgressTest {
    @Test
    fun `progress value maps to clamped zero based image index`() {
        assertEquals(0, comicProgressIndex(1f, totalCount = 50))
        assertEquals(24, comicProgressIndex(25f, totalCount = 50))
        assertEquals(49, comicProgressIndex(50f, totalCount = 50))
        assertEquals(49, comicProgressIndex(99f, totalCount = 50))
    }
}
