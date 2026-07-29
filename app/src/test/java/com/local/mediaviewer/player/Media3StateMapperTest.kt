package com.local.mediaviewer.player

import androidx.media3.common.Player
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.QueueMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3StateMapperTest {
    @Test
    fun `控制器尚未连接时映射为正在打开而不是错误`() {
        val mapped = Media3StateMapper.map(
            connectionState = ControllerConnectionState.Connecting,
            snapshot = null,
        )

        assertEquals(PlaybackStatus.OPENING, mapped.playback.status)
        assertEquals(null, mapped.playback.errorMessage)
    }

    @Test
    fun `缓冲状态保留当前项队列命令循环随机和倍速`() {
        val first = item("first", MediaKind.VIDEO)
        val second = item("second", MediaKind.AUDIO)

        val mapped = Media3StateMapper.map(
            connectionState = ControllerConnectionState.Connected,
            snapshot = Media3StateSnapshot(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                isPlaying = false,
                positionMs = 12_000L,
                durationMs = 60_000L,
                bufferedPositionMs = 30_000L,
                isSeekable = true,
                errorMessage = null,
                items = listOf(first, second),
                currentMediaItemIndex = 1,
                canSkipPrevious = true,
                canSkipNext = false,
                repeatMode = Player.REPEAT_MODE_OFF,
                shuffleModeEnabled = true,
                playbackSpeed = 1.5f,
            ),
        )

        assertEquals(PlaybackStatus.BUFFERING, mapped.playback.status)
        assertEquals(12_000L, mapped.playback.positionMs)
        assertEquals(60_000L, mapped.playback.durationMs)
        assertEquals(50f, mapped.playback.bufferedPercent)
        assertTrue(mapped.playback.isSeekable)
        assertEquals(second, mapped.currentItem)
        assertEquals("second", mapped.queue.currentMediaKey)
        assertEquals(2, mapped.queue.items.size)
        assertEquals(PlaybackMode.SHUFFLE, mapped.queue.mode)
        assertEquals(1.5f, mapped.queue.playbackSpeed)
        assertTrue(mapped.canSkipPrevious)
        assertFalse(mapped.canSkipNext)
    }

    @Test
    fun `结束和播放器错误映射为各自终态`() {
        val ended = Media3StateMapper.map(
            ControllerConnectionState.Connected,
            snapshot(playbackState = Player.STATE_ENDED),
        )
        val failed = Media3StateMapper.map(
            ControllerConnectionState.Connected,
            snapshot(
                playbackState = Player.STATE_IDLE,
                errorMessage = "解码失败",
            ),
        )

        assertEquals(PlaybackStatus.ENDED, ended.playback.status)
        assertEquals(PlaybackStatus.ERROR, failed.playback.status)
        assertEquals("解码失败", failed.playback.errorMessage)
    }

    @Test
    fun `就绪状态按是否实际播放映射并保留单曲循环`() {
        val playing = Media3StateMapper.map(
            ControllerConnectionState.Connected,
            snapshot(
                playbackState = Player.STATE_READY,
                isPlaying = true,
                repeatMode = Player.REPEAT_MODE_ONE,
            ),
        )
        val paused = Media3StateMapper.map(
            ControllerConnectionState.Connected,
            snapshot(
                playbackState = Player.STATE_READY,
                isPlaying = false,
            ),
        )

        assertEquals(PlaybackStatus.PLAYING, playing.playback.status)
        assertEquals(PlaybackMode.REPEAT_ONE, playing.queue.mode)
        assertEquals(PlaybackStatus.PAUSED, paused.playback.status)
    }

    private fun snapshot(
        playbackState: Int,
        isPlaying: Boolean = false,
        errorMessage: String? = null,
        repeatMode: Int = Player.REPEAT_MODE_ALL,
    ) = Media3StateSnapshot(
        playbackState = playbackState,
        playWhenReady = isPlaying,
        isPlaying = isPlaying,
        positionMs = 0L,
        durationMs = 0L,
        bufferedPositionMs = 0L,
        isSeekable = false,
        errorMessage = errorMessage,
        items = listOf(item("only", MediaKind.VIDEO)),
        currentMediaItemIndex = 0,
        canSkipPrevious = false,
        canSkipNext = false,
        repeatMode = repeatMode,
        shuffleModeEnabled = false,
        playbackSpeed = 1f,
    )

    private fun item(
        key: String,
        kind: MediaKind,
    ) = QueueMediaItem(
        mediaKey = key,
        name = key,
        logicalUrl = "https://example.test/$key",
        kind = kind,
    )
}
