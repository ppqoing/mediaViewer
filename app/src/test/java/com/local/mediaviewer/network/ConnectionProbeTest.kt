package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ValidatedServerUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProbeTest {
    private val server = ValidatedServerUrl(
        logicalBaseUrl = "http://media.example:8080",
        host = "media.example",
        port = 8080,
        isIpv4Literal = false,
    )

    @Test
    fun `第一个候选缺一个根目录时继续第二个候选`() = runTest {
        val requests = mutableListOf<String>()
        val transport = DirectoryProbeTransport { url ->
            requests += url
            when {
                url.startsWith("http://10.0.0.1") && url.endsWith("/middle/") ->
                    AppResult.Success("[]")
                url.startsWith("http://10.0.0.1") && url.endsWith("/pik/") ->
                    AppResult.Failure(AppError.HttpFailure(404))
                url.startsWith("http://203.0.113.9") ->
                    AppResult.Success("[]")
                else -> error(url)
            }
        }
        val probe = DefaultConnectionProbe(transport, DefaultDirectoryJsonParser())

        val result = probe.probe(server, listOf("10.0.0.1", "203.0.113.9"))
        val success = result as AppResult.Success<ConnectionTestResult>

        assertEquals("203.0.113.9", success.value.endpoint.ipv4)
        assertEquals(
            listOf("10.0.0.1", "203.0.113.9"),
            success.value.resolvedIpv4s,
        )
        assertEquals(
            listOf(
                "http://10.0.0.1:8080/middle/",
                "http://10.0.0.1:8080/pik/",
                "http://203.0.113.9:8080/middle/",
                "http://203.0.113.9:8080/pik/",
            ),
            requests,
        )
    }

    @Test
    fun `无候选返回未解析到 IPv4`() = runTest {
        val probe = DefaultConnectionProbe(
            DirectoryProbeTransport {
                error("无候选时不应发出请求")
            },
            DefaultDirectoryJsonParser(),
        )

        val result = probe.probe(server, emptyList())

        assertEquals(
            AppError.NoIpv4Address,
            (result as AppResult.Failure).error,
        )
    }

    @Test
    fun `全部失败返回解析结果和最后错误`() = runTest {
        val probe = DefaultConnectionProbe(
            DirectoryProbeTransport {
                AppResult.Failure(AppError.NetworkFailure("timeout"))
            },
            DefaultDirectoryJsonParser(),
        )

        val result = probe.probe(server, listOf("192.0.2.1", "192.0.2.2"))
        val error = (result as AppResult.Failure).error

        assertTrue(error is AppError.ProbeFailure)
        error as AppError.ProbeFailure
        assertEquals(listOf("192.0.2.1", "192.0.2.2"), error.resolvedIpv4s)
        assertEquals("网络连接失败：timeout", error.lastError)
        assertEquals(
            "所有 IPv4 均连接失败：网络连接失败：timeout",
            error.userMessage,
        )
    }

    @Test
    fun `非法候选不崩溃并继续探测合法 IPv4`() = runTest {
        val requests = mutableListOf<String>()
        val probe = DefaultConnectionProbe(
            DirectoryProbeTransport { url ->
                requests += url
                AppResult.Success("[]")
            },
            DefaultDirectoryJsonParser(),
        )

        val result = probe.probe(server, listOf("not-an-ip", "203.0.113.9"))

        assertEquals(
            "203.0.113.9",
            (result as AppResult.Success<ConnectionTestResult>).value.endpoint.ipv4,
        )
        assertEquals(
            listOf(
                "http://203.0.113.9:8080/middle/",
                "http://203.0.113.9:8080/pik/",
            ),
            requests,
        )
    }
}
