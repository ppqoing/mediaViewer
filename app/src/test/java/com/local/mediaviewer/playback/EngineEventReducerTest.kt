package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineEventReducerTest {
    @Test
    fun `时间长度缓冲和 seekable 增量更新`() {
        val initial = PlaybackState(status = PlaybackStatus.OPENING)
        val updated = listOf(
            EngineEvent.DurationChanged(120_000),
            EngineEvent.TimeChanged(30_000),
            EngineEvent.Buffering(42.5f),
            EngineEvent.SeekableChanged(true),
            EngineEvent.Playing,
        ).fold(initial, EngineEventReducer::reduce)

        assertEquals(120_000L, updated.durationMs)
        assertEquals(30_000L, updated.positionMs)
        assertEquals(42.5f, updated.bufferedPercent)
        assertTrue(updated.isSeekable)
        assertEquals(PlaybackStatus.PLAYING, updated.status)
    }

    @Test
    fun `数值事件夹取到合法范围`() {
        val updated = listOf(
            EngineEvent.DurationChanged(-1),
            EngineEvent.TimeChanged(-2),
            EngineEvent.Buffering(120f),
            EngineEvent.SeekableChanged(false),
        ).fold(
            PlaybackState(
                durationMs = 10,
                positionMs = 5,
                isSeekable = true,
            ),
            EngineEventReducer::reduce,
        )

        assertEquals(0L, updated.durationMs)
        assertEquals(0L, updated.positionMs)
        assertEquals(100f, updated.bufferedPercent)
        assertFalse(updated.isSeekable)
    }

    @Test
    fun `结束和错误进入终态而 Opening 清除旧错误`() {
        assertEquals(
            PlaybackStatus.ENDED,
            EngineEventReducer.reduce(
                PlaybackState(),
                EngineEvent.EndReached,
            ).status,
        )
        val error = EngineEventReducer.reduce(
            PlaybackState(),
            EngineEvent.Error("无法解码"),
        )
        assertEquals(PlaybackStatus.ERROR, error.status)
        assertEquals("无法解码", error.errorMessage)

        val reopened = EngineEventReducer.reduce(error, EngineEvent.Opening)
        assertEquals(PlaybackStatus.OPENING, reopened.status)
        assertNull(reopened.errorMessage)
    }

    @Test
    fun `暂停事件进入暂停状态`() {
        assertEquals(
            PlaybackStatus.PAUSED,
            EngineEventReducer.reduce(
                PlaybackState(status = PlaybackStatus.PLAYING),
                EngineEvent.Paused,
            ).status,
        )
    }

    @Test
    fun `增量事件保留当前播放倍速`() {
        val initial = PlaybackState(
            status = PlaybackStatus.PLAYING,
            playbackSpeed = 1.5f,
        )

        val updated = listOf(
            EngineEvent.TimeChanged(5_000L),
            EngineEvent.Buffering(40f),
            EngineEvent.Paused,
            EngineEvent.Error("网络中断"),
        ).fold(initial, EngineEventReducer::reduce)

        assertEquals(1.5f, updated.playbackSpeed)
    }
}
