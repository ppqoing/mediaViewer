package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun PlaybackVolumeControl(
    state: VolumeState,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (state.maximum == 0) 0f else state.current.toFloat() / state.maximum
    val description = state.accessibilityDescription()

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                onToggleMute()
                onExpandedChanged(true)
            },
        ) {
            Icon(
                imageVector = if (state.muted) Icons.AutoMirrored.Default.VolumeOff else Icons.AutoMirrored.Default.VolumeUp,
                contentDescription = description,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChanged(false) },
        ) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    imageVector = if (state.muted) Icons.AutoMirrored.Default.VolumeUp else Icons.AutoMirrored.Default.VolumeOff,
                    contentDescription = if (state.muted) "取消静音" else "静音",
                )
            }
            Slider(
                value = fraction,
                onValueChange = onVolumeChanged,
                valueRange = 0f..1f,
                modifier = Modifier.testTag("volume_slider"),
            )
        }
    }
}

internal fun VolumeState.accessibilityDescription(): String =
    "音量，当前 $percent%，${if (muted) "已静音" else "未静音"}"
