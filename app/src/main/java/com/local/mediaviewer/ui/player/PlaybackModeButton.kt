package com.local.mediaviewer.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.local.mediaviewer.queue.PlaybackMode

@Composable
fun PlaybackModeButton(
    mode: PlaybackMode,
    onModeChanged: (PlaybackMode) -> Unit,
) {
    val label = mode.label()
    IconButton(
        onClick = { onModeChanged(mode.next()) },
        modifier = Modifier.semantics { stateDescription = label },
    ) {
        Icon(
            imageVector = when (mode) {
                PlaybackMode.SEQUENTIAL -> Icons.Default.FormatListBulleted
                PlaybackMode.REPEAT_ALL -> Icons.Default.Repeat
                PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
                PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
            },
            contentDescription = "播放模式：$label",
        )
        Text(label)
    }
}

fun PlaybackMode.next(): PlaybackMode = when (this) {
    PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ALL
    PlaybackMode.REPEAT_ALL -> PlaybackMode.REPEAT_ONE
    PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
    PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
}

fun PlaybackMode.label(): String = when (this) {
    PlaybackMode.SEQUENTIAL -> "顺序播放"
    PlaybackMode.REPEAT_ALL -> "列表循环"
    PlaybackMode.REPEAT_ONE -> "单曲循环"
    PlaybackMode.SHUFFLE -> "随机播放"
}
