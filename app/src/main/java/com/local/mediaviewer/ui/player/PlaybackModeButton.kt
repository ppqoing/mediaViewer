package com.local.mediaviewer.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.queue.PlaybackMode

@Composable
fun PlaybackModeButton(
    mode: PlaybackMode,
    onModeChanged: (PlaybackMode) -> Unit,
) {
    val label = mode.label()
    TextButton(
        onClick = { onModeChanged(mode.next()) },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "播放模式：$label"
                stateDescription = "当前$label"
                selected = true
            },
    ) {
        Row {
            Icon(
                imageVector = when (mode) {
                    PlaybackMode.SEQUENTIAL -> Icons.Default.FormatListBulleted
                    PlaybackMode.REPEAT_ALL -> Icons.Default.Repeat
                    PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
                    PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                },
                contentDescription = null,
            )
            Text(label, modifier = Modifier.padding(start = 4.dp))
        }
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
