package com.local.mediaviewer.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SystemVolumeControllerTest {
    private val audioManager =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(AudioManager::class.java)
    private val controller = SystemVolumeController(audioManager)

    @Test
    fun `静音再次点击恢复静音前音量`() {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0)
        controller.refresh()

        controller.toggleMute()
        assertEquals(0, controller.state.value.current)

        controller.toggleMute()
        assertEquals(7, controller.state.value.current)
    }

    @Test
    fun `音量比例截断到系统范围`() {
        controller.setFraction(2f)
        assertEquals(controller.state.value.maximum, controller.state.value.current)

        controller.setFraction(-1f)
        assertEquals(0, controller.state.value.current)
    }
}
