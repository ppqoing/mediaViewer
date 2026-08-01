package com.local.mediaviewer.navigation

import androidx.navigation.NavHostController
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ACTION_OPEN_CURRENT_PLAYER =
    "com.local.mediaviewer.action.OPEN_CURRENT_PLAYER"
const val EXTRA_OPEN_CURRENT_PLAYER =
    "com.local.mediaviewer.extra.OPEN_CURRENT_PLAYER"
const val PLAYER_ENTRY_WAIT_TIMEOUT_MS: Long = 5_000L

fun isCurrentPlayerNotificationRequest(
    action: String?,
    requested: Boolean,
): Boolean = action == ACTION_OPEN_CURRENT_PLAYER && requested

internal fun NavHostController.leavePlayerSafely() {
    if (!popBackStack()) {
        navigate(HomeRoute) {
            popUpTo(graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
    }
}

class CurrentPlayerNavigationRequests {
    private val mutableRequestNonce = MutableStateFlow(0L)
    val requestNonce: StateFlow<Long> = mutableRequestNonce.asStateFlow()
    private var consumedNonce = 0L

    fun requestOpenCurrentPlayer() {
        mutableRequestNonce.value += 1L
    }

    @Synchronized
    fun consumeIfReady(currentItem: QueueMediaItem?): String? {
        if (currentItem == null || consumedNonce == mutableRequestNonce.value) {
            return null
        }
        consumedNonce = mutableRequestNonce.value
        return currentItem.mediaKey
    }
}

sealed interface PlayerEntryState {
    data object Connecting : PlayerEntryState

    data class Ready(
        val item: QueueMediaItem,
    ) : PlayerEntryState

    data object Empty : PlayerEntryState

    data class Failed(
        val message: String,
    ) : PlayerEntryState
}

fun resolvePlayerEntryState(
    session: PlaybackSessionState,
    hasPresentedItem: Boolean,
    waitExpired: Boolean,
): PlayerEntryState = when {
    session.currentItem != null -> PlayerEntryState.Ready(session.currentItem)
    session.errorMessage != null -> PlayerEntryState.Failed(session.errorMessage)
    // 暂停退到后台时控制器进入休眠，回前台重连期间 currentItem 短暂为 null；
    // 此时必须保持 Connecting，不能把已呈现的播放器页当作空队列弹走。
    session.playback.status == PlaybackStatus.OPENING || !waitExpired ->
        PlayerEntryState.Connecting
    hasPresentedItem -> PlayerEntryState.Empty
    else -> PlayerEntryState.Empty
}
