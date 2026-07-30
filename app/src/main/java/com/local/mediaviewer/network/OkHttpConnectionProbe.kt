package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl
import com.local.mediaviewer.settings.ServerUrlValidator
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通过 RangeShelf 共享发现接口确定可用 IPv4 和共享入口。
 */
class DefaultConnectionProbe(
    private val transport: ShareDiscoveryTransport,
    private val parser: ShareDiscoveryParser,
) : ConnectionProbe {
    /**
     * 按候选顺序请求共享发现接口，首个完整成功的候选即成为当前端点。
     *
     * @param server 已校验的逻辑服务器地址。
     * @param ipv4Candidates 待尝试的 IPv4 地址。
     * @return 成功端点和共享清单；全部失败时保留最后一个错误。
     */
    override suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ): AppResult<ConnectionTestResult> {
        if (ipv4Candidates.isEmpty()) {
            return AppResult.Failure(AppError.NoIpv4Address)
        }

        val logicalBase = server.logicalBaseUrl.toHttpUrl()
        var lastError: AppError = AppError.NetworkFailure("没有完成探测")
        for (candidate in ipv4Candidates) {
            val ipv4 = ServerUrlValidator.parseIpv4(candidate)
            if (ipv4 == null) {
                lastError = AppError.NetworkFailure("IPv4 候选格式无效")
                continue
            }

            val requestBase = logicalBase.newBuilder()
                .host(ipv4)
                .build()
            val endpoint = SessionEndpoint(
                logicalBaseUrl = logicalBase.toString().removeSuffix("/"),
                requestBaseUrl = requestBase.toString().removeSuffix("/"),
                ipv4 = ipv4,
            )
            val requestUrl = requestBase.newBuilder()
                .addPathSegments(SHARE_DISCOVERY_PATH.removePrefix("/"))
                .build()
                .toString()
            when (val response = transport.get(requestUrl)) {
                is AppResult.Failure -> {
                    lastError = response.error
                }

                is AppResult.Success -> when (val parsed = parser.parse(response.value)) {
                    is AppResult.Failure -> {
                        lastError = parsed.error
                    }

                    is AppResult.Success -> return AppResult.Success(
                        ConnectionTestResult(
                            server = server,
                            resolvedIpv4s = ipv4Candidates,
                            endpoint = endpoint,
                            shares = parsed.value,
                        ),
                    )
                }
            }
        }

        return AppResult.Failure(
            AppError.ProbeFailure(
                resolvedIpv4s = ipv4Candidates,
                lastError = lastError.userMessage,
            ),
        )
    }
}

/**
 * 使用 OkHttp 获取 RangeShelf 共享发现文档。
 */
class OkHttpShareDiscoveryTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShareDiscoveryTransport {
    /**
     * 执行无缓存 JSON GET，并把 404 转换为明确的版本兼容错误。
     *
     * @param url 共享发现接口的 IPv4 请求地址。
     * @return 2xx 响应体或可供连接探测汇总的错误。
     */
    override suspend fun get(url: String): AppResult<String> =
        withContext(ioDispatcher) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = if (response.code == 404) {
                            AppError.DiscoveryNotSupported
                        } else {
                            AppError.HttpFailure(response.code)
                        }
                        AppResult.Failure(error)
                    } else {
                        AppResult.Success(response.body.string())
                    }
                }
            } catch (error: IOException) {
                AppResult.Failure(
                    AppError.NetworkFailure(error.javaClass.simpleName),
                )
            } catch (error: IllegalArgumentException) {
                AppResult.Failure(
                    AppError.NetworkFailure(error.javaClass.simpleName),
                )
            }
        }
}

/**
 * RangeShelf 共享发现接口的固定逻辑路径。
 */
const val SHARE_DISCOVERY_PATH = "/.rangeshelf/shares"
