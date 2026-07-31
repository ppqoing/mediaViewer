package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVolumeControlTest {
    @Test
    fun fractionIsClampedAndSafeWhenMaximumIsZero() {
        assertEquals(0f, VolumeState(0, 0, true).fraction)
        assertEquals(1f, VolumeState(12, 10, false).fraction)
    }

    @Test
    fun accessibilityDescriptionIncludesPercentageAndMuteState() {
        assertEquals(
            "音量，当前 50%，未静音",
            VolumeState(current = 5, maximum = 10, muted = false).accessibilityDescription(),
        )
        assertEquals(
            "音量，当前 0%，已静音",
            VolumeState(current = 0, maximum = 10, muted = true).accessibilityDescription(),
        )
    }

    @Test
    fun `volume popup idle policy expires and resets at the approved deadline`() {
        val initial = VolumePopupIdlePolicy(lastInteractionMs = 1_000L)
        assertFalse(initial.shouldClose(nowMs = 3_999L))
        assertTrue(initial.shouldClose(nowMs = 4_000L))

        val reset = initial.interacted(nowMs = 3_500L)
        assertFalse(reset.shouldClose(nowMs = 6_499L))
        assertTrue(reset.shouldClose(nowMs = 6_500L))
    }

}
