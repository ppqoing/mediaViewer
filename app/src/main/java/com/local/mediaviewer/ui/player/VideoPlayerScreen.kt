package com.local.mediaviewer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.local.mediaviewer.playback.PlaybackSpeeds
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import com.local.mediaviewer.ui.components.MediaTopAppBar
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaViewerTheme
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
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val fullscreen by fullscreenController.isFullscreen.collectAsStateWithLifecycle()
    val hasShownVideoGestures by preferences.hasShownVideoGestures
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val volumeState by volumeController.state.collectAsStateWithLifecycle()
    var interaction by remember { mutableStateOf(VideoInteractionState()) }
    var gestureHintDismissed by remember { mutableStateOf(false) }
    var volumeExpanded by remember { mutableStateOf(false) }

    val gestureHintVisible = fullscreen &&
        !hasShownVideoGestures &&
        !gestureHintDismissed

    fun dismissGestureHint() {
        gestureHintDismissed = true
        scope.launch { preferences.markVideoGesturesShown() }
    }

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
        when {
            gestureHintVisible -> dismissGestureHint()
            // 规格 §10/§8.6：锁定时返回不退出全屏；
            // 明确的解锁入口由锁定覆盖层常驻提供。
            interaction.controlsLocked -> Unit
            fullscreen && volumeExpanded -> setVolumeExpanded(false)
            fullscreen -> fullscreenController.exit()
            else -> onBack()
        }
    }

    MediaViewerTheme(darkTheme = true) {
        Scaffold(
            topBar = {
                if (!fullscreen) {
                    MediaTopAppBar(
                        title = state.name,
                        onBack = onBack,
                    )
                }
            },
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen) Modifier else Modifier.padding(padding)),
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
                    PlaybackStatus.OPENING -> PlayerStateOverlay(
                        kind = PlayerOverlayKind.OPENING,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    PlaybackStatus.BUFFERING -> Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 120.dp)
                            .testTag("video_buffering_spinner"),
                    ) {
                        PlayerStateOverlay(
                            kind = PlayerOverlayKind.BUFFERING,
                        )
                    }

                    PlaybackStatus.ERROR -> PlayerStateOverlay(
                        kind = PlayerOverlayKind.ERROR,
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
                        feedback = interaction.feedback,
                        onFeedback = { feedback ->
                            revealControls()
                            interaction = interaction.copy(feedback = feedback)
                        },
                    )
                    if (interaction.controlsVisible || interaction.controlsLocked) {
                        VideoControlsOverlay(
                            state = state,
                            locked = interaction.controlsLocked,
                            onLock = {
                                volumeExpanded = false
                                interaction = VideoInteractionReducer.lock(interaction)
                            },
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
                            onPlaybackModeChanged = { mode ->
                                revealControls()
                                onPlaybackModeChanged(mode)
                            },
                            onVideoScaleModeChanged = {
                                revealControls()
                                onVideoScaleModeChanged(it)
                            },
                            onOpenQueue = { onOpenQueue?.invoke() },
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
                            safeDrawingInsets = safeDrawingInsets,
                        )
                    }
                }
            }
            if (!fullscreen) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 600.dp
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
                        showLowFrequencyControls = !compact,
                    ) {
                        PlaybackVolumeControl(
                            state = volumeState,
                            expanded = volumeExpanded,
                            onExpandedChanged = ::setVolumeExpanded,
                            onRefresh = volumeController::refresh,
                            onToggleMute = volumeController::toggleMute,
                            onVolumeChanged = volumeController::setFraction,
                        )
                        if (compact) {
                            OrdinaryPlaybackSettingsMenu(
                                state = state,
                                playbackMode = playbackMode,
                                onSpeedChanged = onSpeedChanged,
                                onPlaybackModeChanged = onPlaybackModeChanged,
                                onVideoScaleModeChanged = onVideoScaleModeChanged,
                                onExpandedChanged = { expanded ->
                                    interaction = interaction.copy(
                                        menuExpanded = expanded,
                                    )
                                },
                            )
                        } else {
                            VideoScaleMenu(
                                current = state.videoScaleMode,
                                onSelected = onVideoScaleModeChanged,
                            )
                        }
                        onOpenQueue?.let { openQueue ->
                            MediaIconButton(
                                icon = MediaIcons.Queue,
                                contentDescription = "打开队列",
                                onClick = openQueue,
                                modifier = Modifier.testTag("queue_entry_ordinary"),
                            )
                        }
                        MediaIconButton(
                            icon = Icons.Default.Fullscreen,
                            contentDescription = "全屏",
                            onClick = fullscreenController::enter,
                        )
                    }
                }
            }
        }
    }

    if (gestureHintVisible) {
        AlertDialog(
            onDismissRequest = ::dismissGestureHint,
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
                TextButton(onClick = ::dismissGestureHint) { Text("知道了") }
            },
        )
    }
    }
}

