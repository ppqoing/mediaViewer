package com.local.mediaviewer.playback

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE media_key = :mediaKey")
    suspend fun find(mediaKey: String): PlaybackPositionEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE media_key = :mediaKey")
    suspend fun delete(mediaKey: String)
}
