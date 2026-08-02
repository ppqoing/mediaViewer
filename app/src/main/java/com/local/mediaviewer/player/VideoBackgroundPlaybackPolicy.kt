package com.local.mediaviewer.player

enum class VideoSessionExitReason {
    NAVIGATE_AWAY,
    APP_BACKGROUND,
    CONFIGURATION_CHANGE,
}

data class VideoBackgroundLifecycleState(
    val isForeground: Boolean = true,
    val pendingResumeMediaKey: String? = null,
)

enum class VideoBackgroundLifecycleAction {
    NONE,
    PAUSE,
    PLAY,
}

data class VideoBackgroundLifecycleTransition(
    val state: VideoBackgroundLifecycleState,
    val action: VideoBackgroundLifecycleAction,
)

object VideoBackgroundPlaybackPolicy {
    fun onAppStopped(
        state: VideoBackgroundLifecycleState,
        backgroundPlaybackEnabled: Boolean,
        reason: VideoSessionExitReason,
        currentMediaKey: String?,
        playWhenReady: Boolean,
    ): VideoBackgroundLifecycleTransition {
        val backgroundState = state.copy(isForeground = false)
        if (
            reason != VideoSessionExitReason.APP_BACKGROUND ||
            backgroundPlaybackEnabled
        ) {
            return VideoBackgroundLifecycleTransition(
                backgroundState.copy(pendingResumeMediaKey = null),
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        if (state.pendingResumeMediaKey != null) {
            return VideoBackgroundLifecycleTransition(
                backgroundState,
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        val mediaKey = currentMediaKey?.takeIf(String::isNotBlank)
        if (!playWhenReady || mediaKey == null) {
            return VideoBackgroundLifecycleTransition(
                backgroundState.copy(pendingResumeMediaKey = null),
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        return VideoBackgroundLifecycleTransition(
            backgroundState.copy(pendingResumeMediaKey = mediaKey),
            VideoBackgroundLifecycleAction.PAUSE,
        )
    }

    fun onAppStarted(
        state: VideoBackgroundLifecycleState,
    ): VideoBackgroundLifecycleState = state.copy(isForeground = true)

    fun reconcileForeground(
        state: VideoBackgroundLifecycleState,
        currentMediaKey: String?,
        hasActiveVideo: Boolean,
    ): VideoBackgroundLifecycleTransition {
        val pending = state.pendingResumeMediaKey
        if (!state.isForeground || pending == null) {
            return VideoBackgroundLifecycleTransition(
                state,
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        if (!hasActiveVideo) {
            return VideoBackgroundLifecycleTransition(
                clearPending(state),
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        if (currentMediaKey == null) {
            return VideoBackgroundLifecycleTransition(
                state,
                VideoBackgroundLifecycleAction.NONE,
            )
        }
        return VideoBackgroundLifecycleTransition(
            clearPending(state),
            if (currentMediaKey == pending) {
                VideoBackgroundLifecycleAction.PLAY
            } else {
                VideoBackgroundLifecycleAction.NONE
            },
        )
    }

    fun clearPending(
        state: VideoBackgroundLifecycleState,
    ): VideoBackgroundLifecycleState =
        state.copy(pendingResumeMediaKey = null)

    fun shouldStopAndClear(
        reason: VideoSessionExitReason,
    ): Boolean = reason == VideoSessionExitReason.NAVIGATE_AWAY

    @Deprecated("Remove after MediaViewerApp lifecycle migration")
    fun shouldStopAndClear(
        enabled: Boolean,
        reason: VideoSessionExitReason,
    ): Boolean = !enabled && reason != VideoSessionExitReason.CONFIGURATION_CHANGE
}
