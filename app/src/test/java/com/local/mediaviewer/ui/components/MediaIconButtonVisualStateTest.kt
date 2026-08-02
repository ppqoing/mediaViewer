package com.local.mediaviewer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIconButtonVisualStateTest {
    @Test
    fun `disabled generated icon uses disabled alpha without changing loading semantics`() {
        val enabled = mediaIconButtonVisualState(
            enabled = true,
            loading = false,
        )
        val disabled = mediaIconButtonVisualState(
            enabled = false,
            loading = false,
        )
        val loading = mediaIconButtonVisualState(
            enabled = true,
            loading = true,
        )

        assertEquals(1f, enabled.iconAlpha, 0.001f)
        assertEquals(0.38f, disabled.iconAlpha, 0.001f)
        assertNotEquals(enabled.iconAlpha, disabled.iconAlpha)
        assertTrue(enabled.isEnabled)
        assertFalse(disabled.isEnabled)
        assertFalse(loading.isEnabled)
    }
}
