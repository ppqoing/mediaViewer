package com.local.mediaviewer.pdf

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DefaultDispatcherProvider
import com.local.mediaviewer.core.DispatcherProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface PdfFileClient {
    suspend fun download(
        requestUrl: String,
        destination: File,
    ): AppResult<Long>
}

class DefaultPdfFileClient(
    private val client: Call.Factory = OkHttpClient.Builder()
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
            executeCancellable(
                call = client.newCall(request),
                destination = destination,
            )
        } catch (error: IllegalArgumentException) {
            AppResult.Failure(
                AppError.NetworkFailure(error.javaClass.simpleName),
            )
        }
    }

    private suspend fun executeCancellable(
        call: Call,
        destination: File,
    ): AppResult<Long> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        val result = executeBlocking(
            call = call,
            destination = destination,
            isCancelled = continuation::isCancelled,
        )
        if (result != null && continuation.isActive) {
            continuation.resume(result)
        }
    }

    private fun executeBlocking(
        call: Call,
        destination: File,
        isCancelled: () -> Boolean,
    ): AppResult<Long>? = try {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                return AppResult.Failure(
                    AppError.HttpFailure(response.code),
                )
            }

            val writableBytes = (
                destination.parentFile?.usableSpace ?: 0L
                ) - PDF_CACHE_RESERVED_SPACE_BYTES
            val contentLength = response.body.contentLength()
            if (
                contentLength >= 0 &&
                contentLength > writableBytes.coerceAtLeast(0L)
            ) {
                return AppResult.Failure(
                    AppError.PdfCacheSpaceInsufficient,
                )
            }

            try {
                response.body.byteStream().use { input ->
                    destination.outputStream().buffered(
                        PDF_DOWNLOAD_BUFFER_BYTES,
                    ).use { output ->
                        val buffer = ByteArray(PDF_DOWNLOAD_BUFFER_BYTES)
                        var writtenBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            if (count > writableBytes.coerceAtLeast(0L) - writtenBytes) {
                                return AppResult.Failure(
                                    AppError.PdfCacheSpaceInsufficient,
                                )
                            }
                            output.write(buffer, 0, count)
                            writtenBytes += count
                        }
                    }
                }
                AppResult.Success(destination.length())
            } catch (error: IOException) {
                if (isCancelled()) null else {
                    AppResult.Failure(
                        AppError.PdfCacheFailure(error.javaClass.simpleName),
                    )
                }
            }
        }
    } catch (error: IOException) {
        if (isCancelled()) null else {
            AppResult.Failure(
                AppError.NetworkFailure(error.javaClass.simpleName),
            )
        }
    }
}

private const val PDF_DOWNLOAD_BUFFER_BYTES = 32 * 1024
private const val PDF_CACHE_RESERVED_SPACE_BYTES = 16L * 1024L * 1024L
