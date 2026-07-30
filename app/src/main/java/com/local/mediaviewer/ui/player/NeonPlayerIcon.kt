package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal val NeonCyan = Color(0xFF48E7FF)
internal val NeonPurple = Color(0xFF9B6CFF)

@Composable
fun NeonPlayerIcon(
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.size(24.dp),
) {
    val foreground = if (enabled) NeonCyan else NeonCyan.copy(alpha = 0.38f)
    Box(modifier = modifier) {
        if (active) {
            Icon(
                icon,
                contentDescription = null,
                tint = NeonPurple.copy(alpha = 0.55f),
                modifier = Modifier.matchParentSize().offset(1.dp, 1.dp),
            )
        }
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.matchParentSize(),
        )
    }
}
