package com.local.mediaviewer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    state: PlayerUiState,
    controller: PlaybackController,
    fullscreenController: FullscreenStateController,
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
    onResumeHintShown: () -> Unit = {},
    onVideoScaleModeChanged: (VideoScaleMode) -> Unit,
    onBack: () -> Unit,
) {
    val fullscreen by
        fullscreenController.isFullscreen.collectAsStateWithLifecycle()
    ResumeHintDismissEffect(
        resumedFromMs = state.resumedFromMs,
        onResumeHintShown = onResumeHintShown,
    )

    BackHandler {
        if (fullscreen) {
            fullscreenController.exit()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
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
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                VlcSurface(
                    controller = controller,
                    keepScreenOn =
                        state.status == PlaybackStatus.PLAYING,
                    modifier = Modifier.fillMaxSize(),
                )
                when (state.status) {
                    PlaybackStatus.OPENING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    PlaybackStatus.BUFFERING -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                        )
                    }

                    PlaybackStatus.ERROR -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(state.errorMessage.orEmpty())
                            Button(onClick = onRetry) { Text("重试") }
                            TextButton(onClick = onBack) { Text("返回") }
                        }
                    }

                    else -> Unit
                }
                if (fullscreen) {
                    VideoScaleMenu(
                        current = state.videoScaleMode,
                        onSelected =
                            onVideoScaleModeChanged,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing,
                            )
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(
                                    alpha = 0.55f,
                                ),
                                shape = CircleShape,
                            ),
                    )
                }
            }
            if (!fullscreen) {
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
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        VideoScaleMenu(
                            current = state.videoScaleMode,
                            onSelected =
                                onVideoScaleModeChanged,
                        )
                        IconButton(
                            onClick =
                                fullscreenController::enter,
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.Fullscreen,
                                contentDescription = "全屏",
                            )
                        }
                    }
                }
            }
        }
    }
}
