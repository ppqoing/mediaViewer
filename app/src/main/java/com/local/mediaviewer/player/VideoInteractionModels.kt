package com.local.mediaviewer.player

enum class VideoGestureAxis {
    UNDECIDED,
    SEEK,
    BRIGHTNESS,
    VOLUME,
}

sealed interface PlayerGestureFeedback {
    data class Seek(val targetMs: Long, val deltaMs: Long) : PlayerGestureFeedback

    data class Brightness(val percent: Int) : PlayerGestureFeedback

    data class Volume(val percent: Int, val muted: Boolean) : PlayerGestureFeedback
}

data class VideoInteractionState(
    val controlsVisible: Boolean = true,
    val autoHideEpoch: Long = 0L,
    val controlsLocked: Boolean = false,
    val menuExpanded: Boolean = false,
    val scrubbing: Boolean = false,
    val feedback: PlayerGestureFeedback? = null,
)

data class GestureClassificationInput(
    val startX: Float,
    val width: Float,
    val deltaX: Float,
    val deltaY: Float,
    val thresholdPx: Float,
)
