package com.local.mediaviewer.service

import android.os.Bundle
import com.local.mediaviewer.queue.PlaybackNotice
import com.local.mediaviewer.queue.PlaybackNoticeAction
import com.local.mediaviewer.queue.PlaybackNoticeKind

const val ACTION_PLAYBACK_NOTICE =
    "com.local.mediaviewer.action.PLAYBACK_NOTICE"

const val ACTION_RETRY_PERSISTENCE =
    "com.local.mediaviewer.action.RETRY_PERSISTENCE"

object PlaybackNoticeCodec {
    private const val KEY_ID = "id"
    private const val KEY_KIND = "kind"
    private const val KEY_MESSAGE = "message"
    private const val KEY_ACTION = "action"

    fun encode(notice: PlaybackNotice): Bundle = Bundle().apply {
        putLong(KEY_ID, notice.id)
        putString(KEY_KIND, notice.kind.name)
        putString(KEY_MESSAGE, notice.message)
        putString(KEY_ACTION, notice.action?.name)
    }

    fun decode(bundle: Bundle): PlaybackNotice? {
        if (!bundle.containsKey(KEY_ID)) return null
        val kind = bundle.getString(KEY_KIND)?.let { raw ->
            PlaybackNoticeKind.entries.firstOrNull { it.name == raw }
        } ?: return null
        val message = bundle.getString(KEY_MESSAGE)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val rawAction = bundle.getString(KEY_ACTION)
        val action = if (rawAction == null) {
            null
        } else {
            PlaybackNoticeAction.entries.firstOrNull { it.name == rawAction }
                ?: return null
        }
        return PlaybackNotice(
            id = bundle.getLong(KEY_ID),
            kind = kind,
            message = message,
            action = action,
        )
    }
}
