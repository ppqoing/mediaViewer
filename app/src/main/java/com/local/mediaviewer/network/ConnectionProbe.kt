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
