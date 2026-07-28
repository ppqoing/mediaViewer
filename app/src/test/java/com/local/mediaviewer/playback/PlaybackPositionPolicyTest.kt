package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionPolicyTest {
    @Test
    fun `不足十秒不恢复而十秒整恢复`() {
        assertNull(
            PlaybackPositionPolicy.resumePosition(entity(9_999, 100_000)),
        )
        assertEquals(
            10_000L,
            PlaybackPositionPolicy.resumePosition(entity(10_000, 100_000)),
        )
    }

    @Test
    fun `完成事件或达到百分之九十五删除`() {
        assertFalse(
            PlaybackPositionPolicy.shouldDelete(94_999, 100_000, false),
        )
        assertTrue(
            PlaybackPositionPolicy.shouldDelete(95_000, 100_000, false),
        )
        assertTrue(
            PlaybackPositionPolicy.shouldDelete(20_000, 100_000, true),
        )
    }

    @Test
    fun `百分之九十五阈值向上取整且不会 Long 溢出`() {
        assertFalse(PlaybackPositionPolicy.shouldDelete(95, 101, false))
        assertTrue(PlaybackPositionPolicy.shouldDelete(96, 101, false))
        val duration = Long.MAX_VALUE
        val threshold = duration - duration / 20
        assertFalse(
            PlaybackPositionPolicy.shouldDelete(
                threshold - 1,
                duration,
                false,
            ),
        )
        assertTrue(
            PlaybackPositionPolicy.shouldDelete(
                threshold,
                duration,
                false,
            ),
        )
    }

    @Test
    fun `未知或无效时长不会按比例删除`() {
        assertFalse(PlaybackPositionPolicy.shouldDelete(99_999, 0, false))
        assertFalse(PlaybackPositionPolicy.shouldDelete(99_999, -1, false))
    }

    private fun entity(position: Long, duration: Long) =
        PlaybackPositionEntity(
            mediaKey = "key",
            positionMs = position,
            durationMs = duration,
            updatedAtEpochMs = 1,
        )
}
