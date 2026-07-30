package com.local.mediaviewer.playback

import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

object PlaybackSpeeds {
    val supported = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    fun requireSupported(value: Float): Float {
        require(supported.any { it == value }) {
            "不支持的播放倍速：$value"
        }
        return value
    }
}

interface PlaybackEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>

    fun prepare(url: String)

    fun attachVideoOutput(host: ViewGroup)

    fun detachVideoOutput()

    fun refreshVideoOutput() = Unit

    fun setVideoScaleMode(mode: VideoScaleMode)

    fun setPlaybackSpeed(speed: Float)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    override fun close()
}

fun interface PlaybackEngineFactory {
    fun create(): PlaybackEngine
}
