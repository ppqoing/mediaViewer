# IPv4 解析与连接探测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 IPv4 字面地址或 DNS A 记录解析为有序候选，并选出首个同时提供两个合法 Caddy 根目录的会话 IPv4。

**Architecture:** `SystemIpv4Resolver` 隔离阻塞的系统 DNS；`DefaultConnectionProbe` 负责候选顺序和两根目录规则；`OkHttpDirectoryProbeTransport` 负责 3/5 秒 HTTP。成功结果同时携带逻辑服务器地址和仅供当前会话使用的 IPv4 请求地址。

**Tech Stack:** `InetAddress`、OkHttp 5.3.0、Coroutines、MockWebServer。

## Global Constraints

- IPv4 字面地址直接返回；DNS 只保留 `Inet4Address`，忽略 IPv6。
- 保留系统 A 记录顺序，不区分公网与私网地址。
- 每个候选依次请求 `/middle/` 与 `/pik/`。
- 两个请求都必须是 HTTP 200 且可解析为 Caddy JSON。
- 首个完全成功的候选立即结束探测。
- 单次请求连接超时 3 秒、读取超时 5 秒。
- 设置和稳定媒体键保留逻辑主机名，不用选中的 IPv4 覆盖。

---

### Task 4: DNS、会话端点与双根探测

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/core/AppError.kt`
- Create: `app/src/main/java/com/local/mediaviewer/model/SessionEndpoint.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/Ipv4Resolver.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/SystemIpv4Resolver.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/ConnectionProbe.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/OkHttpConnectionProbe.kt`
- Test: `app/src/test/java/com/local/mediaviewer/network/SystemIpv4ResolverTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/model/SessionEndpointTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/network/ConnectionProbeTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/network/ConnectionProbeHttpTest.kt`

**Interfaces:**

- Consumes:

```kotlin
data class ValidatedServerUrl(
    val logicalBaseUrl: String,
    val host: String,
    val port: Int,
    val isIpv4Literal: Boolean,
)

interface DirectoryJsonParser {
    fun parse(
        json: String,
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}
```

- Produces:

```kotlin
data class SessionEndpoint(
    val logicalBaseUrl: String,
    val requestBaseUrl: String,
    val ipv4: String,
) {
    fun requestUrlFor(logicalUrl: String): String
}

interface Ipv4Resolver {
    suspend fun resolve(host: String): AppResult<List<String>>
}

data class ConnectionTestResult(
    val server: ValidatedServerUrl,
    val resolvedIpv4s: List<String>,
    val endpoint: SessionEndpoint,
)

interface ConnectionProbe {
    suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ): AppResult<ConnectionTestResult>
}
```

- [ ] **Step 1: 写 IPv4 解析失败测试**

`SystemIpv4ResolverTest.kt`：

```kotlin
package com.local.mediaviewer.network

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
        assertEquals(
            listOf("203.0.113.7"),
            (resolver.resolve("203.0.113.7") as AppResult.Success).value,
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
        assertEquals(
            listOf("10.0.0.8", "8.8.8.8"),
            (resolver.resolve("media.example") as AppResult.Success).value,
        )
    }

    @Test
    fun `仅 IPv6 和解析异常返回中文领域错误`() = runTest {
        val onlyIpv6 = SystemIpv4Resolver(
            lookup = { arrayOf(InetAddress.getByAddress(ByteArray(16) { 1 })) },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val failed = SystemIpv4Resolver(
            lookup = { throw UnknownHostException("missing") },
            ioDispatcher = Dispatchers.Unconfined,
        )
        assertTrue(onlyIpv6.resolve("v6.example") is AppResult.Failure)
        assertTrue(failed.resolve("missing.example") is AppResult.Failure)
    }
}
```

- [ ] **Step 2: 运行解析测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.SystemIpv4ResolverTest'
```

Expected:

```text
Kotlin compilation fails because SystemIpv4Resolver is unresolved
```

- [ ] **Step 3: 实现系统 IPv4 解析器**

`Ipv4Resolver.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppResult

interface Ipv4Resolver {
    suspend fun resolve(host: String): AppResult<List<String>>
}
```

