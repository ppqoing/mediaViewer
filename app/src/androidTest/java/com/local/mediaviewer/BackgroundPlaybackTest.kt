package com.local.mediaviewer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.player.Media3PlaybackController
import com.local.mediaviewer.player.VideoOutputConnectionState
import com.local.mediaviewer.testing.BackgroundPlaybackTestHarness
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackTest {
    @Test
    fun videoKeepsPlayingWithoutSurfaceAndReattachesContinuously() {
        BackgroundPlaybackTestHarness().use { harness ->
            harness.connectController().use { systemController ->
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    systemController.run {
                        setMediaItems(harness.videoQueue())
                        prepare()
                        play()
                    }
                    harness.waitUntil("fixture video starts") {
                        systemController.read(Player::isPlaying)
                    }

                    val uiController =
                        harness.container.playbackController as Media3PlaybackController
                    scenario.onActivity { activity ->
                        val host = FrameLayout(activity)
                        activity.setContentView(
                            host,
                            ViewGroup.LayoutParams(800, 450),
                        )
                        uiController.attachVideoOutput(host)
                    }
                    harness.waitUntil("first video output attaches") {
                        uiController.videoOutputState.value ==
                            VideoOutputConnectionState.Attached
                    }

                    systemController.run { seekTo(0L) }
                    harness.waitUntil("seek returns to fixture start") {
                        systemController.read(Player::getCurrentPosition) < 750L
                    }
                    uiController.detachVideoOutput()
                    val beforeBackground =
                        systemController.read(Player::getCurrentPosition)
                    scenario.onActivity { it.moveTaskToBack(true) }

                    Thread.sleep(2_000L)

                    val inBackground =
                        systemController.read(Player::getCurrentPosition)
                    assertTrue(
                        "background position should advance: $beforeBackground -> $inBackground",
                        inBackground > beforeBackground + 500L,
                    )
                    assertTrue(systemController.read(Player::isPlaying))

                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity { activity ->
                        val replacement = FrameLayout(activity)
                        activity.setContentView(
                            replacement,
                            ViewGroup.LayoutParams(800, 450),
                        )
                        uiController.attachVideoOutput(replacement)
                    }
                    harness.waitUntil("replacement video output attaches") {
                        uiController.videoOutputState.value ==
                            VideoOutputConnectionState.Attached
                    }
                    val afterReattach =
                        systemController.read(Player::getCurrentPosition)
                    assertTrue(afterReattach >= inBackground)
                    assertTrue(afterReattach > 0L)
                }
            }
        }
    }
}
