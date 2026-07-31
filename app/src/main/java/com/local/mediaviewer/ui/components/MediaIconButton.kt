package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun MediaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
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
        tint = if (selected == true) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
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
        tint = if (selected == true) {
            MediaTheme.playerColors.active
        } else {
            MediaTheme.playerColors.control
        },
    )
}

@Composable
private fun SemanticIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean?,
    loading: Boolean,
    stateDescription: String?,
    tint: Color,
) {
    val effectiveStateDescription = stateDescription ?: if (loading) {
        "正在处理"
    } else {
        null
    }
    IconButton(
        onClick = onClick,
        enabled = enabled && !loading,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        }
    }
}
