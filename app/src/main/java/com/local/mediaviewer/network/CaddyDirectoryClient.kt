package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DefaultDispatcherProvider
import com.local.mediaviewer.core.DispatcherProvider
import com.local.mediaviewer.model.DirectoryEntry
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface CaddyDirectoryClient {
    suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}

class DefaultCaddyDirectoryClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val parser: DirectoryJsonParser = DefaultDirectoryJsonParser(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : CaddyDirectoryClient {
    override suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>> = withContext(dispatchers.io) {
        try {
            val request = Request.Builder()
                .url(requestDirectoryUrl)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Failure(
                        AppError.HttpFailure(response.code),
                    )
                }
                parser.parse(
                    response.body.string(),
                    logicalDirectoryUrl,
                    requestDirectoryUrl,
                )
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
