package com.local.mediaviewer.player

object PlayerInteractionReducer {
    fun seekTarget(
        positionMs: Long,
        durationMs: Long,
        deltaMs: Long,
    ): Long = (positionMs + deltaMs).coerceIn(
        0L,
        durationMs.coerceAtLeast(0L),
    )

    fun beginScrub(state: PlayerUiState): PlayerUiState =
        state.copy(previewPositionMs = state.positionMs)

    fun updateScrub(
        state: PlayerUiState,
        previewMs: Long,
    ): PlayerUiState =
        state.copy(
            previewPositionMs = previewMs.coerceIn(
                0L,
                state.durationMs.coerceAtLeast(0L),
            ),
        )

    fun finishScrub(
        state: PlayerUiState,
    ): Pair<PlayerUiState, Long?> =
        state.copy(previewPositionMs = null) to state.previewPositionMs
}
