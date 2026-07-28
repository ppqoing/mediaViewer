package com.local.mediaviewer.ui.browser

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
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
}
