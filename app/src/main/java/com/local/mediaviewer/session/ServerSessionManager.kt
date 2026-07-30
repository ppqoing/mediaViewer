package com.local.mediaviewer.session

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
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
                is AppResult.Success -> applySuccess(result.value)
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
            applySuccess(result)
        }
    }

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        mutex.withLock {
            mutableState.value = ServerSessionState.Connecting
            when (val result = connect(settings.current().logicalBaseUrl)) {
                is AppResult.Success -> {
                    applySuccess(result.value)
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
        val validated = when (val result = ServerUrlValidator.validate(input)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val addresses = when (val result = resolver.resolve(validated.host)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        return probe.probe(validated, addresses)
    }

    private suspend fun applySuccess(result: ConnectionTestResult) {
        settings.save(
            ServerConfig(
                logicalBaseUrl = result.server.logicalBaseUrl,
                lastSuccessfulIpv4 = result.endpoint.ipv4,
            ),
        )
        mutableState.value = ServerSessionState.Connected(
            endpoint = result.endpoint,
            resolvedIpv4s = result.resolvedIpv4s,
            shares = result.shares,
        )
    }

    private fun applyFailure(error: AppError) {
        val candidates = (error as? AppError.ProbeFailure)
            ?.resolvedIpv4s
            .orEmpty()
        mutableState.value = ServerSessionState.Failed(
            error = error,
            resolvedIpv4s = candidates,
        )
    }
}
