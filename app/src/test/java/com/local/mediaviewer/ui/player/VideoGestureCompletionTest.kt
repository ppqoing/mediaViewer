package com.local.mediaviewer.ui.player

import androidx.compose.ui.input.pointer.PointerEventType
import com.local.mediaviewer.player.VideoGestureAxis
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGestureCompletionTest {
    @Test
    fun `seek only commits for a release event with a pointer up`() {
        assertEquals(
            SeekGestureCompletion.COMMIT,
            seekGestureCompletion(PointerEventType.Release, endedWithUp = true),
        )
        assertEquals(
            SeekGestureCompletion.RESTORE_PREVIEW,
            seekGestureCompletion(PointerEventType.Unknown, endedWithUp = true),
        )
        assertEquals(
            SeekGestureCompletion.RESTORE_PREVIEW,
            seekGestureCompletion(PointerEventType.Exit, endedWithUp = true),
        )
    }

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
