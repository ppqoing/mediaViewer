package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.player.displayedPositionMs
import com.local.mediaviewer.ui.components.MediaTimelineSlider

@Composable
fun PlaybackTimeline(
    state: PlayerUiState,
    onBeginScrub: () -> Unit,
    onPreviewScrub: (Long) -> Unit,
    onCommitScrub: () -> Unit,
) {
    val durationMs = state.durationMs.coerceAtLeast(0L)
    val positionMs = state.displayedPositionMs.coerceIn(0L, durationMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_timeline_layer"),
    ) {
        MediaTimelineSlider(
            durationMs = durationMs,
            positionMs = positionMs,
            enabled = state.isSeekable && state.durationMs > 0L,
            onDragStart = onBeginScrub,
            onPositionPreview = onPreviewScrub,
            onPositionCommit = { onCommitScrub() },
            modifier = Modifier.testTag("playback_timeline"),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatPlaybackTime(positionMs),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text(
                text = formatPlaybackTime(durationMs),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}
