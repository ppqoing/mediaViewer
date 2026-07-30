package com.local.mediaviewer.ui.player

import com.local.mediaviewer.player.VideoGestureAxis
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGestureCompletionTest {
    @Test
    fun verticalGestureCompletionClearsFeedbackInsteadOfHandlingTap() {
        assertEquals(
            GestureCompletionAction.CLEAR_FEEDBACK,
            gestureCompletionAction(VideoGestureAxis.BRIGHTNESS, endedWithUp = true),
        )
        assertEquals(
            GestureCompletionAction.CLEAR_FEEDBACK,
            gestureCompletionAction(VideoGestureAxis.VOLUME, endedWithUp = true),
        )
    }

    @Test
    fun undecidedGestureCompletionHandlesTapOnlyWhenItEndsWithUp() {
        assertEquals(
            GestureCompletionAction.HANDLE_TAP,
            gestureCompletionAction(VideoGestureAxis.UNDECIDED, endedWithUp = true),
        )
        assertEquals(
            GestureCompletionAction.NONE,
            gestureCompletionAction(VideoGestureAxis.UNDECIDED, endedWithUp = false),
        )
    }
}
