# 服务器会话协调 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已保存设置、URL 校验、DNS 和探测组合成一个进程级服务器会话状态机。

**Architecture:** `DefaultServerSessionManager` 串行执行连接操作，并通过 `StateFlow` 暴露 Connecting、Connected、Failed。候选设置只有测试成功后才能保存；请求失败刷新方法重新读取逻辑地址、重新解析 DNS 并重新探测，但“只重试一次”由每个调用方控制。

**Tech Stack:** Kotlin Coroutines、StateFlow、Mutex、DataStore Repository。

## Global Constraints

- 应用启动时解析并探测已保存逻辑地址。
- 保存设置前必须同时验证 `/middle/` 与 `/pik/`。
- 保存原始域名或 IPv4，不用选中的会话 IPv4 覆盖逻辑地址。
- 成功后可保存最近成功 IPv4 仅供状态显示。
- 请求首次失败时重新解析和探测；之后失败交给用户手动重试。
- 所有候选失败时状态包含解析出的 IPv4 与最后错误。

---

### Task 5: 会话状态与设置事务

**Files:**

- Create: `app/src/main/java/com/local/mediaviewer/session/ServerSessionState.kt`
- Create: `app/src/main/java/com/local/mediaviewer/session/ServerSessionManager.kt`
- Test: `app/src/test/java/com/local/mediaviewer/session/ServerSessionManagerTest.kt`

**Interfaces:**

- Consumes:

```kotlin
interface ServerSettingsRepository {
    val config: Flow<ServerConfig>
    suspend fun current(): ServerConfig
    suspend fun save(config: ServerConfig)
}

interface Ipv4Resolver {
    suspend fun resolve(host: String): AppResult<List<String>>
}

interface ConnectionProbe {
    suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ): AppResult<ConnectionTestResult>
}
```

- Produces:

```kotlin
sealed interface ServerSessionState {
    data object Connecting : ServerSessionState
    data class Connected(
        val endpoint: SessionEndpoint,
        val resolvedIpv4s: List<String>,
    ) : ServerSessionState
    data class Failed(
        val error: AppError,
        val resolvedIpv4s: List<String>,
    ) : ServerSessionState
}

interface ServerSessionManager {
    val state: StateFlow<ServerSessionState>
    suspend fun connectSaved()
    suspend fun testCandidate(input: String): AppResult<ConnectionTestResult>
    suspend fun saveCandidate(result: ConnectionTestResult)
    suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint>
}
```

- [ ] **Step 1: 写状态机失败测试**

`ServerSessionManagerTest.kt`：

