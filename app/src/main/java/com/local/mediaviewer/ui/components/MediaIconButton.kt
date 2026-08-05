package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.theme.MediaTheme

internal data class MediaIconButtonVisualState(
    val isEnabled: Boolean,
    val iconAlpha: Float,
)

internal fun mediaIconButtonVisualState(
    enabled: Boolean,
    loading: Boolean,
): MediaIconButtonVisualState = MediaIconButtonVisualState(
    isEnabled = enabled && !loading,
    iconAlpha = if (enabled) 1f else 0.38f,
)

@Composable
fun MediaIconButton(
    icon: MediaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
    iconSize: Dp = 28.dp,
) {
    SemanticIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        loading = loading,
        stateDescription = stateDescription,
        iconSize = iconSize,
        tint = if (selected == true) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
fun PlayerIconButton(
    icon: MediaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
    iconSize: Dp = 32.dp,
) {
    SemanticIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        loading = loading,
        stateDescription = stateDescription,
        iconSize = iconSize,
        tint = if (selected == true) {
            MediaTheme.playerColors.active
        } else {
            MediaTheme.playerColors.control
        },
    )
}

@Composable
private fun SemanticIconButton(
    icon: MediaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean?,
    loading: Boolean,
    stateDescription: String?,
    iconSize: Dp,
    tint: Color,
) {
    val visualState = mediaIconButtonVisualState(enabled, loading)
    val effectiveStateDescription = stateDescription ?: if (loading) {
        "正在处理"
    } else {
        null
    }
    IconButton(
        onClick = onClick,
        enabled = visualState.isEnabled,
        modifier = modifier
            .sizeIn(
                minWidth = MediaTheme.sizing.minimumTouchTarget,
                minHeight = MediaTheme.sizing.minimumTouchTarget,
            )
            .semantics {
                this.contentDescription = contentDescription
                selected?.let { this.selected = it }
                effectiveStateDescription?.let {
                    this.stateDescription = it
                }
            },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = tint,
                strokeWidth = 2.dp,
            )
        } else {
            MediaIconImage(
                icon = icon,
                contentDescription = null,
                tint = tint.copy(alpha = tint.alpha * visualState.iconAlpha),
                modifier = Modifier.requiredSize(iconSize),
            )
        }
    }
}
