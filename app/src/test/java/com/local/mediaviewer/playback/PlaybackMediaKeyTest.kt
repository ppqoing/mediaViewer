package com.local.mediaviewer.playback

import com.local.mediaviewer.model.SessionEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackMediaKeyTest {
    @Test
    fun `DNS 请求 IPv4 变化不改变逻辑媒体键`() {
        val logical =
            "http://media.example:8080/middle/%E5%BD%B1%E7%89%87.mp4"
        val first = SessionEndpoint(
            "http://media.example:8080",
            "http://192.0.2.1:8080",
            "192.0.2.1",
        )
        val second = first.copy(
            requestBaseUrl = "http://192.0.2.2:8080",
            ipv4 = "192.0.2.2",
        )

        assertNotEquals(
            first.requestUrlFor(logical),
            second.requestUrlFor(logical),
        )
        val mediaKey = PlaybackMediaKey.fromLogicalUrl(logical)
        assertEquals(logical, mediaKey)
        assertFalse(mediaKey.contains("192.0.2."))
    }

    @Test
    fun `媒体键保留查询参数但移除片段`() {
        assertEquals(
            "http://media.example:8080/pik/a.mp3?edition=2",
            PlaybackMediaKey.fromLogicalUrl(
                "http://media.example:8080/pik/a.mp3?edition=2#chapter",
            ),
        )
    }
}
