package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFormattersTest {
    @Test
    fun `时长覆盖小时与负值`() {
        assertEquals("00:00", formatPlaybackTime(-1))
        assertEquals("01:05", formatPlaybackTime(65_000))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000))
    }
}
