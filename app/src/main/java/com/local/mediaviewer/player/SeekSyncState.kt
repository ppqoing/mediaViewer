package com.local.mediaviewer.player

import com.local.mediaviewer.playback.PlaybackStatus
import kotlin.math.abs

data class PendingSeek(
    val mediaKey: String?,
    val targetMs: Long,
)

data class SeekSyncState(
    val previewMs: Long? = null,
    val pending: PendingSeek? = null,
) {
    fun begin(actualMs: Long) = copy(previewMs = actualMs)

    fun preview(targetMs: Long, durationMs: Long) = copy(
        previewMs = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L)),
    )

    fun commit(mediaKey: String?): Pair<SeekSyncState, Long?> {
        val target = previewMs ?: return this to null
        return copy(
            previewMs = null,
            pending = PendingSeek(mediaKey, target),
        ) to target
    }

    fun reconcile(
        mediaKey: String?,
        actualMs: Long,
        status: PlaybackStatus,
    ): SeekSyncState {
        val target = pending ?: return this
        val terminal = status == PlaybackStatus.ERROR ||
            status == PlaybackStatus.ENDED
        val mediaChanged = target.mediaKey != null &&
            mediaKey != null &&
            target.mediaKey != mediaKey
        val confirmed = abs(actualMs - target.targetMs) <=
            SEEK_CONFIRMATION_TOLERANCE_MS
        return if (terminal || mediaChanged || confirmed) {
            copy(pending = null)
        } else {
            this
        }
    }

    fun clear() = SeekSyncState()

    fun displayedPosition(actualMs: Long): Long =
        previewMs ?: pending?.targetMs ?: actualMs
}

internal const val SEEK_CONFIRMATION_TOLERANCE_MS = 1_000L
