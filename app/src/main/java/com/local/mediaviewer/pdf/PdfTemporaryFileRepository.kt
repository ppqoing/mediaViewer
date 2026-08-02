package com.local.mediaviewer.pdf

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

data class PdfTemporaryFile(
    val logicalUrl: String,
    val file: File,
    val byteCount: Long,
)

interface PdfTemporaryFileRepository {
    suspend fun acquire(logicalUrl: String): AppResult<PdfTemporaryFile>
    fun release(file: PdfTemporaryFile)
    suspend fun cleanupExpired(nowMs: Long = System.currentTimeMillis())
}

class DefaultPdfTemporaryFileRepository(
    private val cacheRoot: File,
    private val client: PdfFileClient = DefaultPdfFileClient(),
    private val session: ServerSessionManager,
) : PdfTemporaryFileRepository {
    override suspend fun acquire(logicalUrl: String): AppResult<PdfTemporaryFile> {
        val endpoint = currentEndpoint() ?: return unavailable()
        val directory = File(cacheRoot, PDF_CACHE_DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) {
            return AppResult.Failure(
                AppError.PdfCacheFailure("无法创建缓存目录"),
            )
        }

        val baseName = sha256(logicalUrl)
        val partFile = File(directory, "$baseName.part")
        val finalFile = File(directory, "$baseName.pdf")
        return try {
            acquireWith(
                logicalUrl = logicalUrl,
                endpoint = endpoint,
                partFile = partFile,
                finalFile = finalFile,
                allowRefresh = true,
            )
        } catch (error: CancellationException) {
            partFile.delete()
            throw error
        }
    }

    override fun release(file: PdfTemporaryFile) {
        file.file.delete()
    }

    override suspend fun cleanupExpired(nowMs: Long) {
        val directory = File(cacheRoot, PDF_CACHE_DIRECTORY_NAME)
        directory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    (file.extension == "part" || file.extension == "pdf") &&
                    nowMs - file.lastModified() > PDF_CACHE_MAX_AGE_MS
            }
            .forEach(File::delete)
    }

    private suspend fun acquireWith(
        logicalUrl: String,
        endpoint: SessionEndpoint,
        partFile: File,
        finalFile: File,
        allowRefresh: Boolean,
    ): AppResult<PdfTemporaryFile> {
        val result = client.download(
            endpoint.requestUrlFor(logicalUrl),
            partFile,
        )
        return when (result) {
            is AppResult.Success -> publish(
                logicalUrl = logicalUrl,
                partFile = partFile,
                finalFile = finalFile,
                byteCount = result.value,
            )

            is AppResult.Failure -> {
                partFile.delete()
                if (allowRefresh && result.error is AppError.NetworkFailure) {
                    when (val refreshed = session.refreshAfterRequestFailure()) {
                        is AppResult.Success -> acquireWith(
                            logicalUrl = logicalUrl,
                            endpoint = refreshed.value,
                            partFile = partFile,
                            finalFile = finalFile,
                            allowRefresh = false,
                        )

                        is AppResult.Failure -> refreshed
                    }
                } else {
                    result
                }
            }
        }
    }

    private fun publish(
        logicalUrl: String,
        partFile: File,
        finalFile: File,
        byteCount: Long,
    ): AppResult<PdfTemporaryFile> = try {
        Files.move(
            partFile.toPath(),
            finalFile.toPath(),
            REPLACE_EXISTING,
            ATOMIC_MOVE,
        )
        AppResult.Success(
            PdfTemporaryFile(
                logicalUrl = logicalUrl,
                file = finalFile,
                byteCount = byteCount,
            ),
        )
    } catch (error: IOException) {
        partFile.delete()
        AppResult.Failure(
            AppError.PdfCacheFailure(error.javaClass.simpleName),
        )
    }

    private fun currentEndpoint(): SessionEndpoint? =
        (session.state.value as? ServerSessionState.Connected)?.endpoint

    private fun unavailable(): AppResult.Failure =
        AppResult.Failure(AppError.NetworkFailure("服务器尚未连接"))
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

const val PDF_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
private const val PDF_CACHE_DIRECTORY_NAME = "pdf-cache"
