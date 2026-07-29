package com.local.mediaviewer.playback

import android.content.Context
import androidx.room.Room

object MediaViewerDatabaseFactory {
    fun create(
        context: Context,
        databaseName: String = "mediaviewer.db",
    ): MediaViewerDatabase = Room.databaseBuilder(
        context,
        MediaViewerDatabase::class.java,
        databaseName,
    ).addMigrations(MediaViewerDatabase.MIGRATION_1_2).build()
}
