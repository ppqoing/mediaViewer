package com.local.mediaviewer.navigation

import com.local.mediaviewer.model.MediaKind

data class PlayerRouteLifecycleState(
    val lastPresentedKind: MediaKind? = null,
)

enum class PlayerRouteExitAction {
    LEAVE_ONLY,
    STOP_AND_CLEAR,
}

object PlayerRouteLifecyclePolicy {
    fun observeCurrentItem(
        state: PlayerRouteLifecycleState,
        currentKind: MediaKind?,
    ): PlayerRouteLifecycleState = if (currentKind == null) {
        state
    } else {
        state.copy(lastPresentedKind = currentKind)
    }

    fun exitAction(
        state: PlayerRouteLifecycleState,
    ): PlayerRouteExitAction = if (state.lastPresentedKind == MediaKind.VIDEO) {
        PlayerRouteExitAction.STOP_AND_CLEAR
    } else {
        PlayerRouteExitAction.LEAVE_ONLY
    }
}
