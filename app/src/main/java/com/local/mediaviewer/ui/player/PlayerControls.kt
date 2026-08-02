package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun PlayerControls(
    state: PlayerUiState,
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
    playbackMode: PlaybackMode? = null,
    onPlaybackModeChanged: (PlaybackMode) -> Unit = {},
    onOpenQueue: (() -> Unit)? = null,
    showLowFrequencyControls: Boolean = true,
    primaryActionTag: String? = null,
    leadingUtilityControls: @Composable RowScope.() -> Unit = {},
    secondaryControls: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MediaTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xs),
    ) {
        state.resumedFromMs?.let { resumedFromMs ->
            Text("已从 ${formatPlaybackTime(resumedFromMs)} 继续播放")
        }
        PlaybackTimeline(
            state = state,
            onBeginScrub = onBeginScrub,
            onPreviewScrub = onPreviewScrub,
            onCommitScrub = onCommitScrub,
        )
        PlaybackTransportControls(
            state = state,
            onPlay = onPlay,
            onPause = onPause,
            onReplay = onReplay,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onPrevious = onPrevious,
            onNext = onNext,
            primaryActionTag = primaryActionTag,
        )
        PlayerUtilityRow(
            startContent = {
                if (showLowFrequencyControls) {
                    PlaybackSpeedMenu(
                        current = state.playbackSpeed,
                        onSpeedChanged = onSpeedChanged,
                    )
                    playbackMode?.let { mode ->
                        PlaybackModeButton(
                            mode = mode,
                            onModeChanged = onPlaybackModeChanged,
                        )
                    }
                }
                leadingUtilityControls()
            },
            endContent = {
                onOpenQueue?.let { openQueue ->
                    MediaIconButton(
                        icon = PlayerIcons.Queue,
                        contentDescription = "打开队列",
                        onClick = openQueue,
                    )
                }
                secondaryControls()
            },
        )
    }
}
