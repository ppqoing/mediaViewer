package com.local.mediaviewer.queue

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_queue_items",
    indices = [Index(value = ["sort_order"])],
)
data class PlaybackQueueItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_key")
    val mediaKey: String,
    val name: String,
    @ColumnInfo(name = "logical_url")
    val logicalUrl: String,
    @ColumnInfo(name = "media_kind")
    val mediaKind: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

@Entity(
    tableName = "playback_queue_state",
    indices = [Index(value = ["updated_at"])],
)
data class PlaybackQueueStateEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "current_media_key")
    val currentMediaKey: String?,
    @ColumnInfo(name = "playback_mode")
    val playbackMode: String,
    @ColumnInfo(name = "shuffle_order_json")
    val shuffleOrderJson: String,
    @ColumnInfo(name = "shuffle_cursor")
    val shuffleCursor: Int,
    @ColumnInfo(name = "playback_speed")
    val playbackSpeed: Float,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
