package com.local.mediaviewer.playback

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackPositionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MediaViewerDatabase : RoomDatabase() {
    abstract fun playbackPositionDao(): PlaybackPositionDao
}