```kotlin
package com.local.mediaviewer.session

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl
import com.local.mediaviewer.network.ConnectionProbe
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.network.Ipv4Resolver
import com.local.mediaviewer.settings.ServerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSessionManagerTest {
    @Test
    fun `启动连接保存最近成功 IPv4 但保留逻辑域名`() = runTest {
        val settings = FakeSettings(
            ServerConfig("http://media.example:8080"),
        )
        val resolver = QueueResolver(
            mutableListOf(AppResult.Success(listOf("10.0.0.8", "203.0.113.7"))),
        )
        val probe = FakeProbe { server, candidates ->
            AppResult.Success(
                ConnectionTestResult(
                    server = server,
                    resolvedIpv4s = candidates,
                    endpoint = SessionEndpoint(
                        server.logicalBaseUrl,
                        "http://203.0.113.7:8080",
                        "203.0.113.7",
                    ),
                ),
            )
        }
        val manager = DefaultServerSessionManager(settings, resolver, probe)

        manager.connectSaved()

        val state = manager.state.value as ServerSessionState.Connected
        assertEquals("203.0.113.7", state.endpoint.ipv4)
        assertEquals("http://media.example:8080", settings.value.logicalBaseUrl)
        assertEquals("203.0.113.7", settings.value.lastSuccessfulIpv4)
    }

    @Test
    fun `候选设置测试不写入而显式保存才提交`() = runTest {
        val settings = FakeSettings(ServerConfig())
        val resolver = QueueResolver(
            mutableListOf(AppResult.Success(listOf("198.51.100.4"))),
        )
        val probe = successfulProbe("http://198.51.100.4:8090", "198.51.100.4")
        val manager = DefaultServerSessionManager(settings, resolver, probe)

        val tested = manager.testCandidate("http://public.example:8090")
        assertEquals(ServerConfig.DEFAULT_SERVER_URL, settings.value.logicalBaseUrl)

        manager.saveCandidate((tested as AppResult.Success).value)
        assertEquals("http://public.example:8090", settings.value.logicalBaseUrl)
        assertEquals("198.51.100.4", settings.value.lastSuccessfulIpv4)
    }

    @Test
    fun `请求失败刷新时再次执行 DNS`() = runTest {
        val settings = FakeSettings(ServerConfig("http://ddns.example:8080"))
        val resolver = QueueResolver(
            mutableListOf(
                AppResult.Success(listOf("192.0.2.1")),
                AppResult.Success(listOf("192.0.2.2")),
            ),
        )
        val probe = FakeProbe { server, candidates ->
            val ip = candidates.single()
            AppResult.Success(
                ConnectionTestResult(
                    server,
                    candidates,
                    SessionEndpoint(server.logicalBaseUrl, "http://$ip:8080", ip),
                ),
            )
        }
        val manager = DefaultServerSessionManager(settings, resolver, probe)

        manager.connectSaved()
        val refreshed = manager.refreshAfterRequestFailure()

        assertEquals(
            "192.0.2.2",
            (refreshed as AppResult.Success).value.ipv4,
        )
        assertEquals(2, resolver.calls)
    }

    @Test
    fun `解析失败进入 Failed 且不修改设置`() = runTest {
        val initial = ServerConfig("http://missing.example:8080")
        val settings = FakeSettings(initial)
        val manager = DefaultServerSessionManager(
            settings,
            QueueResolver(mutableListOf(AppResult.Failure(AppError.NoIpv4Address))),
            successfulProbe("http://192.0.2.1:8080", "192.0.2.1"),
        )

        manager.connectSaved()

        assertTrue(manager.state.value is ServerSessionState.Failed)
        assertEquals(initial, settings.value)
    }

    private fun successfulProbe(requestBase: String, ip: String) =
        FakeProbe { server, candidates ->
            AppResult.Success(
                ConnectionTestResult(
                    server,
                    candidates,
                    SessionEndpoint(server.logicalBaseUrl, requestBase, ip),
                ),
            )
        }
}

private class FakeSettings(initial: ServerConfig) : ServerSettingsRepository {
    private val flow = MutableStateFlow(initial)
    var value: ServerConfig
        get() = flow.value
        private set(newValue) { flow.value = newValue }
    override val config: Flow<ServerConfig> = flow
    override suspend fun current() = value
    override suspend fun save(config: ServerConfig) { value = config }
}

private class QueueResolver(
    private val results: MutableList<AppResult<List<String>>>,
) : Ipv4Resolver {
    var calls = 0
    override suspend fun resolve(host: String): AppResult<List<String>> {
        calls += 1
        return results.removeAt(0)
    }
}

private fun interface FakeProbeBlock {
    suspend fun invoke(
        server: ValidatedServerUrl,
        candidates: List<String>,
    ): AppResult<ConnectionTestResult>
}

private class FakeProbe(
    private val block: FakeProbeBlock,
) : ConnectionProbe {
    override suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ) = block.invoke(server, ipv4Candidates)
}
```

- [ ] **Step 2: 运行测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.session.ServerSessionManagerTest'
```

Expected:

```text
Kotlin compilation fails because session types are unresolved
```

- [ ] **Step 3: 实现不可变会话状态**

`ServerSessionState.kt`：

```kotlin
package com.local.mediaviewer.session

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.model.SessionEndpoint

sealed interface ServerSessionState {
    data object Connecting : ServerSessionState

    data class Connected(
        val endpoint: SessionEndpoint,
        val resolvedIpv4s: List<String>,
    ) : ServerSessionState

    data class Failed(
        val error: AppError,
        val resolvedIpv4s: List<String>,
    ) : ServerSessionState
}
```

- [ ] **Step 4: 实现串行会话管理器**

`ServerSessionManager.kt`：

```kotlin
package com.local.mediaviewer.session

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl
import com.local.mediaviewer.network.ConnectionProbe
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.network.Ipv4Resolver
import com.local.mediaviewer.settings.ServerSettingsRepository
import com.local.mediaviewer.settings.ServerUrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ServerSessionManager {
    val state: StateFlow<ServerSessionState>
    suspend fun connectSaved()
    suspend fun testCandidate(input: String): AppResult<ConnectionTestResult>
    suspend fun saveCandidate(result: ConnectionTestResult)
    suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint>
}

