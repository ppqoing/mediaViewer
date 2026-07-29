package com.local.mediaviewer.playback

import android.content.Context
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
class MediaViewerDatabaseFactoryTest {
    private val databaseName = "media-viewer-factory-migration-test"
    private lateinit var context: Context

    @Before
    fun deleteExistingDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `app database builder migrates a v1 database`() = runTest {
        createV1Database()

        val database = MediaViewerDatabaseFactory.create(context, databaseName)

        try {
            assertEquals(1200L, database.playbackPositionDao().find("video-1")?.positionMs)
            val state = database.playbackQueueDao().state()
            assertEquals("SEQUENTIAL", state?.playbackMode)
            assertEquals(1f, state?.playbackSpeed)
            assertNull(state?.currentMediaKey)
        } finally {
            database.close()
        }
    }

    private fun createV1Database() {
        val database = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            database.execSQL(
                "CREATE TABLE playback_positions (" +
                    "media_key TEXT NOT NULL, " +
                    "position_ms INTEGER NOT NULL, " +
                    "duration_ms INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL, " +
                    "PRIMARY KEY(media_key))",
            )
            database.execSQL(
                "INSERT INTO playback_positions (media_key, position_ms, duration_ms, updated_at) " +
                    "VALUES ('video-1', 1200, 10000, 42)",
            )
            database.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            database.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, 'd656171bac1683a0af267c0d48f3a23e')",
            )
            database.execSQL("PRAGMA user_version = 1")
        } finally {
            database.close()
        }
    }
}
