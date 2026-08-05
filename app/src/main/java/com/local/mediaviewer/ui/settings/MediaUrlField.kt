package com.local.mediaviewer.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.theme.MediaTheme

enum class MediaUrlFieldState { IDLE, TESTING, SUCCESS, ERROR }

@Composable
fun MediaUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    state: MediaUrlFieldState,
    selectedIpv4: String? = null,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val isTesting = state == MediaUrlFieldState.TESTING
    val isError = state == MediaUrlFieldState.ERROR
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = !isTesting,
        singleLine = true,
        isError = isError,
        label = { Text("服务器 URL") },
        leadingIcon = {
            MediaIconImage(
                icon = MediaIcons.NetworkShare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.requiredSize(24.dp),
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            when (state) {
                MediaUrlFieldState.SUCCESS -> MediaIconImage(
                    icon = MediaIcons.Connected,
                    contentDescription = null,
                    tint = MediaTheme.extendedColors.success,
                    modifier = Modifier.requiredSize(24.dp),
                )

                MediaUrlFieldState.ERROR -> MediaIconImage(
                    icon = MediaIcons.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.requiredSize(24.dp),
                )

                else -> Unit
            }
        },
        supportingText = {
            when (state) {
                MediaUrlFieldState.IDLE -> Text("示例：http://192.168.1.17:8080")
                MediaUrlFieldState.TESTING -> Text("正在测试连接")
                MediaUrlFieldState.SUCCESS -> selectedIpv4?.let { ipv4 ->
                    Text("将连接到 $ipv4")
                }

                MediaUrlFieldState.ERROR -> Text(
                    text = errorMessage ?: "无法连接服务器",
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        },
    )
}
