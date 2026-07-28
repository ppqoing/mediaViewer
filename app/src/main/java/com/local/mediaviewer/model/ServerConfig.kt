package com.local.mediaviewer.model

data class ServerConfig(
    val logicalBaseUrl: String = DEFAULT_SERVER_URL,
    val lastSuccessfulIpv4: String? = null,
) {
    companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.1.17:8080"
    }
}
