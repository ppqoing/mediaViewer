package com.local.mediaviewer.settings

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ValidatedServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlValidatorTest {
    @Test
    fun `接受默认私网 IPv4`() {
        val result = ServerUrlValidator.validate(" http://192.168.1.17:8080/ ")
        val value = (result as AppResult.Success<ValidatedServerUrl>).value

        assertEquals("http://192.168.1.17:8080", value.logicalBaseUrl)
        assertEquals("192.168.1.17", value.host)
        assertEquals(8080, value.port)
        assertTrue(value.isIpv4Literal)
    }

    @Test
    fun `接受公网 IPv4 与 DNS A 记录主机名`() {
        val publicIp =
            (ServerUrlValidator.validate("http://8.8.8.8")
                as AppResult.Success<ValidatedServerUrl>).value
        val dns =
            (ServerUrlValidator.validate("http://Media.Example.COM:8090")
                as AppResult.Success<ValidatedServerUrl>).value

        assertEquals("http://8.8.8.8", publicIp.logicalBaseUrl)
        assertEquals(80, publicIp.port)
        assertTrue(publicIp.isIpv4Literal)
        assertEquals("http://media.example.com:8090", dns.logicalBaseUrl)
        assertEquals("media.example.com", dns.host)
        assertFalse(dns.isIpv4Literal)
    }

    @Test
    fun `拒绝设计之外的 URL 组成部分`() {
        val rejected = listOf(
            "https://example.com",
            "ftp://example.com",
            "http://user@example.com",
            "http://example.com/path",
            "http://example.com?x=1",
            "http://example.com#part",
            "example.com:8080",
            "http://999.1.1.1",
        )

        rejected.forEach { input ->
            assertTrue("$input should fail", ServerUrlValidator.validate(input) is AppResult.Failure)
        }
    }

    @Test
    fun `拒绝 IPv6 字面地址`() {
        val rejected = listOf(
            "http://[::1]",
            "http://[2001:db8::1]:8080",
        )

        rejected.forEach { input ->
            val result = ServerUrlValidator.validate(input)
            assertTrue("$input should fail", result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error.userMessage.contains("IPv4"))
        }
    }
}
