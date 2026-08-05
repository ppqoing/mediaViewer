package com.local.mediaviewer.navigation

import com.local.mediaviewer.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRouteLifecyclePolicyTest {
    @Test
    fun `confirmed video identity survives missing current item during reconnect`() {
        val confirmedVideo = PlayerRouteLifecyclePolicy.observeCurrentItem(
            state = PlayerRouteLifecycleState(),
            currentKind = MediaKind.VIDEO,
        )

        val reconnecting = PlayerRouteLifecyclePolicy.observeCurrentItem(
            state = confirmedVideo,
            currentKind = null,
        )

        assertEquals(MediaKind.VIDEO, reconnecting.lastPresentedKind)
    }

    @Test
    fun `confirmed video stops and clears only when background playback is disabled`() {
        val confirmedVideo = PlayerRouteLifecyclePolicy.observeCurrentItem(
            state = PlayerRouteLifecycleState(),
            currentKind = MediaKind.VIDEO,
        )

        assertEquals(
            PlayerRouteExitAction.STOP_AND_CLEAR,
            PlayerRouteLifecyclePolicy.exitAction(
                state = confirmedVideo,
                backgroundPlaybackEnabled = false,
            ),
        )
        assertEquals(
            PlayerRouteExitAction.LEAVE_ONLY,
            PlayerRouteLifecyclePolicy.exitAction(
                state = confirmedVideo,
                backgroundPlaybackEnabled = true,
            ),
        )
    }

    @Test
    fun `confirmed audio and unconfirmed route exit without video stop and clear`() {
        val confirmedAudio = PlayerRouteLifecyclePolicy.observeCurrentItem(
            state = PlayerRouteLifecycleState(),
            currentKind = MediaKind.AUDIO,
        )

        assertEquals(
            PlayerRouteExitAction.LEAVE_ONLY,
            PlayerRouteLifecyclePolicy.exitAction(
                state = confirmedAudio,
                backgroundPlaybackEnabled = false,
            ),
        )
        assertEquals(
            PlayerRouteExitAction.LEAVE_ONLY,
            PlayerRouteLifecyclePolicy.exitAction(
                state = PlayerRouteLifecycleState(),
                backgroundPlaybackEnabled = false,
            ),
        )
    }

    @Test
    fun `ready audio replaces previously confirmed video identity`() {
        val confirmedVideo = PlayerRouteLifecyclePolicy.observeCurrentItem(
            state = PlayerRouteLifecycleState(),
            currentKind = MediaKind.VIDEO,
        )

        val confirmedAudio = PlayerRouteLifecyclePolicy.observeCurrentItem(
            state = confirmedVideo,
            currentKind = MediaKind.AUDIO,
        )

        assertEquals(MediaKind.AUDIO, confirmedAudio.lastPresentedKind)
        assertEquals(
            PlayerRouteExitAction.LEAVE_ONLY,
            PlayerRouteLifecyclePolicy.exitAction(
                state = confirmedAudio,
                backgroundPlaybackEnabled = true,
            ),
        )
    }
}
