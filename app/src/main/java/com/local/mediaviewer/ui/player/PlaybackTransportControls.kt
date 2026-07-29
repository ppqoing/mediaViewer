package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus

@Composable
fun PlaybackTransportControls(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val seekEnabled = state.isSeekable && state.durationMs > 0L

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ControlButton(
            description = "上一项",
            icon = { Icon(Icons.Default.SkipPrevious, null) },
            enabled = state.canSkipPrevious,
            onClick = onPrevious,
        )
        ControlButton(
            description = "快退 10 秒",
            icon = { Icon(Icons.Default.Replay10, null) },
            enabled = seekEnabled,
            onClick = onSeekBack,
        )
        PrimaryControlButton(
            state = state,
            onPlay = onPlay,
            onPause = onPause,
            onReplay = onReplay,
        )
        ControlButton(
            description = "快进 10 秒",
            icon = { Icon(Icons.Default.Forward10, null) },
            enabled = seekEnabled,
            onClick = onSeekForward,
        )
        ControlButton(
            description = "下一项",
            icon = { Icon(Icons.Default.SkipNext, null) },
            enabled = state.canSkipNext,
            onClick = onNext,
        )
    }
}

@Composable
private fun ControlButton(
    description: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.semantics {
                contentDescription = description
            },
        ) {
            icon()
        }
    }
}

@Composable
private fun PrimaryControlButton(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
) {
    val action = when (state.status) {
        PlaybackStatus.PLAYING,
        PlaybackStatus.BUFFERING,
        -> PrimaryAction("暂停", { Icon(Icons.Default.Pause, null) }, onPause)

        PlaybackStatus.ENDED ->
            PrimaryAction(
                "重新播放",
                { Icon(Icons.Default.Replay, null) },
                onReplay,
            )

        else -> PrimaryAction(
            "播放",
            { Icon(Icons.Default.PlayArrow, null) },
            onPlay,
        )
    }
    FilledIconButton(
        onClick = action.onClick,
        enabled = state.status != PlaybackStatus.OPENING,
        modifier = Modifier
            .size(64.dp)
            .semantics { role = Role.Button },
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.semantics {
                contentDescription = action.description
            },
        ) {
            action.icon()
        }
    }
}

private data class PrimaryAction(
    val description: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
)
