package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus

@Composable
fun PlayerControls(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    trailingControl: @Composable () -> Unit = {},
) {
    val sliderMaximum = state.durationMs.coerceAtLeast(1L)
    val sliderPosition = state.positionMs.coerceIn(0L, sliderMaximum)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.resumedFromMs?.let { resumedFromMs ->
            Text(
                text = "已从 ${formatPlaybackTime(resumedFromMs)} 继续播放",
            )
        }
        if (state.status == PlaybackStatus.BUFFERING) {
            LinearProgressIndicator(
                progress = { state.bufferedPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Slider(
            value = sliderPosition.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..sliderMaximum.toFloat(),
            enabled = state.isSeekable,
            modifier = Modifier.testTag("seek"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatPlaybackTime(state.positionMs)} / " +
                    formatPlaybackTime(state.durationMs),
            )
            IconButton(
                onClick = if (
                    state.status == PlaybackStatus.PLAYING
                ) {
                    onPause
                } else {
                    onPlay
                },
            ) {
                val playing = state.status == PlaybackStatus.PLAYING
                Icon(
                    imageVector = if (playing) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (playing) {
                        "暂停"
                    } else {
                        "播放"
                    },
                )
            }
            trailingControl()
        }
        state.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
