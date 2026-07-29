package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus

@Composable
fun VideoControlsOverlay(
    state: PlayerUiState,
    locked: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onBeginScrub: () -> Unit,
    onPreviewScrub: (Long) -> Unit,
    onCommitScrub: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onVideoScaleModeChanged: (com.local.mediaviewer.playback.VideoScaleMode) -> Unit,
    volumeState: VolumeState,
    volumeExpanded: Boolean,
    onVolumeExpandedChanged: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onMenuExpandedChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_controls"),
    ) {
        if (locked) {
            IconButton(
                onClick = onUnlock,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.LockOpen, "解锁控制")
            }
            return@Box
        }

        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                }
                Text(
                    text = state.name,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.SkipNext, "播放列表（即将支持）")
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onSeekBack,
                enabled = state.isSeekable && state.durationMs > 0L,
            ) {
                Icon(Icons.Default.Replay10, "快退 10 秒")
            }
            PrimaryVideoControl(
                state = state,
                onPlay = onPlay,
                onPause = onPause,
                onReplay = onReplay,
            )
            IconButton(
                onClick = onSeekForward,
                enabled = state.isSeekable && state.durationMs > 0L,
            ) {
                Icon(Icons.Default.Forward10, "快进 10 秒")
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                PlaybackTimeline(
                    state = state,
                    onBeginScrub = onBeginScrub,
                    onPreviewScrub = onPreviewScrub,
                    onCommitScrub = onCommitScrub,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, enabled = state.canSkipPrevious) {
                        Icon(Icons.Default.SkipPrevious, "上一项")
                    }
                    IconButton(onClick = onNext, enabled = state.canSkipNext) {
                        Icon(Icons.Default.SkipNext, "下一项")
                    }
                    PlaybackSpeedMenu(
                        current = state.playbackSpeed,
                        onSpeedChanged = onSpeedChanged,
                        onExpandedChanged = onMenuExpandedChanged,
                    )
                    VideoScaleMenu(
                        current = state.videoScaleMode,
                        onSelected = onVideoScaleModeChanged,
                        onExpandedChanged = onMenuExpandedChanged,
                    )
                    PlaybackVolumeControl(
                        state = volumeState,
                        expanded = volumeExpanded,
                        onExpandedChanged = onVolumeExpandedChanged,
                        onToggleMute = onToggleMute,
                        onVolumeChanged = onVolumeChanged,
                    )
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, "锁定控制")
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.FullscreenExit, "退出全屏")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryVideoControl(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
) {
    val (description, icon, action) = when (state.status) {
        PlaybackStatus.PLAYING,
        PlaybackStatus.BUFFERING,
        -> Triple("暂停", Icons.Default.Pause, onPause)

        PlaybackStatus.ENDED -> Triple("重新播放", Icons.Default.Replay, onReplay)
        else -> Triple("播放", Icons.Default.PlayArrow, onPlay)
    }
    FilledIconButton(onClick = action, enabled = state.status != PlaybackStatus.OPENING) {
        Icon(icon, description)
    }
}
