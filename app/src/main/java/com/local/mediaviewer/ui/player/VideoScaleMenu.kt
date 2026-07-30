package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.local.mediaviewer.playback.VideoScaleMode

fun videoScaleLabel(mode: VideoScaleMode): String =
    when (mode) {
        VideoScaleMode.BEST_FIT -> "等比适应"
        VideoScaleMode.FILL_CROP -> "裁剪铺满"
        VideoScaleMode.STRETCH -> "强制拉伸"
        VideoScaleMode.ORIGINAL -> "原始尺寸"
    }

@Composable
fun VideoScaleMenu(
    current: VideoScaleMode,
    onSelected: (VideoScaleMode) -> Unit,
    onExpandedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(
            onClick = {
                expanded = true
                onExpandedChanged(true)
            },
            modifier = Modifier.testTag("video_scale_menu"),
        ) {
            NeonPlayerIcon(
                icon = PlayerIcons.Scale,
                contentDescription =
                    "画面模式：${videoScaleLabel(current)}",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onExpandedChanged(false)
            },
        ) {
            VideoScaleMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(videoScaleLabel(mode)) },
                    onClick = {
                        expanded = false
                        onExpandedChanged(false)
                        onSelected(mode)
                    },
                    leadingIcon = if (mode == current) {
                        {
                            NeonPlayerIcon(
                                icon = PlayerIcons.Playing,
                                contentDescription = null,
                                active = true,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
