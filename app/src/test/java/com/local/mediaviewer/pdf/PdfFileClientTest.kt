package com.local.mediaviewer.pdf

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DispatcherProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfFileClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun `200 响应流式写入实际目标文件`() = runTest {
        val body = "%PDF-1.4\\nactual PDF bytes".encodeToByteArray()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Length", body.size)
                .body("%PDF-1.4\\nactual PDF bytes")
                .build(),
        )
        val destination = File(temporaryFolder.root, "download.part")
        val client = DefaultPdfFileClient(
            client = OkHttpClient(),
            dispatchers = dispatchers,
        )

        val result = client.download(server.url("/books/book.pdf").toString(), destination)

        assertEquals(AppResult.Success(body.size.toLong()), result)
        assertTrue(destination.isFile)
        assertEquals("%PDF-1.4\\nactual PDF bytes", destination.readText())
        assertEquals("/books/book.pdf", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `HTTP 404 映射为 HttpFailure 且不创建目标文件`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        val destination = File(temporaryFolder.root, "missing.part")
        val client = DefaultPdfFileClient(
            client = OkHttpClient(),
            dispatchers = dispatchers,
        )

        val result = client.download(server.url("/missing.pdf").toString(), destination)

        assertEquals(AppError.HttpFailure(404), (result as AppResult.Failure).error)
        assertFalse(destination.exists())
    }

    @Test
    fun `Content-Length 超出实际可用空间时返回缓存空间不足`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Length", Long.MAX_VALUE)
                .build(),
        )
        val destination = File(temporaryFolder.root, "too-large.part")
        val client = DefaultPdfFileClient(
            client = OkHttpClient(),
            dispatchers = dispatchers,
        )

        val result = client.download(server.url("/huge.pdf").toString(), destination)

        assertEquals(
            AppError.PdfCacheSpaceInsufficient,
            (result as AppResult.Failure).error,
        )
        assertFalse(destination.exists())
    }
}
