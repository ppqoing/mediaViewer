package com.local.mediaviewer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackSessionState

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
) {
    val item = state.currentItem ?: return
    val playing = state.playback.status == PlaybackStatus.PLAYING
    var volumeExpanded by remember { mutableStateOf(false) }
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
            IconButton(onClick = onToggle) {
                NeonPlayerIcon(
                    if (playing) PlayerIcons.Pause else PlayerIcons.Play,
                    contentDescription = if (playing) "暂停" else "播放",
                    active = true,
                )
            }
            IconButton(onClick = onNext) {
                NeonPlayerIcon(PlayerIcons.Next, contentDescription = "下一项")
            }
            PlaybackVolumeControl(
                state = volumeState,
                expanded = volumeExpanded,
                onExpandedChanged = { volumeExpanded = it },
                onRefresh = onVolumeRefresh,
                onToggleMute = onToggleMute,
                onVolumeChanged = onVolumeChanged,
            )
            IconButton(onClick = onOpenQueue) {
                NeonPlayerIcon(PlayerIcons.Queue, contentDescription = "打开队列")
            }
        }
    }
}
