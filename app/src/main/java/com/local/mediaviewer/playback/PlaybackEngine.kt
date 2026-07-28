package com.local.mediaviewer.playback

import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>

    fun prepare(url: String)

    fun attachVideoOutput(host: ViewGroup)

    fun detachVideoOutput()

    fun setVideoScaleMode(mode: VideoScaleMode)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    override fun close()
}

fun interface PlaybackEngineFactory {
    fun create(): PlaybackEngine
}
