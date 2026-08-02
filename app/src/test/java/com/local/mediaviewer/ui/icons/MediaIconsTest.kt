package com.local.mediaviewer.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIconsTest {
    @Test
    fun `warm paper icon inventory uses unique drawable resources`() {
        assertEquals(36, MediaIcons.all.size)
        assertEquals(MediaIcons.all.size, MediaIcons.all.map { it.resourceId }.distinct().size)
        assertTrue(MediaIcons.all.all { it.resourceId != 0 })
    }
}
