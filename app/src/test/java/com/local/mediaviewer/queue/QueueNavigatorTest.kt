package com.local.mediaviewer.queue

import com.local.mediaviewer.model.MediaKind
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueNavigatorTest {
    @Test
    fun `添加已有项移动而不是复制`() {
        val moved = QueueNavigator.addNext(queueOf("a", "b", "c", current = "a"), item("c"))

        assertEquals(listOf("a", "c", "b"), moved.keys())
    }

    @Test
    fun `插入新增项并保持逻辑地址原样`() {
        val next = QueueNavigator.addNext(
            queueOf("a", "b", current = "a"),
            item("c", logicalUrl = "https://host/media%20file.mp4?token=A%2BB"),
        )
        val appended = QueueNavigator.append(next, item("d"))

        assertEquals(listOf("a", "c", "b", "d"), appended.keys())
        assertEquals("https://host/media%20file.mp4?token=A%2BB", next.items[1].logicalUrl)
    }

    @Test
    fun `队列拒绝图片和目录但保留未知媒体`() {
        val replaced = QueueNavigator.replace(
            listOf(
                item("video", MediaKind.VIDEO),
                item("image", MediaKind.IMAGE),
                item("directory", MediaKind.DIRECTORY),
                item("unknown", MediaKind.UNKNOWN),
            ),
            startMediaKey = "image",
            mode = PlaybackMode.SEQUENTIAL,
            random = Random(1),
        )
        val unchanged = QueueNavigator.append(replaced, item("cover", MediaKind.IMAGE))

        assertEquals(listOf("video", "unknown"), replaced.keys())
        assertEquals("video", replaced.currentMediaKey)
        assertEquals(replaced, unchanged)
    }

    @Test
    fun `移动项目使用边界索引并保持当前项目`() {
        val moved = QueueNavigator.move(queueOf("a", "b", "c", current = "b"), "a", 99)

        assertEquals(listOf("b", "c", "a"), moved.keys())
        assertEquals("b", moved.currentMediaKey)
        assertEquals(0, moved.currentIndex)
    }

    @Test
    fun `顺序模式在末项结束时停止`() {
        assertNull(QueueNavigator.next(queueOf("a", current = "a"), QueueAdvanceReason.ENDED))
        assertEquals("b", QueueNavigator.next(queueOf("a", "b", current = "a"), QueueAdvanceReason.USER))
        assertEquals("a", QueueNavigator.previous(queueOf("a", "b", current = "b")))
    }

    @Test
    fun `循环模式在两端环绕`() {
        val queue = queueOf("a", "b", current = "b", mode = PlaybackMode.REPEAT_ALL)

        assertEquals("a", QueueNavigator.next(queue, QueueAdvanceReason.ENDED))
        assertEquals("a", QueueNavigator.previous(queue.copy(currentMediaKey = "b")))
        assertEquals("b", QueueNavigator.previous(queue.copy(currentMediaKey = "a")))
    }

    @Test
    fun `单曲循环只在播放结束时重复`() {
        val queue = queueOf("a", "b", current = "a", mode = PlaybackMode.REPEAT_ONE)

        assertEquals("a", QueueNavigator.next(queue, QueueAdvanceReason.ENDED))
        assertEquals("b", QueueNavigator.next(queue, QueueAdvanceReason.USER))
        assertEquals("b", QueueNavigator.next(queue, QueueAdvanceReason.CURRENT_REMOVED))
    }

    @Test
    fun `随机模式持久化实际顺序且前后导航可逆`() {
        val shuffled = QueueNavigator.setMode(
            queueOf("a", "b", "c", "d", current = "b"),
            PlaybackMode.SHUFFLE,
            Random(7),
        )

        assertEquals(PlaybackMode.SHUFFLE, shuffled.mode)
        assertEquals(4, shuffled.shuffleOrder.distinct().size)
        assertEquals(setOf("a", "b", "c", "d"), shuffled.shuffleOrder.toSet())
        assertEquals("b", shuffled.shuffleOrder[shuffled.shuffleCursor])

        val atSecond = shuffled.copy(
            currentMediaKey = shuffled.shuffleOrder[1],
            shuffleCursor = 1,
        )
        assertEquals(shuffled.shuffleOrder[0], QueueNavigator.previous(atSecond))
        assertEquals(shuffled.shuffleOrder[2], QueueNavigator.next(atSecond, QueueAdvanceReason.USER))
    }

    @Test
    fun `随机队列重建后下一项一致`() {
        val shuffled = QueueNavigator.setMode(
            queueOf("a", "b", "c", current = "a"),
            PlaybackMode.SHUFFLE,
            Random(7),
        )
        val rebuilt = PlaybackQueue(
            items = shuffled.items,
            currentMediaKey = shuffled.currentMediaKey,
            mode = shuffled.mode,
            shuffleOrder = shuffled.shuffleOrder,
            shuffleCursor = shuffled.shuffleCursor,
            playbackSpeed = shuffled.playbackSpeed,
        )

        assertEquals(
            QueueNavigator.next(shuffled, QueueAdvanceReason.ENDED),
            QueueNavigator.next(rebuilt, QueueAdvanceReason.ENDED),
        )
    }

    @Test
    fun `随机队列增删保留未删除项顺序并把新项置于待播区`() {
        val shuffled = QueueNavigator.setMode(
            queueOf("a", "b", "c", current = "a"),
            PlaybackMode.SHUFFLE,
            Random(7),
        )
        val added = QueueNavigator.append(shuffled, item("d"))
        val removed = QueueNavigator.remove(added, "b", Random(9))

        assertEquals("d", added.shuffleOrder[added.shuffleCursor + 1])
        assertFalse(removed.shuffleOrder.contains("b"))
        assertEquals(
            added.shuffleOrder.filterNot { it == "b" },
            removed.shuffleOrder,
        )
    }

    @Test
    fun `删除当前项选择后继且空队列清空当前项`() {
        val remaining = QueueNavigator.remove(
            queueOf("a", "b", current = "a"),
            "a",
            Random(1),
        )
        val empty = QueueNavigator.remove(
            queueOf("a", current = "a"),
            "a",
            Random(1),
        )

        assertEquals("b", remaining.currentMediaKey)
        assertTrue(empty.items.isEmpty())
        assertNull(empty.currentMediaKey)
        assertEquals(-1, empty.currentIndex)
    }

    private fun queueOf(
        vararg keys: String,
        current: String,
        mode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    ): PlaybackQueue = PlaybackQueue(
        items = keys.map(::item),
        currentMediaKey = current,
        mode = mode,
    )

    private fun item(
        key: String,
        kind: MediaKind = MediaKind.VIDEO,
        logicalUrl: String = "https://host/$key.mp4",
    ) = QueueMediaItem(
        mediaKey = key,
        name = key,
        logicalUrl = logicalUrl,
        kind = kind,
    )

    private fun PlaybackQueue.keys(): List<String> = items.map { it.mediaKey }
}
