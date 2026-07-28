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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    state: PlayerUiState,
    engine: PlaybackEngine,
    fullscreenController: FullscreenStateController,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVideoScaleModeChanged: (VideoScaleMode) -> Unit,
    onBack: () -> Unit,
) {
    val fullscreen by
        fullscreenController.isFullscreen.collectAsStateWithLifecycle()

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
                    engine = engine,
                    keepScreenOn =
                        state.status == PlaybackStatus.PLAYING,
                    modifier = Modifier.fillMaxSize(),
                )
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
                    onSeek = onSeek,
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
