package com.local.mediaviewer.ui.player

import android.app.Activity
import android.provider.Settings
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PlayerBrightnessController : AutoCloseable {
    val fraction: StateFlow<Float>

    fun setFraction(value: Float)

    fun adjustByFraction(delta: Float)

    override fun close()
}

class WindowBrightnessController(
    private val activity: Activity,
) : PlayerBrightnessController {
    private val mutableFraction = MutableStateFlow(initialFraction())
    override val fraction: StateFlow<Float> = mutableFraction.asStateFlow()

    override fun setFraction(value: Float) {
        val next = value.coerceIn(MIN_BRIGHTNESS, 1f)
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = next
        }
        mutableFraction.value = next
    }

    override fun adjustByFraction(delta: Float) {
        setFraction(fraction.value + delta)
    }

    override fun close() {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun initialFraction(): Float {
        val windowBrightness = activity.window.attributes.screenBrightness
        if (windowBrightness >= 0f) return windowBrightness.coerceIn(MIN_BRIGHTNESS, 1f)
        val systemBrightness = Settings.System.getInt(
            activity.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            DEFAULT_SYSTEM_BRIGHTNESS,
        )
        return (systemBrightness / MAX_SYSTEM_BRIGHTNESS).coerceIn(MIN_BRIGHTNESS, 1f)
    }

    private companion object {
        const val MIN_BRIGHTNESS = 0.01f
        const val DEFAULT_SYSTEM_BRIGHTNESS = 128
        const val MAX_SYSTEM_BRIGHTNESS = 255f
    }
}
