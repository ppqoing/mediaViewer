package com.local.mediaviewer.queue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.local.mediaviewer.playback.MediaViewerDatabase
import com.local.mediaviewer.model.MediaKind

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaybackQueueDaoTest {
    private lateinit var database: MediaViewerDatabase
    private lateinit var dao: PlaybackQueueDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MediaViewerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.playbackQueueDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun `replacing a snapshot removes stale items and exposes matching state`() = runTest {
        dao.replaceSnapshot(
            items = listOf(item("a", 0), item("b", 1), item("c", 2)),
            state = state(currentMediaKey = "b"),
        )

        dao.replaceSnapshot(
            items = listOf(item("c", 0), item("a", 1)),
            state = state(currentMediaKey = "c", updatedAtEpochMs = 2),
        )

        assertEquals(listOf("c", "a"), dao.items().map { it.mediaKey })
        assertEquals(listOf(0, 1), dao.items().map { it.sortOrder })
        assertNull(dao.items().firstOrNull { it.mediaKey == "b" })
        assertEquals("c", dao.state()?.currentMediaKey)
        assertEquals(2L, dao.state()?.updatedAtEpochMs)
    }

    @Test
    fun `repository preserves raw urls playback speed and shuffle cursor`() = runTest {
        val repository = RoomPlaybackQueueRepository(dao, clock = { 9 })
        val saved = PlaybackQueue(
            items = listOf(
                QueueMediaItem("a", "Alpha", "https://example.test/a%2Fb?raw=1", MediaKind.VIDEO),
                QueueMediaItem("b", "Beta", "https://example.test/b", MediaKind.AUDIO),
            ),
            currentMediaKey = "b",
            mode = PlaybackMode.SHUFFLE,
            shuffleOrder = listOf("b", "a"),
            shuffleCursor = 0,
            playbackSpeed = 1.25f,
        )

        repository.save(saved)

        assertEquals(saved, repository.restore())
        assertEquals(1.25f, dao.state()?.playbackSpeed)
        assertEquals(0, dao.state()?.shuffleCursor)
        assertEquals("https://example.test/a%2Fb?raw=1", dao.items().first().logicalUrl)
    }

    @Test
    fun `repository repairs an invalid playback mode as sequential`() = runTest {
        dao.replaceSnapshot(
            items = listOf(item("a", 0)),
            state = state(currentMediaKey = "a").copy(
                playbackMode = "NOT_A_MODE",
                shuffleOrderJson = "[\"a\"]",
                shuffleCursor = 0,
            ),
        )
        val repository = RoomPlaybackQueueRepository(dao, clock = { 9 })

        val restored = repository.restore()

        assertEquals(PlaybackMode.SEQUENTIAL, restored.mode)
        assertEquals(emptyList<String>(), restored.shuffleOrder)
        assertEquals(-1, restored.shuffleCursor)
        assertEquals("SEQUENTIAL", dao.state()?.playbackMode)
        assertEquals("[]", dao.state()?.shuffleOrderJson)
    }

    @Test
    fun `repository repairs malformed shuffle json as sequential`() = runTest {
        dao.replaceSnapshot(
            items = listOf(item("a", 0)),
            state = state(currentMediaKey = "a").copy(
                playbackMode = "SHUFFLE",
                shuffleOrderJson = "{not-json}",
                shuffleCursor = 0,
            ),
        )
        val repository = RoomPlaybackQueueRepository(dao, clock = { 9 })

        val restored = repository.restore()

        assertEquals(PlaybackMode.SEQUENTIAL, restored.mode)
        assertEquals(emptyList<String>(), restored.shuffleOrder)
        assertEquals(-1, restored.shuffleCursor)
        assertEquals("SEQUENTIAL", dao.state()?.playbackMode)
        assertEquals("[]", dao.state()?.shuffleOrderJson)
    }

    private fun item(mediaKey: String, sortOrder: Int) = PlaybackQueueItemEntity(
        mediaKey = mediaKey,
        name = mediaKey,
        logicalUrl = "https://example.test/$mediaKey",
        mediaKind = "VIDEO",
        sortOrder = sortOrder,
    )

    private fun state(
        currentMediaKey: String,
        updatedAtEpochMs: Long = 1,
    ) = PlaybackQueueStateEntity(
        currentMediaKey = currentMediaKey,
        playbackMode = "SEQUENTIAL",
        shuffleOrderJson = "[]",
        shuffleCursor = -1,
        playbackSpeed = 1f,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
