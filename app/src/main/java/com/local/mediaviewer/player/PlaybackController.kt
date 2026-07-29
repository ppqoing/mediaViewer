package com.local.mediaviewer.player

import android.view.ViewGroup
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface ControllerConnectionState {
    data object Connecting : ControllerConnectionState

    data object Connected : ControllerConnectionState

    data object Dormant : ControllerConnectionState

    data class Failed(
        val message: String,
    ) : ControllerConnectionState
}

sealed interface VideoOutputConnectionState {
    data object Detached : VideoOutputConnectionState

    data object Connecting : VideoOutputConnectionState

    data object Attached : VideoOutputConnectionState

    data class Failed(
        val message: String,
    ) : VideoOutputConnectionState
}

interface PlaybackController : AutoCloseable {
    val state: StateFlow<PlaybackState>
    val videoOutputState: StateFlow<VideoOutputConnectionState>
        get() = detachedVideoOutputState

    fun prepare(url: String)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun setPlaybackSpeed(speed: Float)

    fun attachVideoOutput(host: ViewGroup)

    fun detachVideoOutput()

    fun retryVideoOutput() = Unit

    fun setVideoScaleMode(mode: VideoScaleMode)

    override fun close()
}

private val detachedVideoOutputState = MutableStateFlow<VideoOutputConnectionState>(
    VideoOutputConnectionState.Detached,
)

interface QueuePlaybackController : PlaybackController {
    val sessionState: StateFlow<PlaybackSessionState>

    fun replaceQueue(items: List<QueueMediaItem>, startMediaKey: String)

    fun playNext(item: QueueMediaItem)

    fun append(item: QueueMediaItem)

    fun select(mediaKey: String)

    fun reloadCurrent()

    fun skipPrevious()

    fun skipNext()

    fun move(mediaKey: String, toIndex: Int)

    fun remove(mediaKey: String)

    fun clearExceptCurrent()

    fun clearAll()

    fun setPlaybackMode(mode: PlaybackMode)

    fun onAppStarted() = Unit

    fun onAppStopped() = Unit
}
