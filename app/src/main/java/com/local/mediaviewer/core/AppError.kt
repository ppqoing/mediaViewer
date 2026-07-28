package com.local.mediaviewer.core

sealed interface AppError {
    val userMessage: String

    data class InvalidServerUrl(
        override val userMessage: String,
    ) : AppError

    data object NoIpv4Address : AppError {
        override val userMessage = "未解析到 IPv4"
    }

    data class DnsFailure(val detail: String) : AppError {
        override val userMessage = "DNS 解析失败：$detail"
    }

    data class NetworkFailure(val detail: String) : AppError {
        override val userMessage = "网络连接失败：$detail"
    }

    data class HttpFailure(val statusCode: Int) : AppError {
        override val userMessage = "服务器返回 HTTP $statusCode"
    }

    data class ProbeFailure(
        val resolvedIpv4s: List<String>,
        val lastError: String,
    ) : AppError {
        override val userMessage = "所有 IPv4 均连接失败：$lastError"
    }

    data object InvalidDirectoryResponse : AppError {
        override val userMessage = "目录响应格式无效"
    }

    data class PlaybackFailure(val detail: String) : AppError {
        override val userMessage = "播放失败：$detail"
    }

    data object ImageLoadFailure : AppError {
        override val userMessage = "图片加载失败"
    }
}
