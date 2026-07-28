package com.local.mediaviewer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RootShareTest {
    @Test
    fun `固定入口与服务路径保持设计顺序`() {
        assertEquals(
            listOf(
                Triple("middle", "MiddleDir", "/middle/"),
                Triple("pik", "pik", "/pik/"),
            ),
            RootShare.entries.map { Triple(it.id, it.displayName, it.path) },
        )
    }

    @Test
    fun `根目录 ID 可恢复为同一个入口`() {
        assertSame(RootShare.MIDDLE, RootShare.fromId("middle"))
        assertSame(RootShare.PIK, RootShare.fromId("pik"))
    }

    @Test
    fun `未知根目录 ID 被拒绝`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RootShare.fromId("unknown")
        }

        assertEquals("未知根目录：unknown", error.message)
    }
}
