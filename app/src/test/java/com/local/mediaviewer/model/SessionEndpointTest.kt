package com.local.mediaviewer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionEndpointTest {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://203.0.113.9:8080",
        ipv4 = "203.0.113.9",
    )

    @Test
    fun `仅替换 authority 并保持已经编码的路径`() {
        assertEquals(
            "http://203.0.113.9:8080/middle/%E5%BD%B1%E7%89%87%20%281%29.mp4",
            endpoint.requestUrlFor(
                "http://media.example:8080/middle/%E5%BD%B1%E7%89%87%20%281%29.mp4",
            ),
        )
    }

    @Test
    fun `保留编码查询参数并移除片段`() {
        assertEquals(
            "http://203.0.113.9:8080/pik/a.jpg?size=large%20image",
            endpoint.requestUrlFor(
                "http://media.example:8080/pik/a.jpg?size=large%20image#page",
            ),
        )
    }

    @Test
    fun `拒绝其他逻辑服务器的 URL`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            endpoint.requestUrlFor("http://other.example:8080/middle/a.mp4")
        }

        assertEquals("逻辑媒体 URL 不属于当前服务器", error.message)
    }
}
