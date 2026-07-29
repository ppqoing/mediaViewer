package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.queue.PlaybackMode

@Composable
fun PlayerControls(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onBeginScrub: () -> Unit,
    onPreviewScrub: (Long) -> Unit,
    onCommitScrub: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    playbackMode: PlaybackMode? = null,
    onPlaybackModeChanged: (PlaybackMode) -> Unit = {},
    onOpenQueue: (() -> Unit)? = null,
    secondaryControls: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.resumedFromMs?.let { resumedFromMs ->
            Text("已从 ${formatPlaybackTime(resumedFromMs)} 继续播放")
        }
        PlaybackTimeline(
            state = state,
            onBeginScrub = onBeginScrub,
            onPreviewScrub = onPreviewScrub,
            onCommitScrub = onCommitScrub,
        )
        PlaybackTransportControls(
            state = state,
            onPlay = onPlay,
            onPause = onPause,
            onReplay = onReplay,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            PlaybackSpeedMenu(
                current = state.playbackSpeed,
                onSpeedChanged = onSpeedChanged,
            )
            playbackMode?.let { mode ->
                PlaybackModeButton(
                    mode = mode,
                    onModeChanged = onPlaybackModeChanged,
                )
            }
            onOpenQueue?.let { openQueue ->
                IconButton(onClick = openQueue) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "打开队列")
                }
            }
            secondaryControls()
        }
    }
}
