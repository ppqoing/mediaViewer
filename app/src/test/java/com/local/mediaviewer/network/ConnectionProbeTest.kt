package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ValidatedServerUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证共享发现探测在多 IPv4 候选间的选择和错误汇总。
 */
class ConnectionProbeTest {
    private val server = ValidatedServerUrl(
        logicalBaseUrl = "http://media.example:8080",
        host = "media.example",
        port = 8080,
        isIpv4Literal = false,
    )

    @Test
    fun `第一个候选不支持发现接口时继续第二个候选`() = runTest {
        val requests = mutableListOf<String>()
        val transport = ShareDiscoveryTransport { url ->
            requests += url
            if (url.startsWith("http://10.0.0.1")) {
                AppResult.Failure(AppError.DiscoveryNotSupported)
            } else {
                AppResult.Success(discoveryJson())
            }
        }
        val probe = DefaultConnectionProbe(
            transport,
            DefaultShareDiscoveryParser(),
        )

        val result = probe.probe(server, listOf("10.0.0.1", "203.0.113.9"))
        val success = result as AppResult.Success<ConnectionTestResult>

        assertEquals("203.0.113.9", success.value.endpoint.ipv4)
        assertEquals(
            listOf("10.0.0.1", "203.0.113.9"),
            success.value.resolvedIpv4s,
        )
        assertEquals(listOf("共享一"), success.value.shares.map { it.displayName })
        assertEquals(
            listOf(
                "http://10.0.0.1:8080/.rangeshelf/shares",
                "http://203.0.113.9:8080/.rangeshelf/shares",
            ),
            requests,
        )
    }

    @Test
    fun `无候选返回未解析到 IPv4`() = runTest {
        val probe = DefaultConnectionProbe(
            ShareDiscoveryTransport {
                error("无候选时不应发出请求")
            },
            DefaultShareDiscoveryParser(),
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
            ShareDiscoveryTransport {
                AppResult.Failure(AppError.NetworkFailure("timeout"))
            },
            DefaultShareDiscoveryParser(),
        )

        val result = probe.probe(server, listOf("192.0.2.1", "192.0.2.2"))
        val error = (result as AppResult.Failure).error

        assertTrue(error is AppError.ProbeFailure)
        error as AppError.ProbeFailure
        assertEquals(listOf("192.0.2.1", "192.0.2.2"), error.resolvedIpv4s)
        assertEquals("网络连接失败：timeout", error.lastError)
    }

    @Test
    fun `非法候选不崩溃并继续探测合法 IPv4`() = runTest {
        val requests = mutableListOf<String>()
        val probe = DefaultConnectionProbe(
            ShareDiscoveryTransport { url ->
                requests += url
                AppResult.Success(discoveryJson())
            },
            DefaultShareDiscoveryParser(),
        )

        val result = probe.probe(server, listOf("not-an-ip", "203.0.113.9"))

        assertEquals(
            "203.0.113.9",
            (result as AppResult.Success<ConnectionTestResult>).value.endpoint.ipv4,
        )
        assertEquals(
            listOf("http://203.0.113.9:8080/.rangeshelf/shares"),
            requests,
        )
    }
}

/**
 * 生成测试使用的最小合法共享发现文档。
 *
 * @return 包含一个匿名共享的版本 1 JSON。
 */
internal fun discoveryJson() =
    """
    {
      "schemaVersion": 1,
      "shares": [{
        "id": "4f01061d-9b75-4f7d-96db-49c801e96188",
        "displayName": "共享一",
        "urlPrefix": "共享一",
        "directoryBrowsing": true,
        "authenticationMode": "anonymous"
      }]
    }
    """.trimIndent()
