package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeHintTimingTest {
    @Test
    fun `恢复提示有明确可见周期后才清除`() {
        assertTrue(
            (resumeHintDismissDelayMs(30_000L) ?: 0L) > 0L,
        )
    }

    @Test
    fun `没有恢复提示时不安排清除`() {
        assertEquals(null, resumeHintDismissDelayMs(null))
    }
}
