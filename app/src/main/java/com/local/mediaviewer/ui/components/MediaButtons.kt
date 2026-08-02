package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun MediaPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: MediaIcon? = null,
) {
    MediaButton(
        kind = MediaButtonKind.PRIMARY,
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
    )
}

@Composable
fun MediaSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: MediaIcon? = null,
) {
    MediaButton(
        kind = MediaButtonKind.SECONDARY,
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
    )
}

@Composable
fun MediaDestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: MediaIcon? = null,
) {
    MediaButton(
        kind = MediaButtonKind.DESTRUCTIVE,
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
    )
}

private enum class MediaButtonKind {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
}

@Composable
private fun MediaButton(
    kind: MediaButtonKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    icon: MediaIcon?,
) {
    val semanticsModifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = if (loading) {
            "$label，正在处理"
        } else {
            label
        }
        if (loading) {
            stateDescription = "正在处理"
        }
    }
    val buttonModifier = modifier
        .heightIn(min = MediaTheme.sizing.minimumTouchTarget)
        .then(semanticsModifier)
    val effectiveEnabled = enabled && !loading

    when (kind) {
        MediaButtonKind.PRIMARY -> Button(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = buttonModifier,
        ) {
            MediaButtonContent(label, loading, icon)
        }

        MediaButtonKind.SECONDARY -> OutlinedButton(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = buttonModifier,
        ) {
            MediaButtonContent(label, loading, icon)
        }

        MediaButtonKind.DESTRUCTIVE -> Button(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = buttonModifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(
                    alpha = 0.38f,
                ),
                disabledContentColor = MaterialTheme.colorScheme.onError.copy(
                    alpha = 0.38f,
                ),
            ),
        ) {
            MediaButtonContent(label, loading, icon)
        }
    }
}

@Composable
private fun MediaButtonContent(
    label: String,
    loading: Boolean,
    icon: MediaIcon?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(MediaTheme.spacing.xs))
        } else {
            icon?.let {
                MediaIconImage(
                    icon = it,
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MediaTheme.spacing.xs))
            }
        }
        Text(text = label)
    }
}
