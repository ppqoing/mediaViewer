package com.local.mediaviewer.navigation

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

fun isCurrentPlayerNotificationRequest(
    action: String?,
    requested: Boolean,
): Boolean = action == ACTION_OPEN_CURRENT_PLAYER && requested

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

sealed interface PlayerRouteContent {
    data object Waiting : PlayerRouteContent

    data object Empty : PlayerRouteContent

    data class Ready(
        val item: QueueMediaItem,
    ) : PlayerRouteContent
}

fun resolvePlayerRouteContent(
    session: PlaybackSessionState,
    hasPresentedItem: Boolean,
): PlayerRouteContent = session.currentItem?.let(PlayerRouteContent::Ready)
    ?: if (
        !hasPresentedItem ||
        session.playback.status == PlaybackStatus.OPENING
    ) {
        PlayerRouteContent.Waiting
    } else {
        PlayerRouteContent.Empty
    }
