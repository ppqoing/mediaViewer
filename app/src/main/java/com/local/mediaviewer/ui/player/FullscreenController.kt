package com.local.mediaviewer.ui.player

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface FullscreenStateController : AutoCloseable {
    val isFullscreen: StateFlow<Boolean>

    fun enter()

    fun exit()

    override fun close()
}

class FullscreenController internal constructor(
    private val windowPolicy: FullscreenWindowPolicy,
) : FullscreenStateController {
    constructor(activity: Activity) : this(
        AndroidFullscreenWindowPolicy(activity),
    )

    private val mutableFullscreen = MutableStateFlow(false)
    override val isFullscreen: StateFlow<Boolean> =
        mutableFullscreen.asStateFlow()

    override fun enter() {
        windowPolicy.apply(
            fullscreen = true,
            decorFitsSystemWindows = false,
        )
        mutableFullscreen.value = true
    }

    override fun exit() {
        windowPolicy.apply(
            fullscreen = false,
            decorFitsSystemWindows = false,
        )
        mutableFullscreen.value = false
    }

    override fun close() {
        if (mutableFullscreen.value) {
            exit()
        }
    }
}
