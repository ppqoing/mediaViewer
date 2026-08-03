package com.local.mediaviewer.image

import com.local.mediaviewer.settings.VideoControlsAutoHide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControlsStateTest {
    @Test
    fun interactionPausesAutoHideAndReleaseRestartsThreeSecondDeadline() {
        val interacting = ReaderControlsReducer.beginInteraction(
            ReaderControlsState(),
        )

        assertTrue(interacting.interactionActive)
        assertNull(
            ReaderControlsReducer.autoHideDelayMs(
                interacting,
                VideoControlsAutoHide.THREE_SECONDS,
            ),
        )

        val released = ReaderControlsReducer.endInteraction(interacting)

        assertFalse(released.interactionActive)
        assertEquals(1L, released.autoHideEpoch)
        assertEquals(
            3_000L,
            ReaderControlsReducer.autoHideDelayMs(
                released,
                VideoControlsAutoHide.THREE_SECONDS,
            ),
        )
    }

    @Test
    fun hiddenControlsAndNeverPreferenceHaveNoDeadline() {
        assertNull(
            ReaderControlsReducer.autoHideDelayMs(
                ReaderControlsState(visible = false),
                VideoControlsAutoHide.THREE_SECONDS,
            ),
        )
        assertNull(
            ReaderControlsReducer.autoHideDelayMs(
                ReaderControlsState(),
                VideoControlsAutoHide.NEVER,
            ),
        )
    }

    @Test
    fun toggleChangesVisibilityAndRevealRestartsDeadline() {
        val hidden = ReaderControlsReducer.toggle(
            ReaderControlsState(),
        )
        assertFalse(hidden.visible)

        val shown = ReaderControlsReducer.toggle(hidden)
        assertTrue(shown.visible)

        val revealed = ReaderControlsReducer.reveal(shown)
        assertTrue(revealed.visible)
        assertEquals(
            shown.autoHideEpoch + 1L,
            revealed.autoHideEpoch,
        )
    }
}
