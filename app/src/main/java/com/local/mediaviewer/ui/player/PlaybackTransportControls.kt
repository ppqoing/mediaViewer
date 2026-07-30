package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
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
            icon = { enabled -> NeonPlayerIcon(PlayerIcons.Previous, null, enabled = enabled) },
            enabled = state.canSkipPrevious,
            onClick = onPrevious,
        )
        ControlButton(
            description = "快退 10 秒",
            icon = { enabled -> NeonPlayerIcon(PlayerIcons.Back10, null, enabled = enabled) },
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
            icon = { enabled -> NeonPlayerIcon(PlayerIcons.Forward10, null, enabled = enabled) },
            enabled = seekEnabled,
            onClick = onSeekForward,
        )
        ControlButton(
            description = "下一项",
            icon = { enabled -> NeonPlayerIcon(PlayerIcons.Next, null, enabled = enabled) },
            enabled = state.canSkipNext,
            onClick = onNext,
        )
    }
}

@Composable
private fun ControlButton(
    description: String,
    icon: @Composable (Boolean) -> Unit,
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
            icon(enabled)
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
        -> PrimaryAction("暂停", { enabled -> NeonPlayerIcon(PlayerIcons.Pause, null, active = true, enabled = enabled) }, onPause)

        PlaybackStatus.ENDED ->
            PrimaryAction(
                "重新播放",
                { enabled -> NeonPlayerIcon(PlayerIcons.Replay, null, active = true, enabled = enabled) },
                onReplay,
            )

        else -> PrimaryAction(
            "播放",
            { enabled -> NeonPlayerIcon(PlayerIcons.Play, null, active = true, enabled = enabled) },
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
            action.icon(state.status != PlaybackStatus.OPENING)
        }
    }
}

private data class PrimaryAction(
    val description: String,
    val icon: @Composable (Boolean) -> Unit,
    val onClick: () -> Unit,
)
