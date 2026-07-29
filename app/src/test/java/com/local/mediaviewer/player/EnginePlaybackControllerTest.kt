package com.local.mediaviewer.player

import android.view.ViewGroup
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.VideoScaleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class EnginePlaybackControllerTest {
    @Test
    fun `控制器将播放命令直接委托给引擎`() {
        val engine = RecordingEngine()
        val controller = EnginePlaybackController(engine)

        controller.prepare("https://example.test/movie.mp4")
        controller.play()
        controller.pause()
        controller.stop()
        controller.seekTo(12_000L)
        controller.setPlaybackSpeed(1.5f)
        controller.setVideoScaleMode(VideoScaleMode.FILL_CROP)
        controller.close()

        assertEquals(
            listOf(
                "prepare:https://example.test/movie.mp4",
                "play",
                "pause",
                "stop",
                "seek:12000",
                "speed:1.5",
                "scale:FILL_CROP",
                "close",
            ),
            engine.commands,
        )
        assertEquals(engine.state, controller.state)
    }
}

private class RecordingEngine : PlaybackEngine {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState
    val commands = mutableListOf<String>()

    override fun prepare(url: String) {
        commands += "prepare:$url"
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit

    override fun detachVideoOutput() = Unit

    override fun setVideoScaleMode(mode: VideoScaleMode) {
        commands += "scale:$mode"
    }

    override fun setPlaybackSpeed(speed: Float) {
        commands += "speed:$speed"
    }

    override fun play() {
        commands += "play"
    }

    override fun pause() {
        commands += "pause"
    }

    override fun stop() {
        commands += "stop"
    }

    override fun seekTo(positionMs: Long) {
        commands += "seek:$positionMs"
    }

    override fun close() {
        commands += "close"
    }
}
