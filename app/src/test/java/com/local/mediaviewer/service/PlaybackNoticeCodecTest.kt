package com.local.mediaviewer.service

import android.os.Bundle
import com.local.mediaviewer.queue.PlaybackNotice
import com.local.mediaviewer.queue.PlaybackNoticeAction
import com.local.mediaviewer.queue.PlaybackNoticeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackNoticeCodecTest {
    @Test
    fun `notice bundle round trip preserves the event`() {
        val original = PlaybackNotice(
            id = 42L,
            kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
            message = "播放队列保存失败",
            action = PlaybackNoticeAction.RETRY_PERSISTENCE,
        )

        assertEquals(
            original,
            PlaybackNoticeCodec.decode(PlaybackNoticeCodec.encode(original)),
        )
    }

    @Test
    fun `unknown kind is ignored`() {
        val bundle = validBundle().apply {
            putString("kind", "FUTURE_KIND")
        }

        assertNull(PlaybackNoticeCodec.decode(bundle))
    }

    @Test
    fun `unknown action is ignored`() {
        val bundle = validBundle().apply {
            putString("action", "FUTURE_ACTION")
        }

        assertNull(PlaybackNoticeCodec.decode(bundle))
    }

    @Test
    fun `missing id is ignored`() {
        val bundle = validBundle().apply {
            remove("id")
        }

        assertNull(PlaybackNoticeCodec.decode(bundle))
    }

    @Test
    fun `blank message is ignored`() {
        val bundle = validBundle().apply {
            putString("message", "   ")
        }

        assertNull(PlaybackNoticeCodec.decode(bundle))
    }

    private fun validBundle() = Bundle().apply {
        putLong("id", 7L)
        putString("kind", PlaybackNoticeKind.QUEUE_SAVE_FAILED.name)
        putString("message", "播放队列保存失败")
        putString("action", PlaybackNoticeAction.RETRY_PERSISTENCE.name)
    }
}
