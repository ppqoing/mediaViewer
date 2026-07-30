package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.player.displayedPositionMs

@Composable
fun PlaybackTimeline(
    state: PlayerUiState,
    onBeginScrub: () -> Unit,
    onPreviewScrub: (Long) -> Unit,
    onCommitScrub: () -> Unit,
) {
    val maximum = state.durationMs.coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = state.displayedPositionMs
                .coerceIn(0L, maximum)
                .toFloat(),
            onValueChange = { value ->
                if (state.previewPositionMs == null) {
                    onBeginScrub()
                }
                onPreviewScrub(value.toLong())
            },
            onValueChangeFinished = onCommitScrub,
            valueRange = 0f..maximum.toFloat(),
            enabled = state.isSeekable && state.durationMs > 0L,
            modifier = Modifier.testTag("playback_timeline"),
        )
        Text(
            text = "${formatPlaybackTime(state.displayedPositionMs)} / " +
                formatPlaybackTime(state.durationMs),
        )
    }
}
