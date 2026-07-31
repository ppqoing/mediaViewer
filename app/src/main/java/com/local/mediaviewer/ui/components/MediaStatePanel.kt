package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

enum class MediaStateKind {
    LOADING,
    EMPTY,
    OFFLINE,
    ERROR,
}

data class MediaAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun MediaStatePanel(
    kind: MediaStateKind,
    title: String,
    message: String? = null,
    primaryAction: MediaAction? = null,
    secondaryAction: MediaAction? = null,
    modifier: Modifier = Modifier,
) {
    val icon = when (kind) {
        MediaStateKind.LOADING -> null
        MediaStateKind.EMPTY -> MediaIcons.Empty
        MediaStateKind.OFFLINE -> MediaIcons.Offline
        MediaStateKind.ERROR -> MediaIcons.Error
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MediaTheme.spacing.xl)
            .semantics {
                if (kind == MediaStateKind.ERROR ||
                    kind == MediaStateKind.OFFLINE
                ) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
    ) {
        if (kind == MediaStateKind.LOADING) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            MediaGlyph(
                icon = requireNotNull(icon),
                contentDescription = null,
                tint = when (kind) {
                    MediaStateKind.OFFLINE -> MediaTheme.extendedColors.offline
                    MediaStateKind.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        primaryAction?.let {
            MediaPrimaryButton(label = it.label, onClick = it.onClick)
        }
        secondaryAction?.let {
            MediaSecondaryButton(label = it.label, onClick = it.onClick)
        }
    }
}
