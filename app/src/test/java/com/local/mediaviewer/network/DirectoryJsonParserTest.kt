package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryJsonParserTest {
    private val parser = DefaultDirectoryJsonParser()

    @Test
    fun `解析字段并将文件夹稳定排在文件之前`() {
        val json = """
            [
              {"name":"z.MP4","size":8,"url":"z.MP4","mod_time":"2026-07-28T01:02:03Z","mode":420,"is_dir":false,"is_symlink":false},
              {"name":"子目录","size":0,"url":"%E5%AD%90%E7%9B%AE%E5%BD%95/","mod_time":"2026-07-28T01:02:03Z","mode":493,"is_dir":true,"is_symlink":false},
              {"name":"A.mp3","size":4,"url":"A.mp3","mod_time":"2026-07-28T01:02:03Z","mode":420,"is_dir":false,"is_symlink":false}
            ]
        """.trimIndent()

        val entries = (
            parser.parse(
                json,
                "http://media.example:8080/middle/",
                "http://203.0.113.8:8080/middle/",
            ) as AppResult.Success<List<DirectoryEntry>>
        ).value

        assertEquals(listOf("子目录", "A.mp3", "z.MP4"), entries.map { it.name })
        assertEquals(MediaKind.DIRECTORY, entries[0].kind)
        assertEquals(493L, entries[0].mode)
        assertFalse(entries[0].isSymlink)
        assertEquals(
            "http://media.example:8080/middle/%E5%AD%90%E7%9B%AE%E5%BD%95/",
            entries[0].logicalUrl,
        )
        assertFalse(entries[0].logicalUrl.contains("%25E5"))
    }

    @Test
    fun `Unicode 原文只编码一次且逻辑与请求主机分离`() {
        val json = """
            [{"name":"動画 (1) 😀.mp4","size":8,"url":"動画 (1) 😀.mp4","mod_time":"2026-07-28T01:02:03Z","mode":420,"is_dir":false,"is_symlink":false}]
        """.trimIndent()
        val entry = (
            parser.parse(
                json,
                "http://media.example:8080/pik/",
                "http://198.51.100.7:8080/pik/",
            ) as AppResult.Success<List<DirectoryEntry>>
        ).value.single()

        assertTrue(entry.logicalUrl.startsWith("http://media.example:8080/"))
        assertTrue(entry.requestUrl.startsWith("http://198.51.100.7:8080/"))
        assertEquals(
            "http://media.example:8080/pik/" +
                "%E5%8B%95%E7%94%BB%20(1)%20%F0%9F%98%80.mp4",
            entry.logicalUrl,
        )
        assertEquals(MediaKind.VIDEO, entry.kind)
    }

    @Test
    fun `空数组成功而缺字段和无效时间失败`() {
        val empty = parser.parse(
            "[]",
            "http://media.example/middle/",
            "http://192.0.2.1/middle/",
        ) as AppResult.Success<List<DirectoryEntry>>
        assertEquals(emptyList<DirectoryEntry>(), empty.value)

        val missingField = parser.parse(
            """[{"name":"x"}]""",
            "http://media.example/middle/",
            "http://192.0.2.1/middle/",
        )
        assertTrue(missingField is AppResult.Failure)

        val invalidTime = parser.parse(
            """[{"name":"x.mp4","size":1,"url":"x.mp4","mod_time":"not-an-instant","mode":420,"is_dir":false,"is_symlink":false}]""",
            "http://media.example/middle/",
            "http://192.0.2.1/middle/",
        )
        assertTrue(invalidTime is AppResult.Failure)
    }
}
