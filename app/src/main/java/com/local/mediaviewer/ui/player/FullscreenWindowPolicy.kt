package com.local.mediaviewer.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal fun interface FullscreenWindowPolicy {
    fun apply(
        fullscreen: Boolean,
        decorFitsSystemWindows: Boolean,
    )
}

internal class AndroidFullscreenWindowPolicy(
    private val activity: Activity,
) : FullscreenWindowPolicy {
    override fun apply(
        fullscreen: Boolean,
        decorFitsSystemWindows: Boolean,
    ) {
        WindowCompat.setDecorFitsSystemWindows(
            activity.window,
            decorFitsSystemWindows,
        )
        val bars = WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        )
        if (fullscreen) {
            activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
