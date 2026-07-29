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
    fun `播放器路由在连接窗口等待而在已连接空队列时退出`() {
        assertEquals(
            PlayerRouteContent.Waiting,
            resolvePlayerRouteContent(
                PlaybackSessionState(
                    playback = PlaybackState(status = PlaybackStatus.OPENING),
                ),
            ),
        )
        assertEquals(
            PlayerRouteContent.Empty,
            resolvePlayerRouteContent(
                PlaybackSessionState(
                    playback = PlaybackState(status = PlaybackStatus.IDLE),
                ),
            ),
        )
        assertEquals(
            PlayerRouteContent.Ready(item("b")),
            resolvePlayerRouteContent(
                PlaybackSessionState(
                    queue = PlaybackQueue(
                        items = listOf(item("b")),
                        currentMediaKey = "b",
                    ),
                    currentItem = item("b"),
                ),
            ),
        )
    }

    private fun item(key: String) = QueueMediaItem(
        mediaKey = key,
        name = "$key.mp4",
        logicalUrl = "http://media.example/$key.mp4",
        kind = MediaKind.VIDEO,
    )
}
