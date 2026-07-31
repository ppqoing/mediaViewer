package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FullscreenControllerTest {
    @Test
    fun `exit shows normal orientation but keeps edge to edge`() {
        val calls = mutableListOf<Pair<Boolean, Boolean>>()
        val controller = FullscreenController(
            FullscreenWindowPolicy { fullscreen, decorFits ->
                calls += fullscreen to decorFits
            },
        )

        controller.enter()
        controller.exit()

        assertEquals(
            listOf(true to false, false to false),
            calls,
        )
        assertFalse(controller.isFullscreen.value)
    }
}
