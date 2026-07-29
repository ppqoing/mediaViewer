package com.local.mediaviewer.player

import android.view.ViewGroup
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.VideoScaleMode
import kotlinx.coroutines.flow.StateFlow

class EnginePlaybackController(
    private val engine: PlaybackEngine,
) : PlaybackController {
    override val state: StateFlow<PlaybackState> = engine.state

    override fun prepare(url: String) = engine.prepare(url)

    override fun play() = engine.play()

    override fun pause() = engine.pause()

    override fun stop() = engine.stop()

    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)

    override fun setPlaybackSpeed(speed: Float) =
        engine.setPlaybackSpeed(speed)

    override fun attachVideoOutput(host: ViewGroup) =
        engine.attachVideoOutput(host)

    override fun detachVideoOutput() = engine.detachVideoOutput()

    override fun setVideoScaleMode(mode: VideoScaleMode) =
        engine.setVideoScaleMode(mode)

    override fun close() = engine.close()
}
