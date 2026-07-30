package com.local.mediaviewer.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerIconsTest {
    @Test
    fun `player icon inventory has consistent viewport`() {
        val icons = PlayerIcons.all
        assertEquals(23, icons.size)
        assertTrue(icons.all { it.defaultWidth == 24.dp })
        assertTrue(icons.all { it.defaultHeight == 24.dp })
        assertTrue(icons.all { it.viewportWidth == 24f })
        assertTrue(icons.all { it.viewportHeight == 24f })
        assertEquals(icons.size, icons.map { it.name }.distinct().size)
    }
}
