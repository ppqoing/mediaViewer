package com.local.mediaviewer.player

import android.view.ViewGroup
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.VideoScaleMode
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
