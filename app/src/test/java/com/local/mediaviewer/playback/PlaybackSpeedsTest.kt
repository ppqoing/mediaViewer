package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackSpeedsTest {
    @Test
    fun `只接受产品定义的六档倍速`() {
        assertEquals(1.5f, PlaybackSpeeds.requireSupported(1.5f))

        assertThrows(IllegalArgumentException::class.java) {
            PlaybackSpeeds.requireSupported(1.1f)
        }
    }
}
