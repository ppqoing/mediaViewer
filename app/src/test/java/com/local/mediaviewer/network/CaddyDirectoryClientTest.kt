package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DispatcherProvider
import com.local.mediaviewer.model.DirectoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaddyDirectoryClientTest {
    private lateinit var server: MockWebServer

    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() = server.close()

    @Test
    fun `发送 JSON Accept 并解析 200`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("[]")
                .build(),
        )
        val client = DefaultCaddyDirectoryClient(
            client = OkHttpClient(),
            parser = DefaultDirectoryJsonParser(),
            dispatchers = dispatchers,
        )

        val result = client.listDirectory(
            logicalDirectoryUrl = "http://media.example/middle/",
            requestDirectoryUrl = server.url("/middle/").toString(),
        )

        val entries = (result as AppResult.Success<List<DirectoryEntry>>).value
        assertEquals(emptyList<DirectoryEntry>(), entries)
        assertEquals("application/json", server.takeRequest().headers["Accept"])
    }

    @Test
    fun `HTTP 错误不解析正文`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(403)
                .body("""[{"not":"a directory"}]""")
                .build(),
        )
        val client = DefaultCaddyDirectoryClient(
            OkHttpClient(),
            DefaultDirectoryJsonParser(),
            dispatchers,
        )

        val result = client.listDirectory(
            "http://media.example/middle/",
            server.url("/middle/").toString(),
        )

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.HttpFailure)
        assertEquals(403, (error as AppError.HttpFailure).statusCode)
    }

    @Test
    fun `无效请求 URL 映射为网络错误而不抛异常`() = runTest {
        val client = DefaultCaddyDirectoryClient(
            OkHttpClient(),
            DefaultDirectoryJsonParser(),
            dispatchers,
        )

        val result = client.listDirectory(
            "http://media.example/middle/",
            "not-a-url",
        )

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NetworkFailure)
    }
}
