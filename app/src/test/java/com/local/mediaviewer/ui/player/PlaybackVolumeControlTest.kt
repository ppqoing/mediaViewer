package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackVolumeControlTest {
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
}
