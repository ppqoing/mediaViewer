package com.local.mediaviewer.queue

import java.util.concurrent.atomic.AtomicLong

enum class PlaybackNoticeKind {
    QUEUE_SAVE_FAILED,
    POSITION_SAVE_FAILED,
}

enum class PlaybackNoticeAction {
    RETRY_PERSISTENCE,
}

data class PlaybackNotice(
    val id: Long,
    val kind: PlaybackNoticeKind,
    val message: String,
    val action: PlaybackNoticeAction? = null,
)

private val playbackNoticeIds =
    AtomicLong(System.currentTimeMillis())

internal fun nextPlaybackNoticeId(): Long =
    playbackNoticeIds.incrementAndGet()
