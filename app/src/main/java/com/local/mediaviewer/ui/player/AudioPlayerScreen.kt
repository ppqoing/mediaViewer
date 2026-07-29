package com.local.mediaviewer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus

@OptIn(ExperimentalMaterial3Api::class)
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
    onRetry: () -> Unit,
    volumeController: PlayerVolumeController,
    onResumeHintShown: () -> Unit = {},
    onBack: () -> Unit,
) {
    val volumeState by volumeController.state.collectAsStateWithLifecycle()
    var volumeExpanded by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    ResumeHintDismissEffect(
        resumedFromMs = state.resumedFromMs,
        onResumeHintShown = onResumeHintShown,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = "音频",
                modifier = Modifier.size(96.dp),
            )
            if (state.status == PlaybackStatus.OPENING) {
                CircularProgressIndicator()
            }
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
            ) {
                PlaybackVolumeControl(
                    state = volumeState,
                    expanded = volumeExpanded,
                    onExpandedChanged = { expanded ->
                        volumeExpanded = expanded
                        if (expanded) volumeController.refresh()
                    },
                    onToggleMute = volumeController::toggleMute,
                    onVolumeChanged = volumeController::setFraction,
                )
            }
            if (state.status == PlaybackStatus.BUFFERING) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            if (state.status == PlaybackStatus.ERROR) {
                Text(state.errorMessage.orEmpty())
                Button(onClick = onRetry) { Text("重试") }
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
    }
}
