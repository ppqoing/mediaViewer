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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.components.PlayerIconButton
import com.local.mediaviewer.ui.icons.MediaIcon
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
    primaryActionTag: String? = null,
) {
    val seekEnabled = state.isSeekable && state.durationMs > 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_transport_layer"),
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
            actionTestTag = primaryActionTag,
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
    icon: MediaIcon,
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
    actionTestTag: String?,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(MediaTheme.sizing.playerPrimaryButton),
    ) {
        PlaybackPrimaryActionButton(
            status = state.status,
            size = MediaTheme.sizing.playerPrimaryButton,
            iconSize = 32.dp,
            onPlay = onPlay,
            onPause = onPause,
            onReplay = onReplay,
            actionTestTag = actionTestTag,
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
