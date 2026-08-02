package com.local.mediaviewer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
import com.local.mediaviewer.settings.VideoControlsAutoHide
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import com.local.mediaviewer.ui.components.MediaTopAppBar
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import com.local.mediaviewer.ui.theme.MediaTheme
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
    backgroundPlaybackEnabled: Boolean = false,
    onBackgroundPlaybackChanged: (Boolean) -> Unit = {},
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
    val videoControlsAutoHide by preferences.videoControlsAutoHide
        .collectAsStateWithLifecycle(
            initialValue = VideoControlsAutoHide.THREE_SECONDS,
        )
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
        videoControlsAutoHide,
    ) {
        val autoHideDelayMs = VideoInteractionReducer.autoHideDelayMs(
            playbackStatus = state.status,
            interaction = interaction,
            preference = videoControlsAutoHide,
        )
        if (autoHideDelayMs != null) {
            delay(autoHideDelayMs)
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
        val playerColors = MediaTheme.playerColors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(playerColors.canvas)
                .testTag("video_player_canvas"),
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
                VideoGestureLayer(
                    enabled = !interaction.controlsLocked,
                    extendedGesturesEnabled = fullscreen,
                    durationMs = state.durationMs,
                    positionMs = state.positionMs,
                    volumeController = volumeController,
                    brightnessController = brightnessController,
                    onSingleTap = {
                        interaction =
                            VideoInteractionReducer.toggleControls(
                                interaction,
                            )
                    },
                    onDoubleTap = {
                        revealControls()
                        when (state.status) {
                            PlaybackStatus.PLAYING,
                            PlaybackStatus.BUFFERING,
                            -> onPause()

                            PlaybackStatus.ENDED -> onReplay()
                            else -> onPlay()
                        }
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
                if (fullscreen) {
                    if (
                        interaction.controlsVisible ||
                        interaction.controlsLocked
                    ) {
                        VideoControlsOverlay(
                            state = state,
                            backgroundPlaybackEnabled =
                                backgroundPlaybackEnabled,
                            onBackgroundPlaybackChanged = {
                                revealControls()
                                onBackgroundPlaybackChanged(it)
                            },
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
                } else if (interaction.controlsVisible) {
                    MediaTopAppBar(
                        title = state.name,
                        onBack = onBack,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        playerColors.topScrimStart,
                                        playerColors.topScrimEnd,
                                    ),
                                ),
                            )
                            .testTag("video_top_controls_ordinary"),
                        containerColor = Color.Transparent,
                        contentColor = playerColors.control,
                        windowInsets = safeDrawingInsets.only(
                            WindowInsetsSides.Top +
                                WindowInsetsSides.Horizontal,
                        ),
                        actions = {
                            OrdinaryPlaybackSettingsMenu(
                                state = state,
                                playbackMode = playbackMode,
                                backgroundPlaybackEnabled =
                                    backgroundPlaybackEnabled,
                                onBackgroundPlaybackChanged = {
                                    revealControls()
                                    onBackgroundPlaybackChanged(it)
                                },
                                onSpeedChanged = {
                                    revealControls()
                                    onSpeedChanged(it)
                                },
                                onPlaybackModeChanged = {
                                    revealControls()
                                    onPlaybackModeChanged(it)
                                },
                                onVideoScaleModeChanged = {
                                    revealControls()
                                    onVideoScaleModeChanged(it)
                                },
                                onExpandedChanged = { expanded ->
                                    revealControls()
                                    interaction = interaction.copy(
                                        menuExpanded = expanded,
                                    )
                                },
                            )
                        },
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                safeDrawingInsets.only(
                                    WindowInsetsSides.Bottom +
                                        WindowInsetsSides.Horizontal,
                                ),
                            )
                            .testTag("video_bottom_controls_ordinary")
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        playerColors.bottomScrimStart,
                                        playerColors.bottomScrimEnd,
                                    ),
                                ),
                            ),
                    ) {
                        PlayerControls(
                            state = state,
                            onPlay = { revealControls(); onPlay() },
                            onPause = { revealControls(); onPause() },
                            onReplay = { revealControls(); onReplay() },
                            onSeekBack = { revealControls(); onSeekBack() },
                            onSeekForward = {
                                revealControls()
                                onSeekForward()
                            },
                            onBeginScrub = {
                                revealControls()
                                interaction = interaction.copy(
                                    scrubbing = true,
                                )
                                onBeginScrub()
                            },
                            onPreviewScrub = onPreviewScrub,
                            onCommitScrub = {
                                interaction = interaction.copy(
                                    scrubbing = false,
                                )
                                onCommitScrub()
                            },
                            onPrevious = {
                                revealControls()
                                onPrevious()
                            },
                            onNext = { revealControls(); onNext() },
                            onSpeedChanged = onSpeedChanged,
                            playbackMode = playbackMode,
                            onPlaybackModeChanged =
                                onPlaybackModeChanged,
                            showLowFrequencyControls = false,
                            primaryActionTag = "video_primary_action",
                            leadingUtilityControls = {
                                onOpenQueue?.let { openQueue ->
                                    MediaIconButton(
                                        icon = PlayerIcons.Queue,
                                        contentDescription = "打开队列",
                                        onClick = {
                                            revealControls()
                                            openQueue()
                                        },
                                        modifier = Modifier.testTag(
                                            "queue_entry_ordinary",
                                        ),
                                    )
                                }
                                PlaybackVolumeControl(
                                    state = volumeState,
                                    expanded = volumeExpanded,
                                    onExpandedChanged = ::setVolumeExpanded,
                                    onRefresh = volumeController::refresh,
                                    onToggleMute =
                                        volumeController::toggleMute,
                                    onVolumeChanged =
                                        volumeController::setFraction,
                                )
                            },
                            secondaryControls = {
                                MediaIconButton(
                                    icon = PlayerIcons.FullscreenEnter,
                                    contentDescription = "全屏",
                                    onClick = {
                                        revealControls()
                                        fullscreenController.enter()
                                    },
                                )
                            },
                        )
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
                    Text("双击视频：播放或暂停")
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
    backgroundPlaybackEnabled: Boolean,
    onBackgroundPlaybackChanged: (Boolean) -> Unit,
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
            contentDescription = "更多播放选项",
            onClick = {
                page = OrdinarySettingsPage.ROOT
                onExpandedChanged(true)
            },
        )

        DropdownMenu(
            expanded = page == OrdinarySettingsPage.ROOT,
            onDismissRequest = ::closeMenu,
        ) {
            DropdownMenuItem(
                text = { Text("后台播放") },
                trailingIcon = {
                    Checkbox(
                        checked = backgroundPlaybackEnabled,
                        onCheckedChange = null,
                    )
                },
                onClick = {
                    onBackgroundPlaybackChanged(
                        !backgroundPlaybackEnabled,
                    )
                },
                modifier = Modifier
                    .testTag("video_background_playback")
                    .semantics {
                        role = Role.Checkbox
                        toggleableState = if (backgroundPlaybackEnabled) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        }
                        stateDescription = if (backgroundPlaybackEnabled) {
                            "已启用"
                        } else {
                            "未启用"
                        }
                    },
            )
            DropdownMenuItem(
                text = { Text("播放速度") },
                onClick = { page = OrdinarySettingsPage.SPEED },
            )
            if (playbackMode != null) {
                DropdownMenuItem(
                    text = { Text("播放模式") },
                    onClick = { page = OrdinarySettingsPage.MODE },
                )
            }
            DropdownMenuItem(
                text = { Text("画面比例") },
                onClick = { page = OrdinarySettingsPage.SCALE },
            )
        }

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
