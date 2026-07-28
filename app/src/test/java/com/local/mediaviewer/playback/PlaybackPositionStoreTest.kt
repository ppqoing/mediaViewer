package com.local.mediaviewer.playback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPositionStoreTest {
    @Test
    fun `记录中途位置并恢复`() = runTest {
        val dao = FakePositionDao()
        val store = RoomPlaybackPositionStore(dao)

        store.record("logical-key", 30_000, 100_000, 123)

        assertEquals(30_000L, store.resumePosition("logical-key"))
        assertEquals(123L, dao.values["logical-key"]?.updatedAtEpochMs)
    }

    @Test
    fun `不足十秒仍记录但不恢复`() = runTest {
        val dao = FakePositionDao()
        val store = RoomPlaybackPositionStore(dao)

        store.record("logical-key", 9_999, 100_000, 123)

        assertEquals(9_999L, dao.values["logical-key"]?.positionMs)
        assertNull(store.resumePosition("logical-key"))
    }

    @Test
    fun `达到完成阈值或结束时删除`() = runTest {
        val dao = FakePositionDao()
        val store = RoomPlaybackPositionStore(dao)
        store.record("logical-key", 50_000, 100_000, 1)
        store.record("logical-key", 95_000, 100_000, 2)
        assertNull(dao.values["logical-key"])

        store.record("logical-key", 20_000, 100_000, 3)
        store.record(
            "logical-key",
            20_000,
            100_000,
            4,
            ended = true,
        )
        assertNull(dao.values["logical-key"])
    }

    @Test
    fun `负位置和时长写入前归零`() = runTest {
        val dao = FakePositionDao()
        val store = RoomPlaybackPositionStore(dao)

        store.record("logical-key", -100, -1, 7)

        assertEquals(
            PlaybackPositionEntity("logical-key", 0, 0, 7),
            dao.values["logical-key"],
        )
    }

    @Test
    fun `显式清除只删除指定媒体键`() = runTest {
        val dao = FakePositionDao()
        val store = RoomPlaybackPositionStore(dao)
        store.record("first", 20_000, 100_000, 1)
        store.record("second", 30_000, 100_000, 2)

        store.clear("first")

        assertNull(dao.values["first"])
        assertEquals(30_000L, store.resumePosition("second"))
    }
}

private class FakePositionDao : PlaybackPositionDao {
    val values = mutableMapOf<String, PlaybackPositionEntity>()

    override suspend fun find(mediaKey: String): PlaybackPositionEntity? =
        values[mediaKey]

    override suspend fun upsert(entity: PlaybackPositionEntity) {
        values[entity.mediaKey] = entity
    }

    override suspend fun delete(mediaKey: String) {
        values.remove(mediaKey)
    }
}
