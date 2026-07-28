package com.local.mediaviewer.playback

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaybackPositionDaoTest {
    private lateinit var database: MediaViewerDatabase
    private lateinit var dao: PlaybackPositionDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MediaViewerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.playbackPositionDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun `upsert 覆盖同一媒体键并可删除`() = runTest {
        dao.upsert(PlaybackPositionEntity("key", 10, 100, 1))
        dao.upsert(PlaybackPositionEntity("key", 20, 100, 2))

        assertEquals(20L, dao.find("key")?.positionMs)
        assertEquals(2L, dao.find("key")?.updatedAtEpochMs)

        dao.delete("key")
        assertNull(dao.find("key"))
    }

    @Test
    fun `不同逻辑媒体键独立保存`() = runTest {
        dao.upsert(PlaybackPositionEntity("first", 10, 100, 1))
        dao.upsert(PlaybackPositionEntity("second", 20, 100, 2))

        assertEquals(10L, dao.find("first")?.positionMs)
        assertEquals(20L, dao.find("second")?.positionMs)
    }
}
