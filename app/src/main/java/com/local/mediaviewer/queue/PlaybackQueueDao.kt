package com.local.mediaviewer.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface PlaybackQueueDao {
    @Query("SELECT * FROM playback_queue_items ORDER BY sort_order")
    suspend fun items(): List<PlaybackQueueItemEntity>

    @Query("SELECT * FROM playback_queue_state WHERE id = 1")
    suspend fun state(): PlaybackQueueStateEntity?

    @Query("DELETE FROM playback_queue_items")
    suspend fun deleteAllItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaybackQueueItemEntity>)

    @Upsert
    suspend fun upsertState(state: PlaybackQueueStateEntity)

    @Transaction
    suspend fun replaceSnapshot(
        items: List<PlaybackQueueItemEntity>,
        state: PlaybackQueueStateEntity,
    ) {
        deleteAllItems()
        insertItems(items)
        upsertState(state)
    }
}
