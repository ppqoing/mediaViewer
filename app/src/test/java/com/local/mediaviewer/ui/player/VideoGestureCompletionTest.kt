package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGestureCompletionTest {
    @Test
    fun `seek only commits for a normal pointer up`() {
        assertEquals(SeekGestureCompletion.COMMIT, seekGestureCompletion(endedWithUp = true))
        assertEquals(SeekGestureCompletion.RESTORE_PREVIEW, seekGestureCompletion(endedWithUp = false))
    }
}
