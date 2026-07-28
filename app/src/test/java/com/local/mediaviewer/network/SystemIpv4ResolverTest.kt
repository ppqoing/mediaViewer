package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemIpv4ResolverTest {
    @Test
    fun `IPv4 字面地址不调用 DNS`() = runTest {
        val resolver = SystemIpv4Resolver(
            lookup = { error("DNS must not be called") },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = resolver.resolve("203.0.113.7")

        assertEquals(
            listOf("203.0.113.7"),
            (result as AppResult.Success<List<String>>).value,
        )
    }

    @Test
    fun `只保留 IPv4 并保持系统顺序`() = runTest {
        val resolver = SystemIpv4Resolver(
            lookup = {
                arrayOf(
                    InetAddress.getByAddress(ByteArray(16) { 1 }) as Inet6Address,
                    InetAddress.getByAddress(byteArrayOf(10, 0, 0, 8)) as Inet4Address,
                    InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)) as Inet4Address,
                )
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = resolver.resolve("media.example")

        assertEquals(
            listOf("10.0.0.8", "8.8.8.8"),
            (result as AppResult.Success<List<String>>).value,
        )
    }

    @Test
    fun `仅 IPv6 返回未解析到 IPv4`() = runTest {
        val resolver = SystemIpv4Resolver(
            lookup = { arrayOf(InetAddress.getByAddress(ByteArray(16) { 1 })) },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = resolver.resolve("v6.example")

        assertEquals(
            AppError.NoIpv4Address,
            (result as AppResult.Failure).error,
        )
    }

    @Test
    fun `解析异常返回中文 DNS 错误`() = runTest {
        val missing = SystemIpv4Resolver(
            lookup = { throw UnknownHostException("missing") },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val forbidden = SystemIpv4Resolver(
            lookup = { throw SecurityException("forbidden") },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val missingResult = missing.resolve("missing.example")
        val forbiddenResult = forbidden.resolve("forbidden.example")

        assertTrue(missingResult is AppResult.Failure)
        assertEquals(
            "DNS 解析失败：UnknownHostException",
            (missingResult as AppResult.Failure).error.userMessage,
        )
        assertEquals(
            "DNS 解析失败：SecurityException",
            (forbiddenResult as AppResult.Failure).error.userMessage,
        )
    }
}
