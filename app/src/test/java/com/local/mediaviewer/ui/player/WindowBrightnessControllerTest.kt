package com.local.mediaviewer.ui.player

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WindowBrightnessControllerTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val controller = WindowBrightnessController(activity)

    @Test
    fun `亮度限定范围且关闭恢复默认窗口策略`() {
        controller.setFraction(0f)
        assertEquals(0.01f, activity.window.attributes.screenBrightness)

        controller.setFraction(2f)
        assertEquals(1f, activity.window.attributes.screenBrightness)

        controller.close()
        assertEquals(
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
            activity.window.attributes.screenBrightness,
        )
    }
}
