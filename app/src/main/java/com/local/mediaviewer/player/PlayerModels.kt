package com.local.mediaviewer.player

import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode

data class PlayerRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)

data class PlayerUiState(
    val name: String,
    val kind: MediaKind,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Float = 0f,
    val isSeekable: Boolean = false,
    val resumedFromMs: Long? = null,
    val errorMessage: String? = null,
    val videoScaleMode:
        VideoScaleMode = VideoScaleMode.BEST_FIT,
    val previewPositionMs: Long? = null,
    val playbackSpeed: Float = 1f,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
)

val PlayerUiState.displayedPositionMs: Long
    get() = previewPositionMs ?: positionMs

fun PlayerUiState.withEngine(state: PlaybackState) = copy(
    status = state.status,
    positionMs = state.positionMs,
    durationMs = state.durationMs,
    bufferedPercent = state.bufferedPercent,
    isSeekable = state.isSeekable,
    errorMessage = state.errorMessage,
    playbackSpeed = state.playbackSpeed,
)
