package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.components.PlayerIconButton
import com.local.mediaviewer.ui.theme.MediaTheme

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
            icon = PlayerIcons.Previous,
            enabled = state.canSkipPrevious,
            onClick = onPrevious,
        )
        ControlButton(
            description = "快退 10 秒",
            icon = PlayerIcons.Back10,
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
            icon = PlayerIcons.Forward10,
            enabled = seekEnabled,
            onClick = onSeekForward,
        )
        ControlButton(
            description = "下一项",
            icon = PlayerIcons.Next,
            enabled = state.canSkipNext,
            onClick = onNext,
        )
    }
}

@Composable
private fun ControlButton(
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PlayerIconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(MediaTheme.sizing.minimumTouchTarget),
    )
}

@Composable
private fun PrimaryControlButton(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
) {
    val action = playbackPrimaryAction(state.status)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(MediaTheme.sizing.playerPrimaryButton),
    ) {
        PlayerIconButton(
            icon = action.icon,
            contentDescription = action.contentDescription,
            stateDescription = action.stateDescription,
            enabled = action.enabled,
            loading = action.loading,
            onClick = {
                action.command.invoke(onPlay, onPause, onReplay)
            },
            modifier = Modifier.size(MediaTheme.sizing.playerPrimaryButton),
        )
        if (state.status == PlaybackStatus.BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(18.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
