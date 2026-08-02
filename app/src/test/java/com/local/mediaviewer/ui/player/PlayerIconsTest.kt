package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerIconsTest {
    @Test
    fun `player icon inventory uses generated drawable resources`() {
        assertEquals(25, PlayerIcons.all.size)
        assertEquals(PlayerIcons.all.size, PlayerIcons.all.map { it.resourceId }.distinct().size)
    }

    @Test
    fun `disabled player icon has a non-color marker`() {
        val visualState = neonPlayerIconVisualState(
            active = false,
            enabled = false,
        )

        assertTrue(visualState.showDisabledMark)
    }

    @Test
    fun `disabled active player icon suppresses active accent`() {
        val visualState = neonPlayerIconVisualState(
            active = true,
            enabled = false,
        )

        assertFalse(visualState.showActiveAccent)
    }
}
