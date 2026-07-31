package com.local.mediaviewer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.ui.components.MediaScreenScaffold
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun AudioPlayerScreen(
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
    onRetry: () -> Unit,
    volumeController: PlayerVolumeController,
    onResumeHintShown: () -> Unit = {},
    onBack: () -> Unit,
) {
    val volumeState by volumeController.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var volumeExpanded by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    ResumeHintDismissEffect(
        resumedFromMs = state.resumedFromMs,
        onResumeHintShown = onResumeHintShown,
    )

    MediaScreenScaffold(
        title = "音频播放",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(
                    horizontal = MediaTheme.spacing.md,
                    vertical = MediaTheme.spacing.sm,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xs),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AudioArtworkPlaceholder()
                when (state.status) {
                    PlaybackStatus.OPENING -> PlayerStateOverlay(
                        kind = PlayerOverlayKind.OPENING,
                    )

                    PlaybackStatus.BUFFERING -> Box(
                        modifier = Modifier.testTag("audio_buffering_spinner"),
                    ) {
                        PlayerStateOverlay(
                            kind = PlayerOverlayKind.BUFFERING,
                        )
                    }

                    PlaybackStatus.ERROR -> PlayerStateOverlay(
                        kind = PlayerOverlayKind.ERROR,
                        message = state.errorMessage,
                        onRetry = onRetry,
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    else -> Unit
                }
            }
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            Text(
                text = "音频文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlayerControls(
                state = state,
                onPlay = onPlay,
                onPause = onPause,
                onReplay = onReplay,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onBeginScrub = onBeginScrub,
                onPreviewScrub = onPreviewScrub,
                onCommitScrub = onCommitScrub,
                onPrevious = onPrevious,
                onNext = onNext,
                onSpeedChanged = onSpeedChanged,
                playbackMode = playbackMode,
                onPlaybackModeChanged = onPlaybackModeChanged,
                onOpenQueue = onOpenQueue,
            ) {
                PlaybackVolumeControl(
                    state = volumeState,
                    expanded = volumeExpanded,
                    onExpandedChanged = { expanded ->
                        volumeExpanded = expanded
                        if (expanded) volumeController.refresh()
                    },
                    onRefresh = volumeController::refresh,
                    onToggleMute = volumeController::toggleMute,
                    onVolumeChanged = volumeController::setFraction,
                )
            }
        }
    }
}
