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

    data object DiscoveryNotSupported : AppError {
        override val userMessage = "服务器不支持共享发现接口，请升级 RangeShelf"
    }

    data object InvalidDiscoveryResponse : AppError {
        override val userMessage = "共享发现响应格式无效"
    }

    data class UnsupportedDiscoveryVersion(val schemaVersion: Int) : AppError {
        override val userMessage = "不支持共享发现协议版本 $schemaVersion"
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

    data object PdfCacheSpaceInsufficient : AppError {
        override val userMessage = "缓存空间不足，无法打开 PDF"
    }

    data class PdfCacheFailure(val detail: String) : AppError {
        override val userMessage = "PDF 临时文件写入失败：$detail"
    }

    data object InvalidPdfDocument : AppError {
        override val userMessage = "PDF 文件无效或已损坏"
    }

    data object EncryptedPdfDocument : AppError {
        override val userMessage = "当前版本暂不支持加密 PDF"
    }

    data class PdfPageRenderFailure(val pageNumber: Int) : AppError {
        override val userMessage = "第 $pageNumber 页渲染失败"
    }
}
