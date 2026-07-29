package com.local.mediaviewer.ui.player

import androidx.compose.ui.input.pointer.PointerEventType
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
}
