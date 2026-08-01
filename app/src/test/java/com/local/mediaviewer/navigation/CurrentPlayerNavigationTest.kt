package com.local.mediaviewer.navigation

import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentPlayerNavigationTest {
    @Test
    fun `通知请求在当前项尚未恢复时不会被消费`() {
        val requests = CurrentPlayerNavigationRequests()

        requests.requestOpenCurrentPlayer()

        assertNull(requests.consumeIfReady(null))
        assertEquals("b", requests.consumeIfReady(item("b")))
        assertNull(requests.consumeIfReady(item("b")))
    }

    @Test
    fun `没有通知请求时当前项恢复也不会导航`() {
        val requests = CurrentPlayerNavigationRequests()

        assertNull(requests.consumeIfReady(item("a")))
    }

    @Test
    fun `connection failure wins over the initial waiting state`() {
        val state = resolvePlayerEntryState(
            session = session(currentItem = null, errorMessage = "播放器连接失败"),
            hasPresentedItem = false,
            waitExpired = false,
        )

        assertEquals(
            PlayerEntryState.Failed("播放器连接失败"),
            state,
        )
    }

    @Test
    fun `initial idle becomes empty only after the finite wait`() {
        val idle = session(currentItem = null, status = PlaybackStatus.IDLE)

        assertEquals(
            PlayerEntryState.Connecting,
            resolvePlayerEntryState(
                idle,
                hasPresentedItem = false,
                waitExpired = false,
            ),
        )
        assertEquals(
            PlayerEntryState.Empty,
            resolvePlayerEntryState(
                idle,
                hasPresentedItem = false,
                waitExpired = true,
            ),
        )
    }

    @Test
    fun `a current item always produces ready`() {
        val item = item("video-a")

        assertEquals(
            PlayerEntryState.Ready(item),
            resolvePlayerEntryState(
                session(currentItem = item, errorMessage = "stale error"),
                hasPresentedItem = false,
                waitExpired = true,
            ),
        )
    }

    @Test
    fun `presented player keeps connecting while the session reopens`() {
        val opening = session(currentItem = null, status = PlaybackStatus.OPENING)

        assertEquals(
            PlayerEntryState.Connecting,
            resolvePlayerEntryState(
                opening,
                hasPresentedItem = true,
                waitExpired = false,
            ),
        )
        assertEquals(
            PlayerEntryState.Connecting,
            resolvePlayerEntryState(
                opening,
                hasPresentedItem = true,
                waitExpired = true,
            ),
        )
    }

    @Test
    fun `presented player leaves only after an idle empty wait expires`() {
        val idle = session(currentItem = null, status = PlaybackStatus.IDLE)

        assertEquals(
            PlayerEntryState.Connecting,
            resolvePlayerEntryState(
                idle,
                hasPresentedItem = true,
                waitExpired = false,
            ),
        )
        assertEquals(
            PlayerEntryState.Empty,
            resolvePlayerEntryState(
                idle,
                hasPresentedItem = true,
                waitExpired = true,
            ),
        )
    }

    @Test
    fun `只有通知 action 与显式 extra 同时匹配才接受打开请求`() {
        assertEquals(
            true,
            isCurrentPlayerNotificationRequest(
                action = ACTION_OPEN_CURRENT_PLAYER,
                requested = true,
            ),
        )
        assertEquals(
            false,
            isCurrentPlayerNotificationRequest(
                action = ACTION_OPEN_CURRENT_PLAYER,
                requested = false,
            ),
        )
        assertEquals(
            false,
            isCurrentPlayerNotificationRequest(
                action = "other",
                requested = true,
            ),
        )
    }

    private fun session(
        currentItem: QueueMediaItem? = null,
        status: PlaybackStatus = PlaybackStatus.IDLE,
        errorMessage: String? = null,
    ) = PlaybackSessionState(
        playback = PlaybackState(status = status),
        queue = PlaybackQueue(
            items = listOfNotNull(currentItem),
            currentMediaKey = currentItem?.mediaKey,
        ),
        currentItem = currentItem,
        errorMessage = errorMessage,
    )

    private fun item(key: String) = QueueMediaItem(
        mediaKey = key,
        name = "$key.mp4",
        logicalUrl = "http://media.example/$key.mp4",
        kind = MediaKind.VIDEO,
    )
}
