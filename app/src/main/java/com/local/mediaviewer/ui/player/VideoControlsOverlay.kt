package com.local.mediaviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.ui.components.PlayerIconButton
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

private val CompactLandscapeHeightThreshold = 600.dp

@Composable
fun VideoControlsOverlay(
    state: PlayerUiState,
    backgroundPlaybackEnabled: Boolean,
    onBackgroundPlaybackChanged: (Boolean) -> Unit,
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
    onPlaybackModeChanged: (PlaybackMode) -> Unit,
    onVideoScaleModeChanged: (VideoScaleMode) -> Unit,
    onOpenQueue: () -> Unit,
    volumeState: VolumeState,
    volumeExpanded: Boolean,
    onVolumeExpandedChanged: (Boolean) -> Unit,
    onVolumeRefresh: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onMenuExpandedChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val playerColors = MediaTheme.playerColors
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_controls"),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_root"),
        ) {
            val compactLandscape = maxWidth > maxHeight &&
                maxHeight <= CompactLandscapeHeightThreshold
            if (locked) {
                PlayerIconButton(
                    icon = PlayerIcons.Unlock,
                    contentDescription = "解锁控制",
                    stateDescription = "控制已锁定",
                    onClick = onUnlock,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(
                            safeDrawingInsets.only(
                                WindowInsetsSides.Horizontal,
                            ),
                        )
                        .padding(16.dp)
                        .semantics {
                            toggleableState = ToggleableState.On
                        },
                )
                return@BoxWithConstraints
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                playerColors.topScrimStart,
                                playerColors.topScrimEnd,
                            ),
                        ),
                    )
                    .windowInsetsPadding(
                        safeDrawingInsets.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fullscreen_top_controls")
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIconButton(
                        icon = MediaIcons.Back,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                    Text(
                        text = state.name,
                        color = playerColors.control,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FullscreenPlaybackSettingsMenu(
                        backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                        onBackgroundPlaybackChanged =
                            onBackgroundPlaybackChanged,
                        onExpandedChanged = onMenuExpandedChanged,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        y = if (compactLandscape) (-64).dp else 0.dp,
                    )
                    .testTag("fullscreen_center_controls"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    icon = PlayerIcons.Back10,
                    contentDescription = "快退 10 秒",
                    onClick = onSeekBack,
                    enabled = state.isSeekable && state.durationMs > 0L,
                )
                PlaybackPrimaryActionButton(
                    status = state.status,
                    size = MediaTheme.sizing.fullscreenPrimaryButton,
                    iconSize = 40.dp,
                    onPlay = onPlay,
                    onPause = onPause,
                    onReplay = onReplay,
                    actionTestTag = "fullscreen_primary_action",
                    iconTestTag = "fullscreen_primary_icon",
                )
                PlayerIconButton(
                    icon = PlayerIcons.Forward10,
                    contentDescription = "快进 10 秒",
                    onClick = onSeekForward,
                    enabled = state.isSeekable && state.durationMs > 0L,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                playerColors.bottomScrimStart,
                                playerColors.bottomScrimEnd,
                            ),
                        ),
                    )
                    .windowInsetsPadding(
                        safeDrawingInsets.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fullscreen_bottom_controls")
                        .padding(
                            horizontal = 12.dp,
                            vertical = if (compactLandscape) 4.dp else 12.dp,
                        ),
                ) {
                PlaybackTimeline(
                    state = state,
                    onBeginScrub = onBeginScrub,
                    onPreviewScrub = onPreviewScrub,
                    onCommitScrub = onCommitScrub,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fullscreen_playback_configuration"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FullscreenConfigurationItem(
                        label = "倍速",
                        compact = compactLandscape,
                        modifier = Modifier.weight(1f),
                    ) {
                        PlaybackSpeedMenu(
                            current = state.playbackSpeed,
                            onSpeedChanged = onSpeedChanged,
                            onExpandedChanged = onMenuExpandedChanged,
                        )
                    }
                    FullscreenConfigurationItem(
                        label = "播放模式",
                        compact = compactLandscape,
                        modifier = Modifier.weight(1f),
                    ) {
                        PlaybackModeButton(
                            mode = state.playbackMode,
                            onModeChanged = onPlaybackModeChanged,
                        )
                    }
                    FullscreenConfigurationItem(
                        label = "画面比例",
                        compact = compactLandscape,
                        modifier = Modifier.weight(1f),
                    ) {
                        VideoScaleMenu(
                            current = state.videoScaleMode,
                            onSelected = onVideoScaleModeChanged,
                            onExpandedChanged = onMenuExpandedChanged,
                        )
                    }
                }
                PlayerUtilityRow(
                    startContent = {
                        PlayerIconButton(
                            icon = PlayerIcons.Previous,
                            contentDescription = "上一项",
                            onClick = onPrevious,
                            enabled = state.canSkipPrevious,
                        )
                        PlayerIconButton(
                            icon = PlayerIcons.Next,
                            contentDescription = "下一项",
                            onClick = onNext,
                            enabled = state.canSkipNext,
                        )
                        PlayerIconButton(
                            icon = PlayerIcons.Queue,
                            contentDescription = "打开播放队列",
                            onClick = onOpenQueue,
                            modifier = Modifier.testTag(
                                "queue_entry_fullscreen",
                            ),
                        )
                    },
                    endContent = {
                        PlaybackVolumeControl(
                            state = volumeState,
                            expanded = volumeExpanded,
                            onExpandedChanged = onVolumeExpandedChanged,
                            onRefresh = onVolumeRefresh,
                            onToggleMute = onToggleMute,
                            onVolumeChanged = onVolumeChanged,
                        )
                        PlayerIconButton(
                            icon = PlayerIcons.Lock,
                            contentDescription = "锁定控制",
                            stateDescription = "控制未锁定",
                            onClick = onLock,
                            modifier = Modifier.semantics {
                                toggleableState = ToggleableState.Off
                            },
                        )
                        PlayerIconButton(
                            icon = PlayerIcons.FullscreenExit,
                            contentDescription = "退出全屏",
                            onClick = onBack,
                        )
                    },
                )
                }
            }
        }
    }
}

@Composable
private fun FullscreenConfigurationItem(
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val labelContent: @Composable () -> Unit = {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MediaTheme.playerColors.control.copy(alpha = 0.78f),
        )
    }
    if (compact) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labelContent()
            content()
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            labelContent()
            content()
        }
    }
}

@Composable
private fun FullscreenPlaybackSettingsMenu(
    backgroundPlaybackEnabled: Boolean,
    onBackgroundPlaybackChanged: (Boolean) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    fun closeMenu() {
        expanded = false
        onExpandedChanged(false)
    }

    Box {
        PlayerIconButton(
            icon = MediaIcons.More,
            contentDescription = "更多播放设置",
            onClick = {
                expanded = true
                onExpandedChanged(true)
            },
            modifier = Modifier.testTag("fullscreen_options_menu"),
        )

        DropdownMenu(
            expanded = expanded,
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
        }
    }
}
