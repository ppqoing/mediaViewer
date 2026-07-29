package com.local.mediaviewer.browser

import com.local.mediaviewer.queue.QueueMediaItem

enum class BrowserPlaybackAction {
    PLAY_DIRECTORY,
    PLAY_NEXT,
    ADD_TO_QUEUE,
}

data class BrowserPlaybackRequest(
    val action: BrowserPlaybackAction,
    val selected: QueueMediaItem,
    val directoryItems: List<QueueMediaItem>,
)
