package com.local.mediaviewer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBackgroundPlaybackPolicyTest {
    @Test
    fun `disabled background playback pauses playing video and remembers it`() {
        val stopped = VideoBackgroundPlaybackPolicy.onAppStopped(
            state = VideoBackgroundLifecycleState(),
            backgroundPlaybackEnabled = false,
            reason = VideoSessionExitReason.APP_BACKGROUND,
            currentMediaKey = "video-1",
            playWhenReady = true,
        )
        assertEquals(VideoBackgroundLifecycleAction.PAUSE, stopped.action)
        assertFalse(stopped.state.isForeground)
        assertEquals("video-1", stopped.state.pendingResumeMediaKey)
    }

    @Test
    fun `disabled background playback keeps manually paused video paused`() {
        val stopped = VideoBackgroundPlaybackPolicy.onAppStopped(
            state = VideoBackgroundLifecycleState(),
            backgroundPlaybackEnabled = false,
            reason = VideoSessionExitReason.APP_BACKGROUND,
            currentMediaKey = "video-1",
            playWhenReady = false,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, stopped.action)
        assertNull(stopped.state.pendingResumeMediaKey)
    }

    @Test
    fun `enabled background playback neither pauses nor schedules resume`() {
        val stopped = VideoBackgroundPlaybackPolicy.onAppStopped(
            state = VideoBackgroundLifecycleState(),
            backgroundPlaybackEnabled = true,
            reason = VideoSessionExitReason.APP_BACKGROUND,
            currentMediaKey = "video-1",
            playWhenReady = true,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, stopped.action)
        assertNull(stopped.state.pendingResumeMediaKey)
    }

    @Test
    fun `configuration change does not pause or schedule resume`() {
        val stopped = VideoBackgroundPlaybackPolicy.onAppStopped(
            state = VideoBackgroundLifecycleState(),
            backgroundPlaybackEnabled = false,
            reason = VideoSessionExitReason.CONFIGURATION_CHANGE,
            currentMediaKey = "video-1",
            playWhenReady = true,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, stopped.action)
        assertNull(stopped.state.pendingResumeMediaKey)
    }

    @Test
    fun `foreground waits for session item then resumes matching video once`() {
        val stopped = VideoBackgroundPlaybackPolicy.onAppStopped(
            state = VideoBackgroundLifecycleState(),
            backgroundPlaybackEnabled = false,
            reason = VideoSessionExitReason.APP_BACKGROUND,
            currentMediaKey = "video-1",
            playWhenReady = true,
        )
        val started = VideoBackgroundPlaybackPolicy.onAppStarted(stopped.state)
        val waiting = VideoBackgroundPlaybackPolicy.reconcileForeground(
            state = started,
            currentMediaKey = null,
            hasActiveVideo = true,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, waiting.action)
        assertEquals("video-1", waiting.state.pendingResumeMediaKey)

        val resumed = VideoBackgroundPlaybackPolicy.reconcileForeground(
            state = waiting.state,
            currentMediaKey = "video-1",
            hasActiveVideo = true,
        )
        assertEquals(VideoBackgroundLifecycleAction.PLAY, resumed.action)
        assertNull(resumed.state.pendingResumeMediaKey)

        val reconciledAgain = VideoBackgroundPlaybackPolicy.reconcileForeground(
            state = resumed.state,
            currentMediaKey = "video-1",
            hasActiveVideo = true,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, reconciledAgain.action)
    }

    @Test
    fun `repeated stop does not lose or duplicate pending resume`() {
        val firstStop = VideoBackgroundPlaybackPolicy.onAppStopped(
            state = VideoBackgroundLifecycleState(),
            backgroundPlaybackEnabled = false,
            reason = VideoSessionExitReason.APP_BACKGROUND,
            currentMediaKey = "video-1",
            playWhenReady = true,
        )
        val repeatedStop = VideoBackgroundPlaybackPolicy.onAppStopped(
            state = firstStop.state,
            backgroundPlaybackEnabled = false,
            reason = VideoSessionExitReason.APP_BACKGROUND,
            currentMediaKey = "video-1",
            playWhenReady = false,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, repeatedStop.action)
        assertEquals("video-1", repeatedStop.state.pendingResumeMediaKey)
    }

    @Test
    fun `different item or closed player cancels pending resume`() {
        val pending = VideoBackgroundLifecycleState(
            isForeground = true,
            pendingResumeMediaKey = "video-1",
        )
        val differentItem = VideoBackgroundPlaybackPolicy.reconcileForeground(
            state = pending,
            currentMediaKey = "video-2",
            hasActiveVideo = true,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, differentItem.action)
        assertNull(differentItem.state.pendingResumeMediaKey)

        val closedPlayer = VideoBackgroundPlaybackPolicy.reconcileForeground(
            state = pending,
            currentMediaKey = "video-1",
            hasActiveVideo = false,
        )
        assertEquals(VideoBackgroundLifecycleAction.NONE, closedPlayer.action)
        assertNull(closedPlayer.state.pendingResumeMediaKey)
    }

    @Test
    fun `leaving video always stops and clears regardless of background setting`() {
        assertTrue(
            VideoBackgroundPlaybackPolicy.shouldStopAndClear(
                VideoSessionExitReason.NAVIGATE_AWAY,
            ),
        )
        assertFalse(
            VideoBackgroundPlaybackPolicy.shouldStopAndClear(
                VideoSessionExitReason.APP_BACKGROUND,
            ),
        )
        assertFalse(
            VideoBackgroundPlaybackPolicy.shouldStopAndClear(
                VideoSessionExitReason.CONFIGURATION_CHANGE,
            ),
        )
    }
}
