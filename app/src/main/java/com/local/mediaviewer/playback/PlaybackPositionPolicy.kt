package com.local.mediaviewer.playback

object PlaybackPositionPolicy {
    private const val MINIMUM_RESUME_MS = 10_000L
    private const val COMPLETION_DENOMINATOR = 20L

    fun resumePosition(entity: PlaybackPositionEntity?): Long? {
        entity ?: return null
        if (entity.positionMs < MINIMUM_RESUME_MS) return null
        if (shouldDelete(entity.positionMs, entity.durationMs, ended = false)) {
            return null
        }
        return entity.positionMs
    }

    fun shouldDelete(
        positionMs: Long,
        durationMs: Long,
        ended: Boolean,
    ): Boolean {
        if (ended) return true
        if (durationMs <= 0L) return false

        val completionThreshold =
            durationMs - durationMs / COMPLETION_DENOMINATOR
        return positionMs >= completionThreshold
    }
}
