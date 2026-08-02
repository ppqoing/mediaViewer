package com.local.mediaviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.icons.MediaIconImage

@Composable
fun AudioArtworkPlaceholder(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .size(160.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ),
            )
            .testTag("audio_artwork"),
        contentAlignment = Alignment.Center,
    ) {
        MediaIconImage(
            icon = MediaIcons.Audio,
            contentDescription = "音频封面",
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(64.dp),
        )
    }
}
