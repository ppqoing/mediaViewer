package com.local.mediaviewer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.ui.components.PlayerIconButton

@Composable
fun NowPlayingBar(
    state: PlaybackSessionState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.currentItem ?: return
    val action = playbackPrimaryAction(state.playback.status)
    NowPlayingBarContent(
        item = item,
        action = action,
        onPrimaryAction = {
            action.command.invoke(onPlay, onPause, onReplay)
        },
        onNext = onNext,
        onOpenQueue = onOpenQueue,
        onOpenPlayer = onOpenPlayer,
        showBuffering = state.playback.status == com.local.mediaviewer.playback.PlaybackStatus.BUFFERING,
        modifier = modifier,
    )
}

@Deprecated("Flow Task 7 switches the root to the volume-free overload")
@Composable
fun NowPlayingBar(
    state: PlaybackSessionState,
    volumeState: VolumeState,
    onVolumeRefresh: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) = NowPlayingBar(
    state = state,
    onPlay = onToggle,
    onPause = onToggle,
    onReplay = onToggle,
    onNext = onNext,
    onOpenQueue = onOpenQueue,
    onOpenPlayer = onOpenPlayer,
    modifier = modifier,
)

@Composable
private fun NowPlayingBarContent(
    item: QueueMediaItem,
    action: PlaybackPrimaryAction,
    onPrimaryAction: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    showBuffering: Boolean,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenPlayer)
                    .semantics { contentDescription = "打开播放器：${item.name}" },
            )
            Box(contentAlignment = Alignment.Center) {
                PlayerIconButton(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    stateDescription = action.stateDescription,
                    enabled = action.enabled,
                    loading = action.loading,
                    onClick = onPrimaryAction,
                )
                if (showBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            PlayerIconButton(
                icon = PlayerIcons.Next,
                contentDescription = "下一项",
                onClick = onNext,
            )
            PlayerIconButton(
                icon = PlayerIcons.Queue,
                contentDescription = "打开队列",
                onClick = onOpenQueue,
            )
        }
    }
}
