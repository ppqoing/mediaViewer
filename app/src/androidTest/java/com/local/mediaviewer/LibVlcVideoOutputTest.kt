package com.local.mediaviewer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.VideoScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.util.VLCVideoLayout

@RunWith(AndroidJUnit4::class)
class LibVlcVideoOutputTest {
    @Test
    fun videoLayoutFillsHostAndAcceptsEveryScaleMode() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()
        val engine = AndroidVlcPlaybackEngine(context)
        try {
            ActivityScenario.launch(
                MainActivity::class.java,
            ).use { scenario ->
                scenario.onActivity { activity ->
                    val host = FrameLayout(activity).apply {
                        id = View.generateViewId()
                    }
                    activity.setContentView(
                        host,
                        ViewGroup.LayoutParams(800, 450),
                    )

                    engine.attachVideoOutput(host)

                    assertEquals(1, host.childCount)
                    val output = host.getChildAt(0)
                    assertTrue(output is VLCVideoLayout)
                    assertEquals(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        output.layoutParams.width,
                    )
                    assertEquals(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        output.layoutParams.height,
                    )
                    VideoScaleMode.entries.forEach(
                        engine::setVideoScaleMode,
                    )

                    engine.detachVideoOutput()
                    assertEquals(0, host.childCount)
                }
            }
        } finally {
            engine.close()
        }
    }
}
