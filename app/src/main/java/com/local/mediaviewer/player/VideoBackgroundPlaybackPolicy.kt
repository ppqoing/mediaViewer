package com.local.mediaviewer.player

enum class VideoSessionExitReason {
    NAVIGATE_AWAY,
    APP_BACKGROUND,
    CONFIGURATION_CHANGE,
}

object VideoBackgroundPlaybackPolicy {
    fun shouldStopAndClear(
        enabled: Boolean,
        reason: VideoSessionExitReason,
    ): Boolean =
        !enabled && reason != VideoSessionExitReason.CONFIGURATION_CHANGE
}
