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
        state.copy(seekSync = state.seekSync.begin(state.positionMs))

    fun updateScrub(
        state: PlayerUiState,
        previewMs: Long,
    ): PlayerUiState =
        state.copy(
            seekSync = state.seekSync.preview(previewMs, state.durationMs),
        )

    fun finishScrub(
        state: PlayerUiState,
    ): Pair<PlayerUiState, Long?> {
        val (seekSync, target) = state.seekSync.commit(state.currentMediaKey)
        return state.copy(seekSync = seekSync) to target
    }
}
