package com.local.mediaviewer.playback

enum class PlaybackStatus {
    IDLE,
    OPENING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Float = 0f,
    val isSeekable: Boolean = false,
    val errorMessage: String? = null,
    val playbackSpeed: Float = 1f,
)
