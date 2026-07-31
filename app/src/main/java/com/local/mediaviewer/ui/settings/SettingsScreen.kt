package com.local.mediaviewer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.settings.SettingsBackDecision
import com.local.mediaviewer.settings.SettingsUiState
import com.local.mediaviewer.ui.components.MediaConfirmDialog
import com.local.mediaviewer.ui.components.MediaPrimaryButton
import com.local.mediaviewer.ui.components.MediaSecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInputChanged: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDefaultImageModeChanged: (ImageReaderMode) -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器设置") },
                navigationIcon = {
                    IconButton(onClick = requestLeave) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            MediaSecondaryButton(
                label = "测试连接",
                onClick = onTest,
                enabled = !state.isTesting,
                loading = state.isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_connection"),
            )
            state.resolvedIpv4s.forEach { ip ->
                Text(
                    if (ip == state.selectedIpv4) {
                        "已选择：$ip"
                    } else {
                        ip
                    },
                )
            }
            MediaPrimaryButton(
                label = "保存",
                onClick = onSave,
                enabled = state.canSave,
                loading = state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_server"),
            )
            state.saveError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
            Text(
                text = "图片阅读",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected =
                        state.defaultImageMode ==
                            ImageReaderMode.COMIC,
                    onClick = {
                        onDefaultImageModeChanged(
                            ImageReaderMode.COMIC,
                        )
                    },
                    label = { Text("条漫") },
                    enabled = !state.isSavingImageMode,
                    modifier =
                        Modifier.testTag(
                            "default_reader_comic",
                        ),
                )
                FilterChip(
                    selected =
                        state.defaultImageMode ==
                            ImageReaderMode.SINGLE,
                    onClick = {
                        onDefaultImageModeChanged(
                            ImageReaderMode.SINGLE,
                        )
                    },
                    label = { Text("单图") },
                    enabled = !state.isSavingImageMode,
                    modifier =
                        Modifier.testTag(
                            "default_reader_single",
                        ),
                )
            }
            state.imageModeError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                )
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