`SystemIpv4Resolver.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.settings.ServerUrlValidator
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemIpv4Resolver(
    private val lookup: (String) -> Array<InetAddress> = InetAddress::getAllByName,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Ipv4Resolver {
    override suspend fun resolve(host: String): AppResult<List<String>> {
        ServerUrlValidator.parseIpv4(host)?.let {
            return AppResult.Success(listOf(it))
        }
        return withContext(ioDispatcher) {
            try {
                val addresses = lookup(host)
                    .filterIsInstance<Inet4Address>()
                    .map { it.hostAddress }
                if (addresses.isEmpty()) {
                    AppResult.Failure(AppError.NoIpv4Address)
                } else {
                    AppResult.Success(addresses)
                }
            } catch (error: UnknownHostException) {
                AppResult.Failure(AppError.DnsFailure(error.javaClass.simpleName))
            } catch (error: SecurityException) {
                AppResult.Failure(AppError.DnsFailure(error.javaClass.simpleName))
            }
        }
    }
}
```

- [ ] **Step 4: 写会话 URL 重映射失败测试**

`SessionEndpointTest.kt`：

```kotlin
package com.local.mediaviewer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEndpointTest {
    @Test
    fun `仅替换 authority 并保持已经编码的路径`() {
        val endpoint = SessionEndpoint(
            logicalBaseUrl = "http://media.example:8080",
            requestBaseUrl = "http://203.0.113.9:8080",
            ipv4 = "203.0.113.9",
        )

        assertEquals(
            "http://203.0.113.9:8080/middle/%E5%BD%B1%E7%89%87%20%281%29.mp4",
            endpoint.requestUrlFor(
                "http://media.example:8080/middle/%E5%BD%B1%E7%89%87%20%281%29.mp4",
            ),
        )
    }
}
```

- [ ] **Step 5: 实现会话端点**

`SessionEndpoint.kt`：

```kotlin
package com.local.mediaviewer.model

import okhttp3.HttpUrl.Companion.toHttpUrl

data class SessionEndpoint(
    val logicalBaseUrl: String,
    val requestBaseUrl: String,
    val ipv4: String,
) {
    fun requestUrlFor(logicalUrl: String): String {
        val logicalBase = logicalBaseUrl.toHttpUrl()
        val logical = logicalUrl.toHttpUrl()
        require(
            logical.scheme == logicalBase.scheme &&
                logical.host == logicalBase.host &&
                logical.port == logicalBase.port,
        ) { "逻辑媒体 URL 不属于当前服务器" }
        val requestBase = requestBaseUrl.toHttpUrl()
        return requestBase.newBuilder()
            .encodedPath(logical.encodedPath)
            .encodedQuery(logical.encodedQuery)
            .fragment(null)
            .build()
            .toString()
    }
}
```

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.SystemIpv4ResolverTest' `
  --tests 'com.local.mediaviewer.model.SessionEndpointTest'
```

Expected:

```text
Both test classes pass
```

- [ ] **Step 6: 写候选顺序和双根规则失败测试**

`ConnectionProbeTest.kt`：

```kotlin
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
        val success = result as AppResult.Success

        assertEquals("203.0.113.9", success.value.endpoint.ipv4)
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
    fun `无候选与全部失败均返回明确错误`() = runTest {
        val probe = DefaultConnectionProbe(
            DirectoryProbeTransport { AppResult.Failure(AppError.NetworkFailure("timeout")) },
            DefaultDirectoryJsonParser(),
        )
        assertTrue(probe.probe(server, emptyList()) is AppResult.Failure)
        assertTrue(probe.probe(server, listOf("192.0.2.1")) is AppResult.Failure)
    }
}
```

- [ ] **Step 7: 运行探测测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.ConnectionProbeTest'
```

Expected:

```text
Kotlin compilation fails because ConnectionProbe types are unresolved
```

- [ ] **Step 8: 实现候选探测与错误汇总**

在 `AppError.kt` 中追加：

```kotlin
data class ProbeFailure(
    val resolvedIpv4s: List<String>,
    val lastError: String,
) : AppError {
    override val userMessage =
        "所有 IPv4 均连接失败：$lastError"
}
```

`ConnectionProbe.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl

data class ConnectionTestResult(
    val server: ValidatedServerUrl,
    val resolvedIpv4s: List<String>,
    val endpoint: SessionEndpoint,
)

interface ConnectionProbe {
    suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ): AppResult<ConnectionTestResult>
}

fun interface DirectoryProbeTransport {
    suspend fun get(url: String): AppResult<String>
}
```

