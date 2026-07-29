package com.local.mediaviewer.queue

import com.local.mediaviewer.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface PlaybackQueueRepository {
    val queue: StateFlow<PlaybackQueue>

    suspend fun restore(): PlaybackQueue

    suspend fun save(queue: PlaybackQueue)
}

class RoomPlaybackQueueRepository(
    private val dao: PlaybackQueueDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaybackQueueRepository {
    private val mutableQueue = MutableStateFlow(PlaybackQueue())

    override val queue: StateFlow<PlaybackQueue> = mutableQueue.asStateFlow()

    override suspend fun restore(): PlaybackQueue {
        val items = dao.items().map {
            QueueMediaItem(
                mediaKey = it.mediaKey,
                name = it.name,
                logicalUrl = it.logicalUrl,
                kind = MediaKind.entries.firstOrNull { kind -> kind.name == it.mediaKind } ?: MediaKind.UNKNOWN,
            )
        }
        val state = dao.state()
        val restored = state?.toQueue(items) ?: PlaybackQueue(items = items)
        if (state != null && restored.needsRepair(state)) {
            save(restored)
        } else {
            mutableQueue.value = restored
        }
        return mutableQueue.value
    }

    override suspend fun save(queue: PlaybackQueue) {
        dao.replaceSnapshot(
            items = queue.items.mapIndexed { index, item ->
                PlaybackQueueItemEntity(
                    mediaKey = item.mediaKey,
                    name = item.name,
                    logicalUrl = item.logicalUrl,
                    mediaKind = item.kind.name,
                    sortOrder = index,
                )
            },
            state = PlaybackQueueStateEntity(
                currentMediaKey = queue.currentMediaKey,
                playbackMode = queue.mode.name,
                shuffleOrderJson = Json.encodeToString(queue.shuffleOrder),
                shuffleCursor = queue.shuffleCursor,
                playbackSpeed = queue.playbackSpeed,
                updatedAtEpochMs = clock(),
            ),
        )
        mutableQueue.value = queue
    }

    private fun PlaybackQueueStateEntity.toQueue(items: List<QueueMediaItem>): PlaybackQueue {
        val mode = runCatching { PlaybackMode.valueOf(playbackMode) }.getOrNull()
        val shuffleOrder = runCatching { Json.decodeFromString<List<String>>(shuffleOrderJson) }.getOrNull()
        if (mode == null || shuffleOrder == null) {
            return PlaybackQueue(
                items = items,
                currentMediaKey = currentMediaKey,
                mode = PlaybackMode.SEQUENTIAL,
                playbackSpeed = playbackSpeed,
            )
        }
        return PlaybackQueue(
            items = items,
            currentMediaKey = currentMediaKey,
            mode = mode,
            shuffleOrder = shuffleOrder,
            shuffleCursor = shuffleCursor,
            playbackSpeed = playbackSpeed,
        )
    }

    private fun PlaybackQueue.needsRepair(state: PlaybackQueueStateEntity): Boolean =
        runCatching { PlaybackMode.valueOf(state.playbackMode) }.isFailure ||
            runCatching { Json.decodeFromString<List<String>>(state.shuffleOrderJson) }.isFailure
}
