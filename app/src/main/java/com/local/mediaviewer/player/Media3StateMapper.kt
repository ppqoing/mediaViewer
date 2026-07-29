package com.local.mediaviewer.player

import androidx.media3.common.Player
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem

data class Media3StateSnapshot(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long,
    val isSeekable: Boolean,
    val errorMessage: String?,
    val items: List<QueueMediaItem>,
    val currentMediaItemIndex: Int,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val playbackSpeed: Float,
)

object Media3StateMapper {
    fun map(
        connectionState: ControllerConnectionState,
        snapshot: Media3StateSnapshot?,
    ): PlaybackSessionState {
        if (connectionState !is ControllerConnectionState.Connected || snapshot == null) {
            return PlaybackSessionState(
                playback = PlaybackState(status = PlaybackStatus.OPENING),
                errorMessage = (connectionState as? ControllerConnectionState.Failed)?.message,
            )
        }

        val currentItem = snapshot.items.getOrNull(snapshot.currentMediaItemIndex)
        val mode = when {
            snapshot.shuffleModeEnabled -> PlaybackMode.SHUFFLE
            snapshot.repeatMode == Player.REPEAT_MODE_ONE -> PlaybackMode.REPEAT_ONE
            snapshot.repeatMode == Player.REPEAT_MODE_ALL -> PlaybackMode.REPEAT_ALL
            else -> PlaybackMode.SEQUENTIAL
        }
        val durationMs = snapshot.durationMs.coerceAtLeast(0L)
        val bufferedPercent = if (durationMs > 0L) {
            snapshot.bufferedPositionMs
                .coerceIn(0L, durationMs)
                .toFloat() * 100f / durationMs.toFloat()
        } else {
            0f
        }
        val status = when {
            snapshot.errorMessage != null -> PlaybackStatus.ERROR
            snapshot.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            snapshot.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
            snapshot.playbackState == Player.STATE_READY && snapshot.isPlaying ->
                PlaybackStatus.PLAYING
            snapshot.playbackState == Player.STATE_READY -> PlaybackStatus.PAUSED
            snapshot.playbackState == Player.STATE_IDLE && snapshot.playWhenReady ->
                PlaybackStatus.OPENING
            else -> PlaybackStatus.IDLE
        }
        val playback = PlaybackState(
            status = status,
            positionMs = snapshot.positionMs.coerceAtLeast(0L),
            durationMs = durationMs,
            bufferedPercent = bufferedPercent,
            isSeekable = snapshot.isSeekable,
            errorMessage = snapshot.errorMessage,
            playbackSpeed = snapshot.playbackSpeed,
        )
        return PlaybackSessionState(
            playback = playback,
            playWhenReady = snapshot.playWhenReady,
            queue = PlaybackQueue(
                items = snapshot.items,
                currentMediaKey = currentItem?.mediaKey,
                mode = mode,
                playbackSpeed = snapshot.playbackSpeed,
            ),
            currentItem = currentItem,
            errorMessage = snapshot.errorMessage,
            canSkipPrevious = snapshot.canSkipPrevious,
            canSkipNext = snapshot.canSkipNext,
        )
    }
}
