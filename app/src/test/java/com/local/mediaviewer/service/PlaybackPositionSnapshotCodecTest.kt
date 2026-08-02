package com.local.mediaviewer.service

import android.os.Bundle
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackPositionSnapshotCodecTest {
    @Test
    fun `会话位置转换会裁剪负数和超过时长的位置`() {
        val session = sessionState(
            mediaKey = "video-a",
            positionMs = 75_000L,
            durationMs = 60_000L,
        )

        assertEquals(
            PlaybackPositionSnapshot("video-a", 60_000L, 60_000L),
            session.toPlaybackPositionSnapshot(),
        )
    }

    @Test
    fun `快照 Bundle 往返保留媒体位置和时长`() {
        val expected = PlaybackPositionSnapshot("audio-b", 8_500L, 90_000L)

        assertEquals(
            expected,
            PlaybackPositionSnapshotCodec.decode(
                PlaybackPositionSnapshotCodec.encode(expected),
            ),
        )
    }

    @Test
    fun `损坏 Bundle 和空媒体不会产生快照`() {
        assertNull(PlaybackSessionState().toPlaybackPositionSnapshot())
        assertNull(PlaybackPositionSnapshotCodec.decode(Bundle.EMPTY))
        assertNull(
            PlaybackPositionSnapshotCodec.decode(
                Bundle().apply {
                    putString("media_key", "video-a")
                    putLong("position_ms", -1L)
                    putLong("duration_ms", 60_000L)
                },
            ),
        )
    }

    private fun sessionState(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
    ): PlaybackSessionState {
        val item = QueueMediaItem(
            mediaKey = mediaKey,
            name = mediaKey,
            logicalUrl = "https://example.test/$mediaKey",
            kind = MediaKind.VIDEO,
        )
        return PlaybackSessionState(
            playback = PlaybackState(
                status = PlaybackStatus.PLAYING,
                positionMs = positionMs,
                durationMs = durationMs,
                isSeekable = true,
            ),
            playWhenReady = true,
            queue = PlaybackQueue(
                items = listOf(item),
                currentMediaKey = mediaKey,
            ),
            currentItem = item,
        )
    }
}
