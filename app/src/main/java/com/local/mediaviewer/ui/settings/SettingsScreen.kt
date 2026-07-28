package com.local.mediaviewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.settings.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInputChanged: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDefaultImageModeChanged: (ImageReaderMode) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                label = { Text("服务器 URL") },
                supportingText = {
                    Text("示例：http://192.168.1.17:8080")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_url"),
            )
            Button(
                onClick = onTest,
                enabled = !state.isTesting,
                modifier = Modifier.testTag("test_connection"),
            ) {
                Text(if (state.isTesting) "正在测试…" else "测试连接")
            }
            state.resolvedIpv4s.forEach { ip ->
                Text(
                    if (ip == state.selectedIpv4) {
                        "已选择：$ip"
                    } else {
                        ip
                    },
                )
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.testTag("save_server"),
            ) {
                Text("保存")
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
}
