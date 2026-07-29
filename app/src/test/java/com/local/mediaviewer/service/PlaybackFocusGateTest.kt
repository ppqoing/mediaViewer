package com.local.mediaviewer.service

import com.local.mediaviewer.playback.PlaybackInterruption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackFocusGateTest {
    @Test
    fun `failed focus acquisition blocks play and publishes Chinese error`() {
        val errors = mutableListOf<String>()
        val gate = PlaybackFocusGate(
            acquireFocus = { false },
            pauseForInterruption = {},
            pausePermanently = {},
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
            pauseForInterruption = { pauses += 1 },
            pausePermanently = { pauses += 1 },
            resume = { resumes += 1 },
            publishError = {},
        )

        assertTrue(gate.onUserPlayRequest())
        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        gate.onInterruption(PlaybackInterruption.FocusGained, wasPlaying = true)

        assertEquals(1, pauses)
        assertEquals(1, resumes)

        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        gate.onUserPause()
        gate.onInterruption(PlaybackInterruption.FocusGained, wasPlaying = false)

        assertEquals(2, pauses)
        assertEquals(1, resumes)

        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        gate.onInterruption(PlaybackInterruption.FocusGained, wasPlaying = false)
        assertEquals(1, resumes)
    }

    @Test
    fun `permanent loss and noisy output clear pending resume and pause`() {
        var pauses = 0
        var resumes = 0
        val gate = PlaybackFocusGate(
            acquireFocus = { true },
            pauseForInterruption = { pauses += 1 },
            pausePermanently = { pauses += 1 },
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

    @Test
    fun `interruption pause keeps user play intent until explicit user pause`() = runTest {
        val coordinator = serviceTestCoordinator(this)
        coordinator.replaceQueue(listOf(serviceTestItem("a")), "a")
        advanceUntilIdle()

        coordinator.pauseForInterruption()
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value.playWhenReady)

        coordinator.setPlayWhenReadyFromSession(false)
        advanceUntilIdle()
        assertFalse(coordinator.sessionState.value.playWhenReady)
        coordinator.close()
    }

    @Test
    fun `permanent and noisy losses clear session intent and qualify task removal`() = runTest {
        val coordinator = serviceTestCoordinator(this)
        val gate = PlaybackFocusGate(
            acquireFocus = { true },
            pauseForInterruption = coordinator::pauseForInterruption,
            pausePermanently = coordinator::pause,
            resume = coordinator::play,
            publishError = {},
        )

        coordinator.replaceQueue(listOf(serviceTestItem("a")), "a")
        advanceUntilIdle()
        gate.onInterruption(PlaybackInterruption.TransientLoss, wasPlaying = true)
        advanceUntilIdle()
        assertTrue(coordinator.sessionState.value.playWhenReady)

        gate.onInterruption(PlaybackInterruption.PermanentLoss, wasPlaying = true)
        advanceUntilIdle()
        assertFalse(coordinator.sessionState.value.playWhenReady)
        assertTrue(
            shouldStopAfterTaskRemoved(
                releaseStarted = false,
                playWhenReady = coordinator.sessionState.value.playWhenReady,
                hasConnectedControllers = false,
            ),
        )

        coordinator.replaceQueue(listOf(serviceTestItem("a")), "a")
        advanceUntilIdle()
        gate.onInterruption(PlaybackInterruption.BecomingNoisy, wasPlaying = true)
        advanceUntilIdle()
        assertFalse(coordinator.sessionState.value.playWhenReady)
        coordinator.close()
    }
}