`OkHttpConnectionProbe.kt` 中先实现候选协调：

```kotlin
class DefaultConnectionProbe(
    private val transport: DirectoryProbeTransport,
    private val parser: DirectoryJsonParser,
) : ConnectionProbe {
    override suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ): AppResult<ConnectionTestResult> {
        if (ipv4Candidates.isEmpty()) {
            return AppResult.Failure(AppError.NoIpv4Address)
        }
        var lastError: AppError = AppError.NetworkFailure("没有完成探测")
        for (ipv4 in ipv4Candidates) {
            val logicalBase = server.logicalBaseUrl.toHttpUrl()
            val requestBase = logicalBase.newBuilder().host(ipv4).build()
            val endpoint = SessionEndpoint(
                logicalBaseUrl = logicalBase.toString().removeSuffix("/"),
                requestBaseUrl = requestBase.toString().removeSuffix("/"),
                ipv4 = ipv4,
            )
            var candidateSucceeded = true
            for (root in RootShare.entries) {
                val logicalRoot = logicalBase.resolve(root.path)!!.toString()
                val requestRoot = requestBase.resolve(root.path)!!.toString()
                when (val response = transport.get(requestRoot)) {
                    is AppResult.Failure -> {
                        lastError = response.error
                        candidateSucceeded = false
                        break
                    }
                    is AppResult.Success -> when (
                        val parsed = parser.parse(response.value, logicalRoot, requestRoot)
                    ) {
                        is AppResult.Failure -> {
                            lastError = parsed.error
                            candidateSucceeded = false
                            break
                        }
                        is AppResult.Success -> Unit
                    }
                }
            }
            if (candidateSucceeded) {
                return AppResult.Success(
                    ConnectionTestResult(server, ipv4Candidates, endpoint),
                )
            }
        }
        return AppResult.Failure(
            AppError.ProbeFailure(ipv4Candidates, lastError.userMessage),
        )
    }
}
```

在同一文件顶部导入：

```kotlin
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
```

- [ ] **Step 9: 写真实 HTTP 传输失败测试**

`ConnectionProbeHttpTest.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ValidatedServerUrl
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

    @Before fun start() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @After fun stop() = mockServer.close()

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
        val transport = OkHttpDirectoryProbeTransport()
        val probe = DefaultConnectionProbe(transport, DefaultDirectoryJsonParser())
        val server = ValidatedServerUrl(
            logicalBaseUrl = "http://127.0.0.1:${mockServer.port}",
            host = "127.0.0.1",
            port = mockServer.port,
            isIpv4Literal = true,
        )

        val result = probe.probe(server, listOf("127.0.0.1"))

        assertTrue(result is AppResult.Success)
        assertEquals("/middle/", mockServer.takeRequest().url.encodedPath)
        assertEquals("/pik/", mockServer.takeRequest().url.encodedPath)
    }
}
```

- [ ] **Step 10: 实现 3/5 秒 OkHttp 探测传输**

在 `OkHttpConnectionProbe.kt` 追加：

```kotlin
class OkHttpDirectoryProbeTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DirectoryProbeTransport {
    override suspend fun get(url: String): AppResult<String> =
        withContext(ioDispatcher) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppResult.Failure(AppError.HttpFailure(response.code))
                    } else {
                        AppResult.Success(response.body.string())
                    }
                }
            } catch (error: IOException) {
                AppResult.Failure(
                    AppError.NetworkFailure(error.javaClass.simpleName),
                )
            }
        }
}
```

追加导入：

```kotlin
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
```

- [ ] **Step 11: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.SystemIpv4ResolverTest' `
  --tests 'com.local.mediaviewer.model.SessionEndpointTest' `
  --tests 'com.local.mediaviewer.network.ConnectionProbeTest' `
  --tests 'com.local.mediaviewer.network.ConnectionProbeHttpTest'
.\gradlew.bat lintDebug
```

Expected:

```text
All IPv4 and probe tests pass
Lint reports 0 errors
```

- [ ] **Step 12: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/core/AppError.kt `
  app/src/main/java/com/local/mediaviewer/model/SessionEndpoint.kt `
  app/src/main/java/com/local/mediaviewer/network `
  app/src/test/java/com/local/mediaviewer/model `
  app/src/test/java/com/local/mediaviewer/network
git commit -m "feat: resolve and probe IPv4 endpoints"
```
