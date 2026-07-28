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
        ) {
            "逻辑媒体 URL 不属于当前服务器"
        }

        val requestBase = requestBaseUrl.toHttpUrl()
        return requestBase.newBuilder()
            .encodedPath(logical.encodedPath)
            .encodedQuery(logical.encodedQuery)
            .fragment(null)
            .build()
            .toString()
    }
}
