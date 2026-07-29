package com.local.mediaviewer.playback

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.local.mediaviewer.queue.PlaybackQueueDao
import com.local.mediaviewer.queue.PlaybackQueueItemEntity
import com.local.mediaviewer.queue.PlaybackQueueStateEntity

@Database(
    entities = [
        PlaybackPositionEntity::class,
        PlaybackQueueItemEntity::class,
        PlaybackQueueStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MediaViewerDatabase : RoomDatabase() {
    abstract fun playbackPositionDao(): PlaybackPositionDao

    abstract fun playbackQueueDao(): PlaybackQueueDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_queue_items (" +
                        "media_key TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "logical_url TEXT NOT NULL, " +
                        "media_kind TEXT NOT NULL, " +
                        "sort_order INTEGER NOT NULL, " +
                        "PRIMARY KEY(media_key))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_queue_items_sort_order " +
                        "ON playback_queue_items(sort_order)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_queue_state (" +
                        "id INTEGER NOT NULL, " +
                        "current_media_key TEXT, " +
                        "playback_mode TEXT NOT NULL, " +
                        "shuffle_order_json TEXT NOT NULL, " +
                        "shuffle_cursor INTEGER NOT NULL, " +
                        "playback_speed REAL NOT NULL, " +
                        "updated_at INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_queue_state_updated_at " +
                        "ON playback_queue_state(updated_at)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO playback_queue_state " +
                        "(id, current_media_key, playback_mode, shuffle_order_json, shuffle_cursor, playback_speed, updated_at) " +
                        "VALUES (1, NULL, 'SEQUENTIAL', '[]', -1, 1.0, 0)",
                )
            }
        }
    }
}
