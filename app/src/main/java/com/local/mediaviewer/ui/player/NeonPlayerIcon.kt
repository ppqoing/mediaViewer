package com.local.mediaviewer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.theme.MediaTheme

internal val NeonCyan = Color(0xFF48E7FF)
internal val NeonPurple = Color(0xFF9B6CFF)

internal data class NeonPlayerIconVisualState(
    val showActiveAccent: Boolean,
    val showDisabledMark: Boolean,
)

internal fun neonPlayerIconVisualState(
    active: Boolean,
    enabled: Boolean,
): NeonPlayerIconVisualState = NeonPlayerIconVisualState(
    showActiveAccent = active && enabled,
    showDisabledMark = !enabled,
)

@Composable
fun NeonPlayerIcon(
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.size(24.dp),
) {
    val visualState = neonPlayerIconVisualState(active, enabled)
    val colors = MediaTheme.playerColors
    val foreground = when {
        !enabled -> colors.disabled
        active -> colors.active
        else -> colors.control
    }
    Box(modifier = modifier) {
        if (visualState.showActiveAccent) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.accent.copy(alpha = 0.55f),
                modifier = Modifier.matchParentSize().offset(1.dp, 1.dp),
            )
        }
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.matchParentSize(),
        )
        if (visualState.showDisabledMark) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawLine(
                    color = colors.disabled,
                    start = Offset(size.width * 0.18f, size.height * 0.18f),
                    end = Offset(size.width * 0.82f, size.height * 0.82f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
