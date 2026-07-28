package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.RootShare
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

class DefaultConnectionProbe(
    private val transport: DirectoryProbeTransport,
    private val parser: DirectoryJsonParser,
) : ConnectionProbe {
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
            var candidateSucceeded = true

            for (root in RootShare.entries) {
                val logicalRoot = requireNotNull(logicalBase.resolve(root.path)).toString()
                val requestRoot = requireNotNull(requestBase.resolve(root.path)).toString()
                when (val response = transport.get(requestRoot)) {
                    is AppResult.Failure -> {
                        lastError = response.error
                        candidateSucceeded = false
                        break
                    }

                    is AppResult.Success -> {
                        when (
                            val parsed = parser.parse(
                                json = response.value,
                                logicalDirectoryUrl = logicalRoot,
                                requestDirectoryUrl = requestRoot,
                            )
                        ) {
                            is AppResult.Failure -> {
                                lastError = parsed.error
                                candidateSucceeded = false
                                break
                            }

                            is AppResult.Success -> Unit
                        }
                    }
                }
            }

            if (candidateSucceeded) {
                return AppResult.Success(
                    ConnectionTestResult(
                        server = server,
                        resolvedIpv4s = ipv4Candidates,
                        endpoint = endpoint,
                    ),
                )
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

class OkHttpDirectoryProbeTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DirectoryProbeTransport {
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
                        AppResult.Failure(AppError.HttpFailure(response.code))
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
