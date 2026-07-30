package com.local.mediaviewer.playback

sealed interface EngineEvent {
    data object Opening : EngineEvent

    data class Buffering(val percent: Float) : EngineEvent

    data object Playing : EngineEvent

    data object Paused : EngineEvent

    data class TimeChanged(val positionMs: Long) : EngineEvent

    data class DurationChanged(val durationMs: Long) : EngineEvent

    data class SeekableChanged(val seekable: Boolean) : EngineEvent

    data object EndReached : EngineEvent

    data class Error(val message: String) : EngineEvent
}

object EngineEventReducer {
    fun reduce(
        state: PlaybackState,
        event: EngineEvent,
    ): PlaybackState =
        when (event) {
            EngineEvent.Opening -> state.copy(
                status = PlaybackStatus.OPENING,
                errorMessage = null,
            )

            is EngineEvent.Buffering -> state.copy(
                status = if (event.percent < 100f) {
                    PlaybackStatus.BUFFERING
                } else if (state.status == PlaybackStatus.BUFFERING) {
                    PlaybackStatus.PLAYING
                } else {
                    state.status
                },
                bufferedPercent = event.percent.coerceIn(0f, 100f),
            )

            EngineEvent.Playing -> state.copy(
                status = PlaybackStatus.PLAYING,
            )

            EngineEvent.Paused -> state.copy(
                status = PlaybackStatus.PAUSED,
            )

            is EngineEvent.TimeChanged -> state.copy(
                positionMs = event.positionMs.coerceAtLeast(0L),
            )

            is EngineEvent.DurationChanged -> state.copy(
                durationMs = event.durationMs.coerceAtLeast(0L),
            )

            is EngineEvent.SeekableChanged -> state.copy(
                isSeekable = event.seekable,
            )

            EngineEvent.EndReached -> state.copy(
                status = PlaybackStatus.ENDED,
            )

            is EngineEvent.Error -> state.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = event.message,
            )
        }
}