class DefaultServerSessionManager(
    private val settings: ServerSettingsRepository,
    private val resolver: Ipv4Resolver,
    private val probe: ConnectionProbe,
) : ServerSessionManager {
    private val mutex = Mutex()
    private val mutableState =
        MutableStateFlow<ServerSessionState>(ServerSessionState.Connecting)
    override val state: StateFlow<ServerSessionState> = mutableState.asStateFlow()

    override suspend fun connectSaved() {
        mutex.withLock {
            mutableState.value = ServerSessionState.Connecting
            val config = settings.current()
            when (val result = connect(config.logicalBaseUrl)) {
                is AppResult.Success -> applySuccess(result.value, persist = true)
                is AppResult.Failure -> applyFailure(result.error)
            }
        }
    }

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> = mutex.withLock {
        connect(input)
    }

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        mutex.withLock {
            applySuccess(result, persist = true)
        }
    }

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        mutex.withLock {
            mutableState.value = ServerSessionState.Connecting
            val result = connect(settings.current().logicalBaseUrl)
            when (result) {
                is AppResult.Success -> {
                    applySuccess(result.value, persist = true)
                    AppResult.Success(result.value.endpoint)
                }
                is AppResult.Failure -> {
                    applyFailure(result.error)
                    result
                }
            }
        }

    private suspend fun connect(
        input: String,
    ): AppResult<ConnectionTestResult> {
        val validated = when (val value = ServerUrlValidator.validate(input)) {
            is AppResult.Success -> value.value
            is AppResult.Failure -> return value
        }
        val addresses = when (val value = resolver.resolve(validated.host)) {
            is AppResult.Success -> value.value
            is AppResult.Failure -> return value
        }
        return probe.probe(validated, addresses)
    }

    private suspend fun applySuccess(
        result: ConnectionTestResult,
        persist: Boolean,
    ) {
        if (persist) {
            settings.save(
                ServerConfig(
                    logicalBaseUrl = result.server.logicalBaseUrl,
                    lastSuccessfulIpv4 = result.endpoint.ipv4,
                ),
            )
        }
        mutableState.value = ServerSessionState.Connected(
            endpoint = result.endpoint,
            resolvedIpv4s = result.resolvedIpv4s,
        )
    }

    private fun applyFailure(error: AppError) {
        val candidates = (error as? AppError.ProbeFailure)
            ?.resolvedIpv4s
            .orEmpty()
        mutableState.value = ServerSessionState.Failed(error, candidates)
    }
}
```

- [ ] **Step 5: 运行状态机测试并确认通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.session.ServerSessionManagerTest'
```

Expected:

```text
All four session tests pass
```

- [ ] **Step 6: 增加并发回归测试**

在 `ServerSessionManagerTest.kt` 增加：

```kotlin
@Test
fun `并发连接由 Mutex 串行化且最终状态完整`() = runTest {
    val settings = FakeSettings(ServerConfig())
    val resolver = QueueResolver(
        mutableListOf(
            AppResult.Success(listOf("192.0.2.1")),
            AppResult.Success(listOf("192.0.2.2")),
        ),
    )
    val manager = DefaultServerSessionManager(
        settings,
        resolver,
        FakeProbe { server, candidates ->
            val ip = candidates.single()
            AppResult.Success(
                ConnectionTestResult(
                    server,
                    candidates,
                    SessionEndpoint(server.logicalBaseUrl, "http://$ip:8080", ip),
                ),
            )
        },
    )

    val first = async { manager.connectSaved() }
    val second = async { manager.connectSaved() }
    first.await()
    second.await()

    assertTrue(manager.state.value is ServerSessionState.Connected)
    assertEquals(2, resolver.calls)
}
```

并添加导入：

```kotlin
import kotlinx.coroutines.async
```

- [ ] **Step 7: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.session.ServerSessionManagerTest'
.\gradlew.bat lintDebug
```

Expected:

```text
All session tests pass
Lint reports 0 errors
```

- [ ] **Step 8: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/session `
  app/src/test/java/com/local/mediaviewer/session
git commit -m "feat: coordinate server connection sessions"
```
