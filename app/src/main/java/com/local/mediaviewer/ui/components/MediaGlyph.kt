package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage

@Composable
fun MediaGlyph(
    icon: MediaIcon,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MediaIconImage(
                icon = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
    }
}
