package com.local.mediaviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun MediaVerticalLevelControl(
    value: Float,
    label: String,
    enabled: Boolean = true,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MediaTheme.playerColors.volume
    val fraction = value.coerceIn(0f, 1f)
    val controlModifier = modifier
        .pointerInput(enabled, onValueChanged) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                fun publish(y: Float) {
                    onValueChanged(
                        (1f - y / size.height).coerceIn(0f, 1f),
                    )
                }
                publish(down.position.y)
                do {
                    val event = awaitPointerEvent()
                    event.changes.firstOrNull()?.let { change ->
                        publish(change.position.y)
                        change.consume()
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .size(
            width = MediaTheme.sizing.verticalLevelWidth,
            height = MediaTheme.sizing.verticalLevelHeight,
        )
        .semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = fraction,
                range = 0f..1f,
            )
            setProgress { requested ->
                if (!enabled) return@setProgress false
                onValueChanged(requested.coerceIn(0f, 1f))
                true
            }
            if (!enabled) disabled()
        }

    Canvas(modifier = controlModifier) {
        val barWidth = 8.dp.toPx()
        val left = (size.width - barWidth) / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(left, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
        val filledHeight = size.height * fraction
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(left, size.height - filledHeight),
            size = Size(barWidth, filledHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
}
