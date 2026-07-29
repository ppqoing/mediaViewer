package com.local.mediaviewer.queue

import com.local.mediaviewer.model.MediaKind
import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackMode {
    SEQUENTIAL,
    REPEAT_ALL,
    REPEAT_ONE,
    SHUFFLE,
}

@Serializable
data class QueueMediaItem(
    val mediaKey: String,
    val name: String,
    val logicalUrl: String,
    val kind: MediaKind,
)

@Serializable
data class PlaybackQueue(
    val items: List<QueueMediaItem> = emptyList(),
    val currentMediaKey: String? = null,
    val mode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val shuffleOrder: List<String> = emptyList(),
    val shuffleCursor: Int = -1,
    val playbackSpeed: Float = 1f,
) {
    val currentItem: QueueMediaItem?
        get() = items.firstOrNull { it.mediaKey == currentMediaKey }

    val currentIndex: Int
        get() = items.indexOfFirst { it.mediaKey == currentMediaKey }
}

enum class QueueAdvanceReason {
    USER,
    ENDED,
    CURRENT_REMOVED,
}
