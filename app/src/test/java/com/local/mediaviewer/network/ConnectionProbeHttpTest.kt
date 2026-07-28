package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ValidatedServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionProbeHttpTest {
    private lateinit var mockServer: MockWebServer

    @Before
    fun start() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun stop() = mockServer.close()

    @Test
    fun `两个根均为 JSON 200 时探测成功`() = runTest {
        repeat(2) {
            mockServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("[]")
                    .build(),
            )
        }
        val transport = OkHttpDirectoryProbeTransport(
            ioDispatcher = Dispatchers.Unconfined,
        )
        val probe = DefaultConnectionProbe(transport, DefaultDirectoryJsonParser())
        val server = ValidatedServerUrl(
            logicalBaseUrl = "http://127.0.0.1:${mockServer.port}",
            host = "127.0.0.1",
            port = mockServer.port,
            isIpv4Literal = true,
        )

        val result = probe.probe(server, listOf("127.0.0.1"))

        assertTrue(result is AppResult.Success<ConnectionTestResult>)
        val middle = mockServer.takeRequest()
        val pik = mockServer.takeRequest()
        assertEquals("/middle/", middle.url.encodedPath)
        assertEquals("/pik/", pik.url.encodedPath)
        assertEquals("application/json", middle.headers["Accept"])
        assertEquals("application/json", pik.headers["Accept"])
    }

    @Test
    fun `HTTP 失败保留状态码`() = runTest {
        mockServer.enqueue(MockResponse.Builder().code(503).build())
        val transport = OkHttpDirectoryProbeTransport(
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = transport.get(mockServer.url("/middle/").toString())

        assertEquals(
            AppError.HttpFailure(503),
            (result as AppResult.Failure).error,
        )
    }

    @Test
    fun `无效请求 URL 映射为网络错误而不抛异常`() = runTest {
        val transport = OkHttpDirectoryProbeTransport(
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = transport.get("not-a-url")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.NetworkFailure)
    }
}
