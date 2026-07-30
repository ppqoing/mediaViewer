package com.local.mediaviewer.ui.player

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.local.mediaviewer.playback.PlaybackSpeeds
import java.util.Locale

@Composable
fun PlaybackSpeedMenu(
    current: Float,
    onSpeedChanged: (Float) -> Unit,
    onExpandedChanged: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val description = "播放速度，当前 ${formatPlaybackSpeed(current)} 倍"

    TextButton(
        onClick = {
            expanded = true
            onExpandedChanged(true)
        },
        modifier = Modifier.semantics {
            contentDescription = description
        },
    ) {
        NeonPlayerIcon(PlayerIcons.Speed, contentDescription = null)
        Text("${formatPlaybackSpeed(current)} 倍")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            onExpandedChanged(false)
        },
    ) {
        PlaybackSpeeds.supported.forEach { speed ->
            DropdownMenuItem(
                text = { Text("${formatPlaybackSpeed(speed)} 倍") },
                onClick = {
                    onSpeedChanged(speed)
                    expanded = false
                    onExpandedChanged(false)
                },
                trailingIcon = {
                    if (speed == current) {
                        NeonPlayerIcon(
                            icon = PlayerIcons.Playing,
                            contentDescription = "已选择",
                            active = true,
                        )
                    }
                },
            )
        }
    }
}

fun formatPlaybackSpeed(speed: Float): String = when {
    speed % 1f == 0f -> String.format(Locale.ROOT, "%.1f", speed)
    speed * 10f % 1f == 0f -> String.format(Locale.ROOT, "%.1f", speed)
    else -> String.format(Locale.ROOT, "%.2f", speed)
}
