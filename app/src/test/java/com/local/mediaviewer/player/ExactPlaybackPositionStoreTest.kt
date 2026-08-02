package com.local.mediaviewer.player

import com.local.mediaviewer.service.PlaybackPositionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactPlaybackPositionStoreTest {
    @Test
    fun `拒绝非当前媒体快照并保持当前媒体的已有位置`() {
        val store = ExactPlaybackPositionStore()
        store.accept(
            "video-b",
            PlaybackPositionSnapshot("video-b", 12_000L, 60_000L),
        )

        assertFalse(
            store.accept(
                "video-b",
                PlaybackPositionSnapshot("video-a", 9_000L, 60_000L),
            ),
        )

        assertEquals(12_000L, store.positionFor("video-b"))
    }

    @Test
    fun `当前媒体切换后拒绝候选会清除旧媒体位置`() {
        val store = ExactPlaybackPositionStore()
        store.accept(
            "video-a",
            PlaybackPositionSnapshot("video-a", 12_000L, 60_000L),
        )

        assertFalse(
            store.accept(
                "video-b",
                PlaybackPositionSnapshot("video-a", 9_000L, 60_000L),
            ),
        )

        assertEquals(0L, store.positionFor("video-a"))
    }

    @Test
    fun `当前媒体为空后拒绝候选会清除旧媒体位置`() {
        val store = ExactPlaybackPositionStore()
        store.accept(
            "video-a",
            PlaybackPositionSnapshot("video-a", 12_000L, 60_000L),
        )

        assertFalse(
            store.accept(
                null,
                PlaybackPositionSnapshot("video-a", 9_000L, 60_000L),
            ),
        )

        assertEquals(0L, store.positionFor("video-a"))
    }

    @Test
    fun `同一媒体向后 seek 覆盖已缓存的精确位置`() {
        val store = ExactPlaybackPositionStore()
        store.accept(
            "video-a",
            PlaybackPositionSnapshot("video-a", 20_000L, 60_000L),
        )

        assertTrue(
            store.accept(
                "video-a",
                PlaybackPositionSnapshot("video-a", 5_000L, 60_000L),
            ),
        )
        assertEquals(5_000L, store.positionFor("video-a"))
    }

    @Test
    fun `clear 清空当前媒体快照`() {
        val store = ExactPlaybackPositionStore()
        store.accept(
            "video-a",
            PlaybackPositionSnapshot("video-a", 20_000L, 60_000L),
        )

        store.clear()

        assertEquals(0L, store.positionFor("video-a"))
    }
}
