package com.local.mediaviewer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.ui.components.MediaGlyph
import com.local.mediaviewer.ui.components.MediaPrimaryButton
import com.local.mediaviewer.ui.components.MediaSecondaryButton
import com.local.mediaviewer.ui.components.WarmPaperCard
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun ConnectionStatusCard(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WarmPaperCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        when (state) {
            HomeUiState.Connecting -> ConnectingContent()

            is HomeUiState.Connected -> ConnectedContent(state.ipv4)

            is HomeUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ConnectingContent() {
    Row(
        modifier = Modifier.padding(MediaTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(MediaTheme.spacing.sm))
        Text(
            text = "正在连接服务器",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ConnectedContent(ipv4: String) {
    Row(
        modifier = Modifier.padding(MediaTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
    ) {
        MediaGlyph(
            icon = MediaIcons.Connected,
            contentDescription = null,
            tint = MediaTheme.extendedColors.success,
        )
        Column(verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xxs)) {
            Text("已连接", style = MaterialTheme.typography.titleMedium)
            Text(
                text = ipv4,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(MediaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
        ) {
            MediaGlyph(
                icon = MediaIcons.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
        MediaPrimaryButton(label = "重试", onClick = onRetry)
        MediaSecondaryButton(label = "服务器设置", onClick = onOpenSettings)
    }
}
