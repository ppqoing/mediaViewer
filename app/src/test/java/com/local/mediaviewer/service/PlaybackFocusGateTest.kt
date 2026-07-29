package com.local.mediaviewer.service

import com.local.mediaviewer.playback.PlaybackInterruption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFocusGateTest {
    @Test
    fun `failed focus acquisition blocks play and publishes Chinese error`() {
        val errors = mutableListOf<String>()
        val gate = PlaybackFocusGate(
            acquireFocus = { false },
            pause = {},
            resume = {},
            publishError = errors::add,
        )

        assertFalse(gate.onUserPlayRequest())
        assertEquals(listOf("无法获取音频焦点，暂时不能播放"), errors)
    }

    @Test
    fun `transient loss resumes on gain only while user still wants playback`() {
        var pauses = 0
        var resumes = 0
        val gate = PlaybackFocusGate(
            acquireFocus = { true },
            pause = { pauses += 1 },
            resume = { resumes += 1 },
            publishError = {},
        )

        assertTrue(gate.onUserPlayRequest())
        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        gate.onInterruption(PlaybackInterruption.FocusGained, wasPlaying = false)

        assertEquals(1, pauses)
        assertEquals(1, resumes)

        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        gate.onUserPause()
        gate.onInterruption(PlaybackInterruption.FocusGained, wasPlaying = false)

        assertEquals(2, pauses)
        assertEquals(1, resumes)
    }

    @Test
    fun `permanent loss and noisy output clear pending resume and pause`() {
        var pauses = 0
        var resumes = 0
        val gate = PlaybackFocusGate(
            acquireFocus = { true },
            pause = { pauses += 1 },
            resume = { resumes += 1 },
            publishError = {},
        )

        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        gate.onInterruption(PlaybackInterruption.PermanentLoss, wasPlaying = false)
        gate.onInterruption(PlaybackInterruption.FocusGained, wasPlaying = false)
        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        gate.onInterruption(PlaybackInterruption.BecomingNoisy, wasPlaying = false)
        gate.onInterruption(PlaybackInterruption.FocusGained, wasPlaying = false)

        assertEquals(4, pauses)
        assertEquals(0, resumes)
    }
}
