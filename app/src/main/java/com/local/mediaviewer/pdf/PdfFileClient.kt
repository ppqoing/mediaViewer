package com.local.mediaviewer.pdf

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DefaultDispatcherProvider
import com.local.mediaviewer.core.DispatcherProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface PdfFileClient {
    suspend fun download(
        requestUrl: String,
        destination: File,
    ): AppResult<Long>
}

class DefaultPdfFileClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : PdfFileClient {
    override suspend fun download(
        requestUrl: String,
        destination: File,
    ): AppResult<Long> = withContext(dispatchers.io) {
        try {
            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Failure(
                        AppError.HttpFailure(response.code),
                    )
                }

                val availableBytes = (
                    destination.parentFile?.usableSpace ?: 0L
                    ) - PDF_CACHE_RESERVED_SPACE_BYTES
                val contentLength = response.body.contentLength()
                if (
                    contentLength >= 0 &&
                    contentLength > availableBytes.coerceAtLeast(0L)
                ) {
                    return@withContext AppResult.Failure(
                        AppError.PdfCacheSpaceInsufficient,
                    )
                }

                try {
                    response.body.byteStream().use { input ->
                        destination.outputStream().buffered(
                            PDF_DOWNLOAD_BUFFER_BYTES,
                        ).use { output ->
                            val buffer = ByteArray(PDF_DOWNLOAD_BUFFER_BYTES)
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    AppResult.Success(destination.length())
                } catch (error: IOException) {
                    AppResult.Failure(
                        AppError.PdfCacheFailure(error.javaClass.simpleName),
                    )
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

private const val PDF_DOWNLOAD_BUFFER_BYTES = 32 * 1024
private const val PDF_CACHE_RESERVED_SPACE_BYTES = 16L * 1024L * 1024L
