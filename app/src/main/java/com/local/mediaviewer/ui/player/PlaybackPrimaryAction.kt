package com.local.mediaviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.theme.MediaTheme

enum class PlaybackPrimaryCommand {
    PLAY,
    PAUSE,
    REPLAY,
    NONE,
}

data class PlaybackPrimaryAction(
    val command: PlaybackPrimaryCommand,
    val icon: MediaIcon,
    val contentDescription: String,
    val stateDescription: String? = null,
    val enabled: Boolean = true,
    val loading: Boolean = false,
)

fun playbackPrimaryAction(status: PlaybackStatus): PlaybackPrimaryAction =
    when (status) {
        PlaybackStatus.IDLE,
        PlaybackStatus.PAUSED,
        -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.PLAY,
            icon = PlayerIcons.Play,
            contentDescription = "播放",
        )

        PlaybackStatus.PLAYING -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.PAUSE,
            icon = PlayerIcons.Pause,
            contentDescription = "暂停",
        )

        PlaybackStatus.BUFFERING -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.PAUSE,
            icon = PlayerIcons.Pause,
            contentDescription = "暂停",
            stateDescription = "正在缓冲，可暂停",
        )

        PlaybackStatus.ENDED -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.REPLAY,
            icon = PlayerIcons.Replay,
            contentDescription = "重新播放",
        )

        PlaybackStatus.OPENING -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.NONE,
            icon = PlayerIcons.Play,
            contentDescription = "正在打开",
            stateDescription = "正在打开媒体",
            enabled = false,
            loading = true,
        )

        PlaybackStatus.ERROR -> PlaybackPrimaryAction(
            command = PlaybackPrimaryCommand.NONE,
            icon = PlayerIcons.Play,
            contentDescription = "播放不可用",
            stateDescription = "播放错误",
            enabled = false,
        )
    }

@Composable
fun PlaybackPrimaryActionButton(
    status: PlaybackStatus,
    size: Dp,
    iconSize: Dp,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
    actionTestTag: String? = null,
    iconTestTag: String = "playback_primary_icon",
) {
    val action = playbackPrimaryAction(status)
    val colors = MediaTheme.playerColors
    val actionModifier = if (actionTestTag == null) {
        Modifier
    } else {
        Modifier.testTag(actionTestTag)
    }
    Box(
        modifier = modifier
            .size(size)
            .testTag("playback_primary_action")
            .clip(CircleShape)
            .background(
                colors.active.copy(
                    alpha = if (action.enabled) 1f else 0.38f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = {
                action.command.invoke(onPlay, onPause, onReplay)
            },
            enabled = action.enabled && !action.loading,
            modifier = actionModifier
                .fillMaxSize()
                .semantics {
                    contentDescription = action.contentDescription
                    action.stateDescription?.let {
                        stateDescription = it
                    }
                },
        ) {
            if (action.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = colors.control,
                    strokeWidth = 2.dp,
                )
            } else {
                MediaIconImage(
                    icon = action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = if (action.enabled) 1f else 0.60f,
                    ),
                    modifier = Modifier
                        .size(iconSize)
                        .testTag(iconTestTag),
                )
            }
        }
    }
}

internal fun PlaybackPrimaryCommand.invoke(
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
) = when (this) {
    PlaybackPrimaryCommand.PLAY -> onPlay()
    PlaybackPrimaryCommand.PAUSE -> onPause()
    PlaybackPrimaryCommand.REPLAY -> onReplay()
    PlaybackPrimaryCommand.NONE -> Unit
}
