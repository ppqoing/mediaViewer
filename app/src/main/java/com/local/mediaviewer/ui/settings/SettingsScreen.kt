package com.local.mediaviewer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.settings.SettingsBackDecision
import com.local.mediaviewer.settings.SettingsUiState
import com.local.mediaviewer.settings.VideoControlsAutoHide
import com.local.mediaviewer.ui.components.MediaConfirmDialog
import com.local.mediaviewer.ui.components.MediaPrimaryButton
import com.local.mediaviewer.ui.components.MediaScreenScaffold
import com.local.mediaviewer.ui.components.MediaSecondaryButton
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInputChanged: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDefaultImageModeChanged: (ImageReaderMode) -> Unit,
    onVideoControlsAutoHideChanged: (
        VideoControlsAutoHide,
    ) -> Unit = {},
    onBack: () -> Unit,
    onBackRequest: () -> SettingsBackDecision = {
        SettingsBackDecision.LEAVE
    },
    onDiscardConfirmed: () -> Unit = onBack,
) {
    var showDiscardDialog by rememberSaveable {
        mutableStateOf(false)
    }
    val requestLeave = {
        when (onBackRequest()) {
            SettingsBackDecision.LEAVE -> onBack()
            SettingsBackDecision.CONFIRM_DISCARD -> {
                showDiscardDialog = true
            }
        }
    }
    val urlFieldState = when {
        state.isTesting -> MediaUrlFieldState.TESTING
        state.errorMessage != null -> MediaUrlFieldState.ERROR
        state.selectedIpv4 != null -> MediaUrlFieldState.SUCCESS
        else -> MediaUrlFieldState.IDLE
    }

    BackHandler(onBack = requestLeave)

    MediaScreenScaffold(
        title = "服务器设置",
        onBack = requestLeave,
    ) { scaffoldPadding ->
        val layoutDirection = LocalLayoutDirection.current
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val pageGutter = if (maxWidth >= 600.dp) {
                MediaTheme.spacing.widePageGutter
            } else {
                MediaTheme.spacing.pageGutter
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .testTag("settings_list"),
                contentPadding = PaddingValues(
                    start =
                        scaffoldPadding.calculateStartPadding(
                            layoutDirection,
                        ) + pageGutter,
                    top =
                        scaffoldPadding.calculateTopPadding() +
                            MediaTheme.spacing.md,
                    end =
                        scaffoldPadding.calculateEndPadding(
                            layoutDirection,
                        ) + pageGutter,
                    bottom =
                        scaffoldPadding.calculateBottomPadding() +
                            MediaTheme.spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    MediaTheme.spacing.lg,
                ),
            ) {
                item {
                    SettingsSection(
                        title = "服务器连接",
                        description =
                            "测试成功后保存服务器地址",
                    ) {
                        MediaUrlField(
                            value = state.input,
                            onValueChange = onInputChanged,
                            state = urlFieldState,
                            selectedIpv4 = state.selectedIpv4,
                            errorMessage = state.errorMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_url"),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "settings_secondary_action",
                                )
                                .semantics {
                                    if (state.isTesting) {
                                        disabled()
                                    }
                                },
                        ) {
                            MediaSecondaryButton(
                                label = "测试连接",
                                onClick = onTest,
                                enabled = !state.isTesting,
                                loading = state.isTesting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("test_connection"),
                            )
                        }
                        state.saveError?.let { message ->
                            Text(
                                text = message,
                                color =
                                    MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .testTag(
                                        "settings_save_error",
                                    )
                                    .semantics {
                                        liveRegion =
                                            LiveRegionMode.Polite
                                    },
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "settings_primary_action",
                                )
                                .semantics {
                                    if (
                                        !state.canSave ||
                                        state.isSaving
                                    ) {
                                        disabled()
                                    }
                                },
                        ) {
                            MediaPrimaryButton(
                                label = "保存",
                                onClick = onSave,
                                enabled = state.canSave,
                                loading = state.isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_server"),
                            )
                        }
                    }
                }
                item {
                    SettingsSection(
                        title = "视频播放",
                        description = "选择上下功能区自动隐藏时长",
                    ) {
                        SettingsChoiceRow(
                            title = "3 秒",
                            description = "3 秒无操作后自动隐藏",
                            selected =
                                state.videoControlsAutoHide ==
                                    VideoControlsAutoHide.THREE_SECONDS,
                            onClick = {
                                onVideoControlsAutoHideChanged(
                                    VideoControlsAutoHide.THREE_SECONDS,
                                )
                            },
                            enabled =
                                !state.isSavingVideoControlsAutoHide,
                            modifier = Modifier.testTag(
                                "video_controls_auto_hide_3",
                            ),
                        )
                        SettingsChoiceRow(
                            title = "5 秒",
                            description = "5 秒无操作后自动隐藏",
                            selected =
                                state.videoControlsAutoHide ==
                                    VideoControlsAutoHide.FIVE_SECONDS,
                            onClick = {
                                onVideoControlsAutoHideChanged(
                                    VideoControlsAutoHide.FIVE_SECONDS,
                                )
                            },
                            enabled =
                                !state.isSavingVideoControlsAutoHide,
                            modifier = Modifier.testTag(
                                "video_controls_auto_hide_5",
                            ),
                        )
                        SettingsChoiceRow(
                            title = "10 秒",
                            description = "10 秒无操作后自动隐藏",
                            selected =
                                state.videoControlsAutoHide ==
                                    VideoControlsAutoHide.TEN_SECONDS,
                            onClick = {
                                onVideoControlsAutoHideChanged(
                                    VideoControlsAutoHide.TEN_SECONDS,
                                )
                            },
                            enabled =
                                !state.isSavingVideoControlsAutoHide,
                            modifier = Modifier.testTag(
                                "video_controls_auto_hide_10",
                            ),
                        )
                        SettingsChoiceRow(
                            title = "15 秒",
                            description = "15 秒无操作后自动隐藏",
                            selected =
                                state.videoControlsAutoHide ==
                                    VideoControlsAutoHide.FIFTEEN_SECONDS,
                            onClick = {
                                onVideoControlsAutoHideChanged(
                                    VideoControlsAutoHide.FIFTEEN_SECONDS,
                                )
                            },
                            enabled =
                                !state.isSavingVideoControlsAutoHide,
                            modifier = Modifier.testTag(
                                "video_controls_auto_hide_15",
                            ),
                        )
                        SettingsChoiceRow(
                            title = "不隐藏",
                            description = "保持功能区显示",
                            selected =
                                state.videoControlsAutoHide ==
                                    VideoControlsAutoHide.NEVER,
                            onClick = {
                                onVideoControlsAutoHideChanged(
                                    VideoControlsAutoHide.NEVER,
                                )
                            },
                            enabled =
                                !state.isSavingVideoControlsAutoHide,
                            modifier = Modifier.testTag(
                                "video_controls_auto_hide_never",
                            ),
                        )
                        state.videoControlsAutoHideError?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                }
                item {
                    SettingsSection(
                        title = "图片阅读",
                        description = "选择默认的图片阅读方式",
                    ) {
                        SettingsChoiceRow(
                            title = "条漫",
                            description = "纵向连续阅读图片",
                            selected =
                                state.defaultImageMode ==
                                    ImageReaderMode.COMIC,
                            onClick = {
                                onDefaultImageModeChanged(
                                    ImageReaderMode.COMIC,
                                )
                            },
                            enabled =
                                !state.isSavingImageMode,
                            modifier = Modifier.testTag(
                                "default_reader_comic",
                            ),
                        )
                        SettingsChoiceRow(
                            title = "单图",
                            description = "逐张查看图片",
                            selected =
                                state.defaultImageMode ==
                                    ImageReaderMode.SINGLE,
                            onClick = {
                                onDefaultImageModeChanged(
                                    ImageReaderMode.SINGLE,
                                )
                            },
                            enabled =
                                !state.isSavingImageMode,
                            modifier = Modifier.testTag(
                                "default_reader_single",
                            ),
                        )
                        state.imageModeError?.let { message ->
                            Text(
                                text = message,
                                color =
                                    MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        MediaConfirmDialog(
            title = "放弃未保存的服务器更改？",
            message = "当前服务器地址尚未保存，放弃后将丢失这些更改。",
            confirmLabel = "放弃更改",
            dismissLabel = "继续编辑",
            destructive = true,
            onConfirm = {
                showDiscardDialog = false
                onDiscardConfirmed()
            },
            onDismiss = {
                showDiscardDialog = false
            },
        )
    }
}
