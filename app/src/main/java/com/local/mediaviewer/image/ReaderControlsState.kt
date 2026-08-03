package com.local.mediaviewer.image

import com.local.mediaviewer.settings.VideoControlsAutoHide

data class ReaderControlsState(
    val visible: Boolean = true,
    val interactionActive: Boolean = false,
    val autoHideEpoch: Long = 0L,
)

object ReaderControlsReducer {
    fun toggle(state: ReaderControlsState): ReaderControlsState =
        state.copy(visible = !state.visible)

    fun reveal(state: ReaderControlsState): ReaderControlsState =
        state.copy(
            visible = true,
            autoHideEpoch = state.autoHideEpoch + 1L,
        )

    fun beginInteraction(
        state: ReaderControlsState,
    ): ReaderControlsState =
        state.copy(interactionActive = true)

    fun endInteraction(
        state: ReaderControlsState,
    ): ReaderControlsState =
        state.copy(
            interactionActive = false,
            autoHideEpoch = state.autoHideEpoch + 1L,
        )

    fun autoHideDelayMs(
        state: ReaderControlsState,
        preference: VideoControlsAutoHide,
    ): Long? =
        if (state.visible && !state.interactionActive) {
            preference.delayMs
        } else {
            null
        }
}
