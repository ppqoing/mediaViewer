package com.local.mediaviewer.player

import android.view.ViewGroup
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController : AutoCloseable {
    val state: StateFlow<PlaybackState>

    fun prepare(url: String)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun setPlaybackSpeed(speed: Float)

    fun attachVideoOutput(host: ViewGroup)

    fun detachVideoOutput()

    fun setVideoScaleMode(mode: VideoScaleMode)

    override fun close()
}

interface QueuePlaybackController : PlaybackController {
    val sessionState: StateFlow<PlaybackSessionState>

    fun replaceQueue(items: List<QueueMediaItem>, startMediaKey: String)

    fun playNext(item: QueueMediaItem)

    fun append(item: QueueMediaItem)

    fun select(mediaKey: String)

    fun skipPrevious()

    fun skipNext()

    fun move(mediaKey: String, toIndex: Int)

    fun remove(mediaKey: String)

    fun clearExceptCurrent()

    fun clearAll()

    fun setPlaybackMode(mode: PlaybackMode)
}
