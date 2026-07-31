package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.local.mediaviewer.ui.theme.MediaTheme
import kotlin.math.roundToLong

@Composable
fun MediaTimelineSlider(
    durationMs: Long,
    positionMs: Long,
    enabled: Boolean = true,
    onDragStart: () -> Unit,
    onPositionPreview: (Long) -> Unit,
    onPositionCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val clampedPosition = positionMs.coerceIn(0L, safeDuration)
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(clampedPosition.toFloat()) }
    val effectiveEnabled = enabled && safeDuration > 0L
    val displayedPosition = if (dragging) dragPosition else clampedPosition.toFloat()

    Slider(
        value = displayedPosition,
        onValueChange = { requested ->
            if (!dragging) {
                dragging = true
                onDragStart()
            }
            dragPosition = requested.coerceIn(0f, safeDuration.toFloat())
            onPositionPreview(dragPosition.roundToLong().coerceIn(0L, safeDuration))
        },
        onValueChangeFinished = {
            if (dragging) {
                onPositionCommit(
                    dragPosition.roundToLong().coerceIn(0L, safeDuration),
                )
                dragging = false
            }
        },
        modifier = modifier.heightIn(min = MediaTheme.sizing.timelineTouchHeight),
        enabled = effectiveEnabled,
        valueRange = 0f..safeDuration.toFloat(),
    )
}
