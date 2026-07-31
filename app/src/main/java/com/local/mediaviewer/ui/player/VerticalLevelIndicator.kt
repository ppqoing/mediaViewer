package com.local.mediaviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun VerticalLevelIndicator(
    fraction: Float,
    label: String,
    icon: ImageVector,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val percent = (safeFraction * 100).roundToInt()
    val trackShape = RoundedCornerShape(2.dp)

    Column(
        modifier = modifier
            .width(64.dp)
            .height(220.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = "$percent%"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeonPlayerIcon(
            icon = icon,
            contentDescription = null,
            active = true,
            modifier = Modifier
                .size(24.dp)
                .clearAndSetSemantics {},
        )
        Text(
            text = "$percent%",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .weight(1f)
                .background(Color.White.copy(alpha = 0.28f), trackShape),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(safeFraction)
                    .background(fillColor, trackShape),
            )
        }
    }
}
