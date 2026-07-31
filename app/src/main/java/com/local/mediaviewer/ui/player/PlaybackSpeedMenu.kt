package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
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
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import java.util.Locale

@Composable
fun PlaybackSpeedMenu(
    current: Float,
    onSpeedChanged: (Float) -> Unit,
    onExpandedChanged: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val description = "播放速度，当前 ${formatPlaybackSpeed(current)} 倍"

    Box {
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
        MediaOptionMenu(
            expanded = expanded,
            options = PlaybackSpeeds.supported.map { speed ->
                MediaOption(
                    key = speed,
                    label = "${formatPlaybackSpeed(speed)} 倍",
                )
            },
            selectedKey = current,
            onSelect = { speed ->
                onSpeedChanged(speed)
                expanded = false
                onExpandedChanged(false)
            },
            onDismissRequest = {
                expanded = false
                onExpandedChanged(false)
            },
        )
    }
}

fun formatPlaybackSpeed(speed: Float): String = when {
    speed % 1f == 0f -> String.format(Locale.ROOT, "%.1f", speed)
    speed * 10f % 1f == 0f -> String.format(Locale.ROOT, "%.1f", speed)
    else -> String.format(Locale.ROOT, "%.2f", speed)
}
