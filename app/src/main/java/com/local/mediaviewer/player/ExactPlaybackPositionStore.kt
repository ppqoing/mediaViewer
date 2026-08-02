package com.local.mediaviewer.player

import com.local.mediaviewer.service.PlaybackPositionSnapshot

internal class ExactPlaybackPositionStore {
    private var latest: PlaybackPositionSnapshot? = null

    fun accept(
        currentMediaKey: String?,
        candidate: PlaybackPositionSnapshot,
    ): Boolean {
        if (currentMediaKey == null || candidate.mediaKey != currentMediaKey) {
            return false
        }
        latest = candidate
        return true
    }

    fun positionFor(currentMediaKey: String?): Long {
        val snapshot = latest
        if (currentMediaKey == null || snapshot?.mediaKey != currentMediaKey) {
            latest = null
            return 0L
        }
        return snapshot.positionMs
    }

    fun clear() {
        latest = null
    }
}
