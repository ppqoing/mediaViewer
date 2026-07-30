package com.local.mediaviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerGestureFeedback

@Composable
internal fun PlayerGestureFeedbackOverlay(feedback: PlayerGestureFeedback?) {
    val value = feedback ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        when (value) {
            is PlayerGestureFeedback.Seek -> Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics {
                        contentDescription = "定位 ${formatPlaybackTime(value.targetMs)}"
                    }
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeonPlayerIcon(
                    icon = if (value.deltaMs < 0) PlayerIcons.Back10 else PlayerIcons.Forward10,
                    contentDescription = null,
                )
                Text(
                    text = formatPlaybackTime(value.targetMs),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is PlayerGestureFeedback.Brightness -> VerticalLevelIndicator(
                fraction = value.percent / 100f,
                label = "亮度",
                icon = PlayerIcons.Brightness,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(24.dp)
                    .testTag("gesture_brightness_rail"),
            )

            is PlayerGestureFeedback.Volume -> VerticalLevelIndicator(
                fraction = value.percent / 100f,
                label = "音量",
                icon = if (value.muted) PlayerIcons.Muted else PlayerIcons.Volume,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .testTag("gesture_volume_rail"),
            )
        }
    }
}
