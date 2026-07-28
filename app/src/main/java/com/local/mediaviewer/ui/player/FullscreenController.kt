package com.local.mediaviewer.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FullscreenController(
    private val activity: Activity,
) : AutoCloseable {
    private val mutableFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> =
        mutableFullscreen.asStateFlow()

    fun enter() {
        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        ).hide(WindowInsetsCompat.Type.systemBars())
        mutableFullscreen.value = true
    }

    fun exit() {
        WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        ).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        mutableFullscreen.value = false
    }

    override fun close() {
        if (mutableFullscreen.value) {
            exit()
        }
    }
}
