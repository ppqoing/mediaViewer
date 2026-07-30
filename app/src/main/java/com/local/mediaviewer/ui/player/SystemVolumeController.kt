package com.local.mediaviewer.ui.player

import android.media.AudioManager
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VolumeState(
    val current: Int,
    val maximum: Int,
    val muted: Boolean,
) {
    val fraction: Float
        get() = if (maximum <= 0) 0f
        else (current.toFloat() / maximum).coerceIn(0f, 1f)

    val percent: Int
        get() = if (maximum == 0) 0 else (current * 100f / maximum).roundToInt()
}

interface PlayerVolumeController {
    val state: StateFlow<VolumeState>

    fun refresh()

    fun setFraction(value: Float)

    fun adjustByFraction(delta: Float)

    fun toggleMute()
}

class SystemVolumeController(
    private val audioManager: AudioManager,
    private val showSystemUi: Boolean = false,
) : PlayerVolumeController {
    private val mutableState = MutableStateFlow(readState())
    override val state: StateFlow<VolumeState> = mutableState.asStateFlow()

    private var unmutedVolume = mutableState.value.current.takeIf { it > 0 } ?: fallbackVolume()

    override fun refresh() {
        publish(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    override fun setFraction(value: Float) {
        val maximum = state.value.maximum
        val target = (value.coerceIn(0f, 1f) * maximum).roundToInt()
        setVolume(target)
    }

    override fun adjustByFraction(delta: Float) {
        val currentFraction =
            if (state.value.maximum == 0) 0f else state.value.current.toFloat() / state.value.maximum
        setFraction(currentFraction + delta)
    }

    override fun toggleMute() {
        if (state.value.current == 0) {
            setVolume(unmutedVolume)
        } else {
            setVolume(0)
        }
    }

    private fun setVolume(target: Int) {
        val maximum = state.value.maximum
        val next = target.coerceIn(0, maximum)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            next,
            if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0,
        )
        publish(next)
    }

    private fun publish(current: Int) {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val next = current.coerceIn(0, maximum)
        if (next > 0) unmutedVolume = next
        mutableState.value = VolumeState(next, maximum, next == 0)
    }

    private fun readState(): VolumeState {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maximum)
        return VolumeState(current, maximum, current == 0)
    }

    private fun fallbackVolume(): Int =
        (mutableState.value.maximum / 2).coerceAtLeast(1)
}
