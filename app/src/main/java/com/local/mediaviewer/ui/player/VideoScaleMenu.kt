package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import com.local.mediaviewer.ui.components.PlayerIconButton

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
        PlayerIconButton(
            icon = PlayerIcons.Scale,
            contentDescription = "画面比例",
            onClick = {
                expanded = true
                onExpandedChanged(true)
            },
            modifier = Modifier.testTag("video_scale_menu"),
            stateDescription = "当前${videoScaleLabel(current)}",
        )
        MediaOptionMenu(
            expanded = expanded,
            options = VideoScaleMode.entries.map { mode ->
                MediaOption(
                    key = mode,
                    label = videoScaleLabel(mode),
                )
            },
            selectedKey = current,
            onSelect = { mode ->
                expanded = false
                onExpandedChanged(false)
                onSelected(mode)
            },
            onDismissRequest = {
                expanded = false
                onExpandedChanged(false)
            },
        )
    }
}
