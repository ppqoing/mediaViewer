package com.local.mediaviewer.ui.browser

import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserFormattersTest {
    @Test
    fun `目录无大小且文件按 IEC 单位格式化`() {
        assertEquals("—", formatEntrySize(0, isDirectory = true))
        assertEquals("0 B", formatEntrySize(0, isDirectory = false))
        assertEquals("512 B", formatEntrySize(512, isDirectory = false))
        assertEquals("1.5 KiB", formatEntrySize(1536, isDirectory = false))
        assertEquals(
            "2.0 MiB",
            formatEntrySize(2L * 1024 * 1024, isDirectory = false),
        )
        assertEquals(
            "1.0 GiB",
            formatEntrySize(1024L * 1024 * 1024, isDirectory = false),
        )
    }

    @Test
    fun `修改时间按指定本地时区显示到分钟`() {
        assertEquals(
            "2026-07-28 09:02",
            formatModifiedAt(
                Instant.parse("2026-07-28T01:02:03Z"),
                ZoneId.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun `筛选只接受当前已加载列表中的对应媒体类型`() {
        val folder = browserEntry("folder", MediaKind.DIRECTORY)
        val video = browserEntry("clip.mp4", MediaKind.VIDEO)
        val audio = browserEntry("song.flac", MediaKind.AUDIO)
        val image = browserEntry("cover.jpg", MediaKind.IMAGE)
        val gif = browserEntry("motion.GIF", MediaKind.IMAGE)

        assertTrue(BrowserFilter.ALL.accepts(folder))
        assertTrue(BrowserFilter.ALL.accepts(video))
        assertTrue(BrowserFilter.FOLDERS.accepts(folder))
        assertFalse(BrowserFilter.FOLDERS.accepts(video))
        assertTrue(BrowserFilter.VIDEO.accepts(video))
        assertFalse(BrowserFilter.VIDEO.accepts(image))
        assertTrue(BrowserFilter.AUDIO.accepts(audio))
        assertFalse(BrowserFilter.AUDIO.accepts(video))
        assertTrue(BrowserFilter.IMAGE.accepts(image))
        assertFalse(BrowserFilter.IMAGE.accepts(gif))
        assertTrue(BrowserFilter.GIF.accepts(gif))
        assertFalse(BrowserFilter.GIF.accepts(image))
    }
}

private fun browserEntry(name: String, kind: MediaKind) = DirectoryEntry(
    name = name,
    size = 1,
    modifiedAt = Instant.EPOCH,
    mode = 420,
    isDirectory = kind == MediaKind.DIRECTORY,
    isSymlink = false,
    logicalUrl = "http://media.example/middle/$name",
    requestUrl = "http://192.0.2.1/middle/$name",
    kind = kind,
)
