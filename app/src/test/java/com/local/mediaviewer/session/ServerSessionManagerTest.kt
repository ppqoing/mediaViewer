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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
            mutableListOf(
                AppResult.Success(listOf("10.0.0.8", "203.0.113.7")),
            ),
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
        assertEquals(
            listOf("10.0.0.8", "203.0.113.7"),
            state.resolvedIpv4s,
        )
        assertEquals("http://media.example:8080", settings.value.logicalBaseUrl)
        assertEquals("203.0.113.7", settings.value.lastSuccessfulIpv4)
    }

    @Test
    fun `候选设置测试不写入而显式保存才提交`() = runTest {
        val settings = FakeSettings(ServerConfig())
        val resolver = QueueResolver(
            mutableListOf(AppResult.Success(listOf("198.51.100.4"))),
        )
        val probe = successfulProbe(
            "http://198.51.100.4:8090",
            "198.51.100.4",
        )
        val manager = DefaultServerSessionManager(settings, resolver, probe)

        val tested = manager.testCandidate("http://public.example:8090")

        assertEquals(ServerConfig.DEFAULT_SERVER_URL, settings.value.logicalBaseUrl)
        assertEquals(0, settings.saveCalls)

        manager.saveCandidate(
            (tested as AppResult.Success<ConnectionTestResult>).value,
        )

        assertEquals("http://public.example:8090", settings.value.logicalBaseUrl)
        assertEquals("198.51.100.4", settings.value.lastSuccessfulIpv4)
        assertEquals(1, settings.saveCalls)
        assertTrue(manager.state.value is ServerSessionState.Connected)
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
                    SessionEndpoint(
                        server.logicalBaseUrl,
                        "http://$ip:8080",
                        ip,
                    ),
                ),
            )
        }
        val manager = DefaultServerSessionManager(settings, resolver, probe)

        manager.connectSaved()
        val refreshed = manager.refreshAfterRequestFailure()

        assertEquals(
            "192.0.2.2",
            (refreshed as AppResult.Success<SessionEndpoint>).value.ipv4,
        )
        assertEquals(2, resolver.calls)
    }

    @Test
    fun `解析失败进入 Failed 且不修改设置`() = runTest {
        val initial = ServerConfig("http://missing.example:8080")
        val settings = FakeSettings(initial)
        val manager = DefaultServerSessionManager(
            settings,
            QueueResolver(
                mutableListOf(AppResult.Failure(AppError.NoIpv4Address)),
            ),
            successfulProbe("http://192.0.2.1:8080", "192.0.2.1"),
        )

        manager.connectSaved()

        val state = manager.state.value as ServerSessionState.Failed
        assertEquals(AppError.NoIpv4Address, state.error)
        assertEquals(emptyList<String>(), state.resolvedIpv4s)
        assertEquals(initial, settings.value)
        assertEquals(0, settings.saveCalls)
    }

    @Test
    fun `探测失败状态保留已解析 IPv4`() = runTest {
        val candidates = listOf("192.0.2.10", "192.0.2.11")
        val error = AppError.ProbeFailure(
            resolvedIpv4s = candidates,
            lastError = "服务器返回 HTTP 404",
        )
        val settings = FakeSettings(ServerConfig("http://media.example:8080"))
        val manager = DefaultServerSessionManager(
            settings,
            QueueResolver(mutableListOf(AppResult.Success(candidates))),
            FakeProbe { _, _ -> AppResult.Failure(error) },
        )

        manager.connectSaved()

        val state = manager.state.value as ServerSessionState.Failed
        assertEquals(error, state.error)
        assertEquals(candidates, state.resolvedIpv4s)
        assertEquals(0, settings.saveCalls)
    }

    @Test
    fun `并发连接由 Mutex 串行化且最终状态完整`() = runTest {
        val settings = FakeSettings(ServerConfig())
        val resolver = YieldingQueueResolver(
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
                        SessionEndpoint(
                            server.logicalBaseUrl,
                            "http://$ip:8080",
                            ip,
                        ),
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
        assertEquals(1, resolver.maxActive)
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
        private set(newValue) {
            flow.value = newValue
        }
    var saveCalls = 0
        private set

    override val config: Flow<ServerConfig> = flow

    override suspend fun current(): ServerConfig = value

    override suspend fun save(config: ServerConfig) {
        saveCalls += 1
        value = config
    }
}

private class QueueResolver(
    private val results: MutableList<AppResult<List<String>>>,
) : Ipv4Resolver {
    var calls = 0
        private set

    override suspend fun resolve(host: String): AppResult<List<String>> {
        calls += 1
        return results.removeAt(0)
    }
}

private class YieldingQueueResolver(
    private val results: MutableList<AppResult<List<String>>>,
) : Ipv4Resolver {
    var calls = 0
        private set
    var maxActive = 0
        private set
    private var active = 0

    override suspend fun resolve(host: String): AppResult<List<String>> {
        calls += 1
        active += 1
        maxActive = maxOf(maxActive, active)
        yield()
        active -= 1
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
    ): AppResult<ConnectionTestResult> =
        block.invoke(server, ipv4Candidates)
}
