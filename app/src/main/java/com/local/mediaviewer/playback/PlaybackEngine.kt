package com.local.mediaviewer.playback

import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>

    fun prepare(url: String)

    fun attachVideoSurface(surfaceView: SurfaceView)

    fun detachVideoSurface()

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    override fun close()
}

fun interface PlaybackEngineFactory {
    fun create(): PlaybackEngine
}
