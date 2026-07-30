package com.local.mediaviewer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.player.VideoInteractionReducer
import com.local.mediaviewer.player.VideoInteractionState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    state: PlayerUiState,
    controller: PlaybackController,
    fullscreenController: FullscreenStateController,
    preferences: PlayerPreferencesRepository,
    volumeController: PlayerVolumeController,
    brightnessController: PlayerBrightnessController,
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
    onResumeHintShown: () -> Unit = {},
    onVideoScaleModeChanged: (VideoScaleMode) -> Unit,
    onBack: () -> Unit,
) {
    val fullscreen by fullscreenController.isFullscreen.collectAsStateWithLifecycle()
    val hasShownVideoGestures by preferences.hasShownVideoGestures
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val volumeState by volumeController.state.collectAsStateWithLifecycle()
    var interaction by remember { mutableStateOf(VideoInteractionState()) }
    var gestureHintDismissed by remember { mutableStateOf(false) }
    var volumeExpanded by remember { mutableStateOf(false) }

    fun revealControls() {
        interaction = VideoInteractionReducer.revealControls(interaction)
    }

    fun setVolumeExpanded(expanded: Boolean) {
        volumeExpanded = expanded
        interaction = interaction.copy(menuExpanded = expanded)
        if (expanded) {
            volumeController.refresh()
            revealControls()
        }
    }

    LaunchedEffect(
        state.status,
        interaction.controlsVisible,
        interaction.autoHideEpoch,
        interaction.menuExpanded,
        interaction.scrubbing,
        interaction.feedback,
    ) {
        if (VideoInteractionReducer.canAutoHide(state.status, interaction)) {
            delay(3_000)
            interaction = interaction.copy(controlsVisible = false)
        }
    }
    LaunchedEffect(interaction.feedback) {
        if (interaction.feedback != null) {
            delay(800)
            interaction = interaction.copy(feedback = null)
        }
    }
    ResumeHintDismissEffect(
        resumedFromMs = state.resumedFromMs,
        onResumeHintShown = onResumeHintShown,
    )

    BackHandler {
        if (fullscreen) fullscreenController.exit() else onBack()
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(state.name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                VlcSurface(
                    controller = controller,
                    keepScreenOn = state.status == PlaybackStatus.PLAYING,
                    modifier = Modifier.fillMaxSize(),
                )
                when (state.status) {
                    PlaybackStatus.OPENING -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    PlaybackStatus.BUFFERING -> CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("video_buffering_spinner"),
                    )

                    PlaybackStatus.ERROR -> ErrorPlayerContent(
                        message = state.errorMessage.orEmpty(),
                        onRetry = onRetry,
                        onBack = onBack,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> Unit
                }
                if (fullscreen) {
                    VideoGestureLayer(
                        enabled = !interaction.controlsLocked,
                        durationMs = state.durationMs,
                        positionMs = state.positionMs,
                        volumeController = volumeController,
                        brightnessController = brightnessController,
                        onSingleTap = {
                            interaction = VideoInteractionReducer.toggleControls(interaction)
                        },
                        onSeekBack = {
                            revealControls()
                            onSeekBack()
                        },
                        onSeekForward = {
                            revealControls()
                            onSeekForward()
                        },
                        onBeginScrub = {
                            revealControls()
                            interaction = interaction.copy(scrubbing = true)
                            onBeginScrub()
                        },
                        onPreviewScrub = onPreviewScrub,
                        onCommitScrub = {
                            interaction = interaction.copy(scrubbing = false)
                            onCommitScrub()
                        },
                        onFeedback = { feedback ->
                            revealControls()
                            interaction = interaction.copy(feedback = feedback)
                        },
                    )
                    if (interaction.controlsVisible || interaction.controlsLocked) {
                        VideoControlsOverlay(
                            state = state,
                            locked = interaction.controlsLocked,
                            onLock = { interaction = VideoInteractionReducer.lock(interaction) },
                            onUnlock = { interaction = VideoInteractionReducer.unlock(interaction) },
                            onPlay = { revealControls(); onPlay() },
                            onPause = { revealControls(); onPause() },
                            onReplay = { revealControls(); onReplay() },
                            onSeekBack = { revealControls(); onSeekBack() },
                            onSeekForward = { revealControls(); onSeekForward() },
                            onBeginScrub = {
                                revealControls()
                                interaction = interaction.copy(scrubbing = true)
                                onBeginScrub()
                            },
                            onPreviewScrub = onPreviewScrub,
                            onCommitScrub = {
                                interaction = interaction.copy(scrubbing = false)
                                onCommitScrub()
                            },
                            onPrevious = { revealControls(); onPrevious() },
                            onNext = { revealControls(); onNext() },
                            onSpeedChanged = { speed -> revealControls(); onSpeedChanged(speed) },
                            onVideoScaleModeChanged = {
                                revealControls()
                                onVideoScaleModeChanged(it)
                            },
                            volumeState = volumeState,
                            volumeExpanded = volumeExpanded,
                            onVolumeExpandedChanged = ::setVolumeExpanded,
                            onVolumeRefresh = volumeController::refresh,
                            onToggleMute = volumeController::toggleMute,
                            onVolumeChanged = volumeController::setFraction,
                            onMenuExpandedChanged = { expanded ->
                                revealControls()
                                interaction = interaction.copy(menuExpanded = expanded)
                            },
                            onBack = fullscreenController::exit,
                        )
                    }
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
                    playbackMode = playbackMode,
                    onPlaybackModeChanged = onPlaybackModeChanged,
                    onOpenQueue = onOpenQueue,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlaybackVolumeControl(
                            state = volumeState,
                            expanded = volumeExpanded,
                            onExpandedChanged = ::setVolumeExpanded,
                            onRefresh = volumeController::refresh,
                            onToggleMute = volumeController::toggleMute,
                            onVolumeChanged = volumeController::setFraction,
                        )
                        VideoScaleMenu(state.videoScaleMode, onVideoScaleModeChanged)
                        IconButton(onClick = fullscreenController::enter) {
                            Icon(Icons.Default.Fullscreen, "全屏")
                        }
                    }
                }
            }
        }
    }

    if (fullscreen && !hasShownVideoGestures && !gestureHintDismissed) {
        AlertDialog(
            onDismissRequest = fullscreenController::exit,
            title = { Text("视频手势") },
            text = {
                Column {
                    Text("左右双击：快退/快进 10 秒")
                    Text("横向滑动：调整进度")
                    Text("左侧上下滑：亮度")
                    Text("右侧上下滑：音量")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        gestureHintDismissed = true
                        scope.launch { preferences.markVideoGesturesShown() }
                    },
                ) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun ErrorPlayerContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message)
        Button(onClick = onRetry) { Text("重试") }
        TextButton(onClick = onBack) { Text("返回") }
    }
}
