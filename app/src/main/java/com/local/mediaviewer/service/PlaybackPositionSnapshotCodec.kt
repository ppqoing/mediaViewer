package com.local.mediaviewer.service

import android.os.Bundle
import com.local.mediaviewer.queue.PlaybackSessionState

const val ACTION_GET_EXACT_PLAYBACK_POSITION =
    "com.local.mediaviewer.action.GET_EXACT_PLAYBACK_POSITION"

data class PlaybackPositionSnapshot(
    val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
)

fun PlaybackSessionState.toPlaybackPositionSnapshot(): PlaybackPositionSnapshot? {
    val mediaKey = currentItem?.mediaKey
        ?: queue.currentMediaKey
        ?: return null
    if (mediaKey.isBlank()) return null
    val durationMs = playback.durationMs.coerceAtLeast(0L)
    val positionMs = playback.positionMs.coerceAtLeast(0L).let { position ->
        if (durationMs > 0L) position.coerceAtMost(durationMs) else position
    }
    return PlaybackPositionSnapshot(mediaKey, positionMs, durationMs)
}

object PlaybackPositionSnapshotCodec {
    private const val KEY_MEDIA_KEY = "media_key"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_DURATION_MS = "duration_ms"

    fun encode(snapshot: PlaybackPositionSnapshot): Bundle {
        val durationMs = snapshot.durationMs.coerceAtLeast(0L)
        val positionMs = snapshot.positionMs.coerceAtLeast(0L).let { position ->
            if (durationMs > 0L) position.coerceAtMost(durationMs) else position
        }
        return Bundle().apply {
            putString(KEY_MEDIA_KEY, snapshot.mediaKey)
            putLong(KEY_POSITION_MS, positionMs)
            putLong(KEY_DURATION_MS, durationMs)
        }
    }

    fun decode(bundle: Bundle): PlaybackPositionSnapshot? {
        val mediaKey = bundle.getString(KEY_MEDIA_KEY)
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (!bundle.containsKey(KEY_POSITION_MS) ||
            !bundle.containsKey(KEY_DURATION_MS)
        ) return null
        val positionMs = bundle.getLong(KEY_POSITION_MS)
        val durationMs = bundle.getLong(KEY_DURATION_MS)
        if (positionMs < 0L || durationMs < 0L) return null
        return PlaybackPositionSnapshot(
            mediaKey = mediaKey,
            positionMs = if (durationMs > 0L) {
                positionMs.coerceAtMost(durationMs)
            } else {
                positionMs
            },
            durationMs = durationMs,
        )
    }
}
