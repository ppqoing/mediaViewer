package com.local.mediaviewer

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.mediaviewer.playback.MediaViewerDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaViewerDatabaseMigrationTest {
    private val databaseName = "media-viewer-migration-test"
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaViewerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun migrationFrom1To2_preservesPositionsAndCreatesDefaultQueueState() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO playback_positions (media_key, position_ms, duration_ms, updated_at) " +
                    "VALUES ('video-1', 1200, 10000, 42)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            MediaViewerDatabase.MIGRATION_1_2,
        ).apply {
            query("SELECT position_ms FROM playback_positions WHERE media_key = 'video-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1200L, it.getLong(0))
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'playback_queue_items'").use {
                assertTrue(it.moveToFirst())
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'playback_queue_state'").use {
                assertTrue(it.moveToFirst())
            }
            query(
                "SELECT current_media_key, playback_mode, playback_speed " +
                    "FROM playback_queue_state WHERE id = 1",
            ).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertEquals("SEQUENTIAL", it.getString(1))
                assertEquals(1f, it.getFloat(2))
            }
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'playback_positions'").use {
                assertTrue(it.moveToFirst())
            }
            close()
        }
    }
}