private enum class OrdinarySettingsPage {
    ROOT,
    SPEED,
    MODE,
    SCALE,
}

@Composable
private fun OrdinaryPlaybackSettingsMenu(
    state: PlayerUiState,
    playbackMode: PlaybackMode?,
    onSpeedChanged: (Float) -> Unit,
    onPlaybackModeChanged: (PlaybackMode) -> Unit,
    onVideoScaleModeChanged: (VideoScaleMode) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
) {
    var page by remember { mutableStateOf<OrdinarySettingsPage?>(null) }

    fun closeMenu() {
        page = null
        onExpandedChanged(false)
    }

    Box {
        MediaIconButton(
            icon = MediaIcons.More,
            contentDescription = "更多播放设置",
            onClick = {
                page = OrdinarySettingsPage.ROOT
                onExpandedChanged(true)
            },
        )

        MediaOptionMenu(
            expanded = page == OrdinarySettingsPage.ROOT,
            options = buildList<MediaOption<OrdinarySettingsPage>> {
                add(
                    MediaOption(
                        key = OrdinarySettingsPage.SPEED,
                        label = "播放速度",
                        icon = PlayerIcons.Speed,
                    ),
                )
                if (playbackMode != null) {
                    add(
                        MediaOption(
                            key = OrdinarySettingsPage.MODE,
                            label = "播放模式",
                            icon = PlayerIcons.Sequential,
                        ),
                    )
                }
                add(
                    MediaOption(
                        key = OrdinarySettingsPage.SCALE,
                        label = "画面比例",
                        icon = PlayerIcons.Scale,
                    ),
                )
            },
            selectedKey = null,
            onSelect = { selected -> page = selected },
            onDismissRequest = ::closeMenu,
        )

        MediaOptionMenu(
            expanded = page == OrdinarySettingsPage.SPEED,
            options = PlaybackSpeeds.supported.map { speed ->
                MediaOption(
                    key = speed,
                    label = "${formatPlaybackSpeed(speed)} 倍",
                )
            },
            selectedKey = state.playbackSpeed,
            onSelect = { speed ->
                onSpeedChanged(speed)
                closeMenu()
            },
            onDismissRequest = ::closeMenu,
        )

        playbackMode?.let { currentMode ->
            MediaOptionMenu(
                expanded = page == OrdinarySettingsPage.MODE,
                options = PlaybackMode.entries.map { mode ->
                    MediaOption(
                        key = mode,
                        label = mode.label(),
                    )
                },
                selectedKey = currentMode,
                onSelect = { mode ->
                    onPlaybackModeChanged(mode)
                    closeMenu()
                },
                onDismissRequest = ::closeMenu,
            )
        }

        MediaOptionMenu(
            expanded = page == OrdinarySettingsPage.SCALE,
            options = VideoScaleMode.entries.map { mode ->
                MediaOption(
                    key = mode,
                    label = videoScaleLabel(mode),
                )
            },
            selectedKey = state.videoScaleMode,
            onSelect = { mode ->
                onVideoScaleModeChanged(mode)
                closeMenu()
            },
            onDismissRequest = ::closeMenu,
        )
    }
}
