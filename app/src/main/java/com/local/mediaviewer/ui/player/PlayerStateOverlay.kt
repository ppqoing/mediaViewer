package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel

enum class PlayerOverlayKind {
    OPENING,
    BUFFERING,
    ERROR,
}

@Composable
fun PlayerStateOverlay(
    kind: PlayerOverlayKind,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tag = when (kind) {
        PlayerOverlayKind.OPENING -> "player_state_opening"
        PlayerOverlayKind.BUFFERING -> "player_state_buffering"
        PlayerOverlayKind.ERROR -> "player_state_error"
    }
    val title = when (kind) {
        PlayerOverlayKind.OPENING -> "正在打开媒体"
        PlayerOverlayKind.BUFFERING -> "正在缓冲"
        PlayerOverlayKind.ERROR -> "播放失败"
    }

    Box(modifier = modifier.testTag(tag)) {
        if (kind == PlayerOverlayKind.ERROR) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MediaStatePanel(
                    kind = MediaStateKind.ERROR,
                    title = title,
                    message = message,
                    primaryAction = onRetry?.let { MediaAction("重试", it) },
                )
                onBack?.let { callback ->
                    TextButton(onClick = callback) {
                        Text("返回")
                    }
                }
            }
        } else {
            MediaStatePanel(
                kind = MediaStateKind.LOADING,
                title = title,
                message = message,
            )
        }
    }
}
