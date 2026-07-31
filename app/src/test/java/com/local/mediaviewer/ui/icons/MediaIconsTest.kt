package com.local.mediaviewer.ui.icons

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIconsTest {
    @Test
    fun `shared icon inventory is stable and uses 24dp viewport`() {
        assertEquals(19, MediaIcons.all.size)
        assertTrue(MediaIcons.all.all { it.defaultWidth == 24.dp })
        assertTrue(MediaIcons.all.all { it.defaultHeight == 24.dp })
        assertEquals(
            MediaIcons.all.size,
            MediaIcons.all.map { it.name }.distinct().size,
        )
    }
}
