package com.local.mediaviewer.player

import com.local.mediaviewer.playback.PlaybackStatus

object VideoInteractionReducer {
    fun canAutoHide(
        playbackStatus: PlaybackStatus,
        interaction: VideoInteractionState,
    ): Boolean =
        playbackStatus == PlaybackStatus.PLAYING &&
            interaction.controlsVisible &&
            !interaction.menuExpanded &&
            !interaction.scrubbing &&
            interaction.feedback == null

    fun toggleControls(state: VideoInteractionState): VideoInteractionState =
        if (state.controlsLocked) {
            state
        } else {
            state.copy(controlsVisible = !state.controlsVisible)
        }

    fun revealControls(state: VideoInteractionState): VideoInteractionState =
        if (state.controlsLocked) {
            state
        } else {
            state.copy(
                controlsVisible = true,
                autoHideEpoch = state.autoHideEpoch + 1,
            )
        }

    fun lock(state: VideoInteractionState): VideoInteractionState =
        state.copy(
            controlsLocked = true,
            controlsVisible = false,
            menuExpanded = false,
            feedback = null,
        )

    fun unlock(state: VideoInteractionState): VideoInteractionState =
        state.copy(controlsLocked = false, controlsVisible = true)
}
