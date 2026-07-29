package com.local.mediaviewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.app.DefaultAppContainer
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibVlcEngineCreationTest {
    @Test
    fun createPrepareAndCloseNativeEngine() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = AndroidVlcPlaybackEngine(context)
        assertEquals(PlaybackStatus.IDLE, engine.state.value.status)

        engine.prepare("http://127.0.0.1:8080/middle/movie.mp4")
        assertEquals(PlaybackStatus.OPENING, engine.state.value.status)

        engine.close()
        engine.close()
        engine.detachVideoOutput()
        engine.play()
        engine.pause()
        engine.seekTo(10_000)
        assertThrows(IllegalStateException::class.java) {
            engine.prepare("http://127.0.0.1:8080/middle/other.mp4")
        }
    }

    @Test
    fun appContainerKeepsOneApplicationPlaybackController() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = DefaultAppContainer(context)
        val first = container.playbackController

        val second = container.playbackController

        assertSame(first, second)
        first.close()
    }
}
