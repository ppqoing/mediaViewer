package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

enum class MediaSnackbarKind { INFO, SUCCESS, ERROR }

data class MediaSnackbarVisuals(
    override val message: String,
    val kind: MediaSnackbarKind,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
fun MediaSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { snackbarData ->
        val visuals = snackbarData.visuals
        val typedVisuals = visuals as? MediaSnackbarVisuals
        val kind = typedVisuals?.kind ?: MediaSnackbarKind.INFO
        val (icon, tint) = snackbarGlyph(kind)
        Snackbar(
            action = visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = snackbarData::performAction) {
                        Text(label)
                    }
                }
            },
            dismissAction = if (visuals.withDismissAction) {
                {
                    TextButton(onClick = snackbarData::dismiss) {
                        Text("关闭")
                    }
                }
            } else {
                null
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Text(visuals.message)
            }
        }
    }
}

@Composable
private fun snackbarGlyph(kind: MediaSnackbarKind): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> =
    when (kind) {
        MediaSnackbarKind.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
        MediaSnackbarKind.SUCCESS -> MediaIcons.Connected to MediaTheme.extendedColors.success
        MediaSnackbarKind.ERROR -> MediaIcons.Error to MaterialTheme.colorScheme.error
    }
