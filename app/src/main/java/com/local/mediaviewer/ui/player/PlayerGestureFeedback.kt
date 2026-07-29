package com.local.mediaviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerGestureFeedback

@Composable
internal fun PlayerGestureFeedbackOverlay(feedback: PlayerGestureFeedback?) {
    val value = feedback ?: return
    val description = when (value) {
        is PlayerGestureFeedback.Seek -> "定位 ${formatPlaybackTime(value.targetMs)}"
        is PlayerGestureFeedback.Brightness -> "亮度 ${value.percent}%"
        is PlayerGestureFeedback.Volume -> "音量 ${value.percent}%"
    }
    Row(
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = description }
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (value) {
            is PlayerGestureFeedback.Seek -> Icon(
                imageVector = if (value.deltaMs < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                contentDescription = null,
                tint = Color.White,
            )

            is PlayerGestureFeedback.Brightness -> Icon(
                imageVector = Icons.Filled.BrightnessMedium,
                contentDescription = null,
                tint = Color.White,
            )

            is PlayerGestureFeedback.Volume -> Icon(
                imageVector = if (value.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Text(
            text = when (value) {
                is PlayerGestureFeedback.Seek -> formatPlaybackTime(value.targetMs)
                is PlayerGestureFeedback.Brightness -> "${value.percent}%"
                is PlayerGestureFeedback.Volume -> "${value.percent}%"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
