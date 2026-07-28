package com.local.mediaviewer.playback

interface PlaybackPositionStore {
    suspend fun resumePosition(mediaKey: String): Long?

    suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean = false,
    )

    suspend fun clear(mediaKey: String)
}

class RoomPlaybackPositionStore(
    private val dao: PlaybackPositionDao,
) : PlaybackPositionStore {
    override suspend fun resumePosition(mediaKey: String): Long? =
        PlaybackPositionPolicy.resumePosition(dao.find(mediaKey))

    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) {
        if (
            PlaybackPositionPolicy.shouldDelete(
                positionMs = positionMs,
                durationMs = durationMs,
                ended = ended,
            )
        ) {
            dao.delete(mediaKey)
        } else {
            dao.upsert(
                PlaybackPositionEntity(
                    mediaKey = mediaKey,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    updatedAtEpochMs = updatedAtEpochMs,
                ),
            )
        }
    }

    override suspend fun clear(mediaKey: String) {
        dao.delete(mediaKey)
    }
}
