package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PlaybackVolumeControl(
    state: VolumeState,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = state.accessibilityDescription()

    LaunchedEffect(expanded) {
        while (expanded) {
            onRefresh()
            delay(250L)
        }
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = { onExpandedChanged(!expanded) },
        ) {
            NeonPlayerIcon(
                icon = if (state.muted) PlayerIcons.Muted else PlayerIcons.Volume,
                contentDescription = description,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChanged(false) },
        ) {
            Text(
                text = "${state.percent}%",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Box(
                modifier = Modifier
                    .testTag("volume_popup")
                    .width(64.dp)
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Slider(
                    value = state.fraction,
                    onValueChange = onVolumeChanged,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .width(192.dp)
                        .graphicsLayer { rotationZ = -90f }
                        .testTag("volume_slider_vertical"),
                )
            }
            IconButton(onClick = onToggleMute) {
                NeonPlayerIcon(
                    icon = if (state.muted) PlayerIcons.Volume else PlayerIcons.Muted,
                    contentDescription = if (state.muted) "取消静音" else "静音",
                )
            }
        }
    }
}

internal fun VolumeState.accessibilityDescription(): String =
    "音量，当前 $percent%，${if (muted) "已静音" else "未静音"}"
