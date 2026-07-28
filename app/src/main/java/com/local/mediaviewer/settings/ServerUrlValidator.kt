package com.local.mediaviewer.settings

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ValidatedServerUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrlValidator {
    fun validate(input: String): AppResult<ValidatedServerUrl> {
        val raw = input.trim()
        val url = raw.toHttpUrlOrNull()
            ?: return invalid("请输入完整地址，例如 http://192.168.1.17:8080")

        if (url.scheme != "http") return invalid("服务器地址只允许使用 http")
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) {
            return invalid("服务器地址不能包含用户名或密码")
        }
        if (url.encodedPath != "/") return invalid("服务器地址必须是根地址，不能包含路径")
        if (url.query != null) return invalid("服务器地址不能包含查询参数")
        if (url.fragment != null) return invalid("服务器地址不能包含片段")
        if (url.host.contains(':')) return invalid("服务器地址只支持 IPv4 或域名")

        val ipv4Like = url.host.all { it.isDigit() || it == '.' }
        val ipv4 = parseIpv4(url.host)
        if (ipv4Like && ipv4 == null) return invalid("IPv4 地址格式无效")

        return AppResult.Success(
            ValidatedServerUrl(
                logicalBaseUrl = url.toString().removeSuffix("/"),
                host = url.host,
                port = url.port,
                isIpv4Literal = ipv4 != null,
            ),
        )
    }

    internal fun parseIpv4(host: String): String? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        if (parts.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) }) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return octets.joinToString(".")
    }

    private fun invalid(message: String): AppResult.Failure =
        AppResult.Failure(AppError.InvalidServerUrl(message))
}
